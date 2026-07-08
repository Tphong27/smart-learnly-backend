package com.smartlearnly.backend.question.service;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.file.config.StorageProperties;
import com.smartlearnly.backend.file.service.FileStorageService;
import com.smartlearnly.backend.question.entity.Question;
import com.smartlearnly.backend.question.entity.QuestionMediaAttachment;
import com.smartlearnly.backend.question.entity.QuestionMediaType;
import com.smartlearnly.backend.question.repository.QuestionMediaAttachmentRepository;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.IDN;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.util.unit.DataSize;

@Service
@RequiredArgsConstructor
public class QuestionMediaImportService {
    private static final int MAX_REDIRECTS = 3;
    private static final int MAX_URL_LENGTH = 2048;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);

    private static final Map<String, String> IMAGE_TYPES = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp"
    );
    private static final Map<String, String> AUDIO_TYPES = Map.of(
            "audio/mpeg", "mp3",
            "audio/mp4", "m4a",
            "audio/x-m4a", "m4a",
            "audio/wav", "wav",
            "audio/x-wav", "wav"
    );

    private final QuestionMediaAttachmentRepository mediaAttachmentRepository;
    private final FileStorageService fileStorageService;
    private final StorageProperties storageProperties;
    private final Tika tika = new Tika();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public List<String> validateMediaReferences(List<String> imageUrls, List<String> audioUrls) {
        List<String> errors = new ArrayList<>();
        validateUrlList(imageUrls, QuestionMediaType.IMAGE, errors);
        validateUrlList(audioUrls, QuestionMediaType.AUDIO, errors);
        return errors;
    }

    public void attachImportedMedia(Question question, List<String> imageUrls, List<String> audioUrls, String importSource) {
        List<String> images = normalizeUrls(imageUrls);
        List<String> audios = normalizeUrls(audioUrls);
        if (images.isEmpty() && audios.isEmpty()) {
            return;
        }
        attach(question, QuestionMediaType.IMAGE, images, importSource);
        attach(question, QuestionMediaType.AUDIO, audios, importSource);
    }

    private void attach(Question question, QuestionMediaType mediaType, List<String> urls, String importSource) {
        if (urls.isEmpty()) {
            return;
        }
        long existingCount = mediaAttachmentRepository.countByQuestionIdAndMediaType(question.getId(), mediaType);
        if (existingCount + urls.size() > maxCount(mediaType)) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, maxCountMessage(mediaType));
        }
        int displayOrder = (int) existingCount + 1;
        for (String url : urls) {
            DownloadedMedia media = download(url, mediaType);
            store(question, mediaType, media, displayOrder, importSource);
            displayOrder += 1;
        }
    }

    private void store(Question question, QuestionMediaType mediaType, DownloadedMedia media, int displayOrder, String importSource) {
        String contentType = detectContentType(media.content(), mediaType);
        String extension = allowedTypes(mediaType).get(contentType);
        if (extension == null) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_MEDIA_TYPE, unsupportedTypeMessage(mediaType));
        }
        String objectKey = "questions/%s/%s/%s.%s".formatted(
                question.getId(),
                mediaType == QuestionMediaType.IMAGE ? "images" : "audios",
                UUID.randomUUID(),
                extension
        );
        FileStorageService.StoredFile stored = fileStorageService.store(
                storageProperties.getQuestionMediaBucket(),
                objectKey,
                contentType,
                media.content()
        );

        QuestionMediaAttachment attachment = new QuestionMediaAttachment();
        attachment.setQuestionId(question.getId());
        attachment.setMediaType(mediaType);
        attachment.setMediaUrl(stored.url());
        attachment.setObjectKey(stored.objectPath());
        attachment.setBucket(storageProperties.getQuestionMediaBucket());
        attachment.setOriginalFileName(resolveFileName(media.finalUri(), stored.fileName()));
        attachment.setContentType(stored.contentType());
        attachment.setFileSize(stored.size());
        attachment.setDisplayOrder(displayOrder);
        attachment.setImportSource(normalizeImportSource(importSource));
        mediaAttachmentRepository.save(attachment);
    }

    private DownloadedMedia download(String rawUrl, QuestionMediaType mediaType) {
        URI uri = parseAndValidateUri(rawUrl);
        DataSize maxSize = maxSize(mediaType);
        for (int redirectCount = 0; redirectCount <= MAX_REDIRECTS; redirectCount += 1) {
            validateHost(uri);
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .GET()
                    .timeout(REQUEST_TIMEOUT)
                    .header("User-Agent", "SmartLearnly-MediaImport/1.0")
                    .build();
            HttpResponse<InputStream> response;
            try {
                response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            } catch (IOException exception) {
                throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE, "Could not download media URL: " + rawUrl);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE, "Media URL download was interrupted");
            }

            int status = response.statusCode();
            if (status >= 300 && status < 400) {
                closeQuietly(response.body());
                String location = response.headers().firstValue("location").orElse(null);
                uri = resolveRedirect(uri, location, rawUrl);
                continue;
            }
            if (status < 200 || status >= 300) {
                closeQuietly(response.body());
                throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE, "Media URL returned HTTP " + status);
            }
            OptionalLong contentLength = response.headers().firstValueAsLong("content-length");
            if (contentLength.isPresent() && contentLength.getAsLong() > maxSize.toBytes()) {
                closeQuietly(response.body());
                throw new BusinessException(ErrorCode.PAYLOAD_TOO_LARGE);
            }
            return new DownloadedMedia(uri, readLimited(response.body(), maxSize));
        }
        throw new BusinessException(ErrorCode.INVALID_REQUEST, "Media URL has too many redirects");
    }

    private URI parseAndValidateUri(String rawUrl) {
        String value = rawUrl == null ? "" : rawUrl.trim();
        if (value.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Media URL is required");
        }
        if (value.length() > MAX_URL_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Media URL must not exceed 2048 characters");
        }
        URI uri;
        try {
            uri = new URI(value);
        } catch (URISyntaxException exception) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Media URL is invalid");
        }
        String scheme = uri.getScheme();
        if (scheme == null || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Media URL must use http or https");
        }
        if (uri.getUserInfo() != null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Media URL must not include credentials");
        }
        validateHost(uri);
        return uri;
    }

    private URI resolveRedirect(URI currentUri, String location, String originalUrl) {
        if (location == null || location.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Media URL redirect is missing a Location header");
        }
        URI redirected = currentUri.resolve(location.trim());
        String scheme = redirected.getScheme();
        if (scheme == null || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Media URL redirect must use http or https");
        }
        if (redirected.toString().length() > MAX_URL_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Media URL redirect is too long");
        }
        try {
            return new URI(redirected.toString());
        } catch (URISyntaxException exception) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Media URL redirect is invalid: " + originalUrl);
        }
    }

    private void validateHost(URI uri) {
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Media URL host is required");
        }
        String asciiHost = IDN.toASCII(host.trim()).toLowerCase(Locale.ROOT);
        if (asciiHost.equals("localhost") || asciiHost.endsWith(".localhost") || asciiHost.endsWith(".local") || asciiHost.endsWith(".internal")) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Media URL host is not allowed");
        }
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(asciiHost);
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Media URL host could not be resolved");
        }
        if (addresses.length == 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Media URL host could not be resolved");
        }
        for (InetAddress address : addresses) {
            if (isBlockedAddress(address)) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST, "Media URL host resolves to a private or internal address");
            }
        }
    }

    private boolean isBlockedAddress(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        if (address instanceof Inet4Address inet4Address) {
            byte[] bytes = inet4Address.getAddress();
            int first = bytes[0] & 0xff;
            int second = bytes[1] & 0xff;
            return first == 0
                    || first == 10
                    || first == 127
                    || (first == 169 && second == 254)
                    || (first == 172 && second >= 16 && second <= 31)
                    || (first == 192 && second == 168);
        }
        if (address instanceof Inet6Address inet6Address) {
            byte[] bytes = inet6Address.getAddress();
            int first = bytes[0] & 0xff;
            return (first & 0xfe) == 0xfc;
        }
        return false;
    }

    private byte[] readLimited(InputStream body, DataSize maxSize) {
        long maxBytes = maxSize.toBytes();
        try (InputStream input = body; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            long total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    throw new BusinessException(ErrorCode.PAYLOAD_TOO_LARGE);
                }
                output.write(buffer, 0, read);
            }
            if (total == 0) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST, "Downloaded media file is empty");
            }
            return output.toByteArray();
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE, "Could not read downloaded media file");
        }
    }

    private void closeQuietly(InputStream inputStream) {
        if (inputStream == null) return;
        try {
            inputStream.close();
        } catch (IOException ignored) {
            // Nothing useful to do; the request is already being rejected or redirected.
        }
    }

    private void validateUrlList(List<String> urls, QuestionMediaType mediaType, List<String> errors) {
        List<String> normalized = normalizeUrls(urls);
        if (normalized.size() > maxCount(mediaType)) {
            errors.add(maxCountMessage(mediaType));
            return;
        }
        for (String url : normalized) {
            try {
                parseAndValidateUri(url);
            } catch (BusinessException exception) {
                errors.add((mediaType == QuestionMediaType.IMAGE ? "Image" : "Audio") + " URL is invalid: " + exception.getMessage());
            }
        }
    }

    private List<String> normalizeUrls(List<String> urls) {
        if (urls == null || urls.isEmpty()) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (String url : urls) {
            if (url == null) continue;
            String value = url.trim();
            if (!value.isEmpty()) normalized.add(value);
        }
        return normalized;
    }

    private String detectContentType(byte[] content, QuestionMediaType mediaType) {
        try {
            return tika.detect(content);
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_MEDIA_TYPE, mediaType == QuestionMediaType.IMAGE
                    ? "Question image type could not be detected"
                    : "Question audio type could not be detected");
        }
    }

    private Map<String, String> allowedTypes(QuestionMediaType mediaType) {
        return mediaType == QuestionMediaType.IMAGE ? IMAGE_TYPES : AUDIO_TYPES;
    }

    private DataSize maxSize(QuestionMediaType mediaType) {
        return mediaType == QuestionMediaType.IMAGE
                ? storageProperties.getQuestionImageMaxSize()
                : storageProperties.getQuestionAudioMaxSize();
    }

    private int maxCount(QuestionMediaType mediaType) {
        return mediaType == QuestionMediaType.IMAGE
                ? QuestionMediaService.MAX_IMAGES_PER_QUESTION
                : QuestionMediaService.MAX_AUDIOS_PER_QUESTION;
    }

    private String maxCountMessage(QuestionMediaType mediaType) {
        return mediaType == QuestionMediaType.IMAGE
                ? "A question can have at most 5 images"
                : "A question can have at most 3 audio files";
    }

    private String unsupportedTypeMessage(QuestionMediaType mediaType) {
        return mediaType == QuestionMediaType.IMAGE
                ? "Question image URL must download a JPEG, PNG, or WebP file"
                : "Question audio URL must download an MP3, M4A, or WAV file";
    }

    private String resolveFileName(URI uri, String fallback) {
        String path = uri.getPath();
        if (path == null || path.isBlank()) {
            return fallback;
        }
        int slashIndex = path.lastIndexOf('/');
        String fileName = slashIndex >= 0 ? path.substring(slashIndex + 1) : path;
        return fileName.isBlank() ? fallback : fileName;
    }

    private String normalizeImportSource(String value) {
        if (value == null || value.isBlank()) {
            return "excel_import";
        }
        String normalized = value.trim().replace('-', '_').toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "excel_import", "json_import", "image_import" -> normalized;
            default -> "excel_import";
        };
    }

    private record DownloadedMedia(URI finalUri, byte[] content) {
    }
}
