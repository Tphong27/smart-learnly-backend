package com.smartlearnly.backend.videoai.service;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.videoai.dto.VideoAiDtos.GeneratedSummary;
import com.smartlearnly.backend.videoai.dto.VideoAiDtos.GenerateSummaryResponse;
import com.smartlearnly.backend.videoai.service.YoutubeVideoMetadataService.YoutubeVideoMetadata;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

@Slf4j
@Service
public class VideoSummaryService {

    private final YoutubeVideoMetadataService metadataService;
    private final YoutubeTranscriptService transcriptService;
    private final GeminiVideoSummaryService summaryService;

    /**
     * Khởi tạo service điều phối metadata, Gemini và transcript fallback.
     */
    public VideoSummaryService(
            YoutubeVideoMetadataService metadataService,
            YoutubeTranscriptService transcriptService,
            GeminiVideoSummaryService summaryService) {
        this.metadataService = metadataService;
        this.transcriptService = transcriptService;
        this.summaryService = summaryService;
    }

    /**
     * Tạo summary trực tiếp từ video và chỉ dùng transcript khi Gemini trực tiếp lỗi.
     */
    public GenerateSummaryResponse generateVideoSummary(String youtubeUrl) {
        String videoId = extractVideoIdFromYoutubeUrl(youtubeUrl);
        YoutubeVideoMetadata metadata = metadataService.fetchYoutubeVideoMetadata(videoId);
        String canonicalUrl = buildCanonicalYoutubeUrl(videoId);
        GeneratedSummary summary = generateSummaryWithFallback(videoId, canonicalUrl);
        long durationSeconds = metadata.durationSeconds();
        return new GenerateSummaryResponse(
                videoId,
                canonicalUrl,
                durationSeconds,
                Math.toIntExact((durationSeconds + 59) / 60),
                summary);
    }

    /**
     * Chuẩn hóa URL của video lesson hoặc giữ nguyên URL hiện tại hợp lệ.
     */
    public String normalizeLessonVideoUrl(
            String currentVideoUrl,
            String requestedVideoUrl,
            boolean videoLesson) {
        if (!videoLesson) {
            return null;
        }
        String current = normalize(currentVideoUrl);
        String requested = normalize(requestedVideoUrl);
        if (requested == null) {
            if (current != null) {
                return current;
            }
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Enter a valid HTTPS YouTube URL");
        }

        if (current != null && current.equals(requested)) {
            return current;
        }
        return buildCanonicalYoutubeUrl(
                extractVideoIdFromYoutubeUrl(requested));
    }

    /**
     * Ưu tiên Gemini video input rồi dùng transcript scraper làm đường dự phòng.
     */
    private GeneratedSummary generateSummaryWithFallback(
            String videoId,
            String canonicalUrl) {
        try {
            return summaryService.generateSummaryFromYoutubeVideo(canonicalUrl);
        } catch (BusinessException directFailure) {
            if (directFailure.errorCode() != ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE) {
                throw directFailure;
            }
            log.warn(
                    "Direct Gemini YouTube summary failed; using transcript fallback: videoId={}",
                    videoId);
            try {
                YoutubeTranscriptService.TranscriptResult transcript =
                        transcriptService.fetchYoutubeTranscript(videoId);
                return summaryService.generateSummaryFromTranscript(
                        transcript.language(),
                        transcript.text());
            } catch (BusinessException fallbackFailure) {
                directFailure.addSuppressed(fallbackFailure);
                log.warn(
                        "Transcript fallback for video summary failed: videoId={} errorCode={} message={}",
                        videoId,
                        fallbackFailure.errorCode(),
                        fallbackFailure.getMessage());
                throw directFailure;
            }
        }
    }

    /**
     * Trim chuỗi và đổi giá trị rỗng thành null.
     */
    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    /**
     * Trích xuất video ID từ hai dạng URL YouTube HTTPS được hỗ trợ.
     */
    String extractVideoIdFromYoutubeUrl(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Enter a valid HTTPS YouTube URL");
        }

        try {
            URI uri = new URI(normalized);
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || uri.getHost() == null
                    || uri.getUserInfo() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443)) {
                throw new BusinessException(
                        ErrorCode.INVALID_REQUEST,
                        "Enter a valid HTTPS YouTube URL");
            }

            String host = uri.getHost().toLowerCase(Locale.ROOT);
            String path = uri.getPath() == null ? "" : uri.getPath();
            String videoId = null;

            if ("youtu.be".equals(host)) {
                String[] parts = path.split("/");
                if (parts.length == 2) {
                    videoId = parts[1];
                }
            } else if (("youtube.com".equals(host)
                    || "www.youtube.com".equals(host))
                    && ("/watch".equals(path) || "/watch/".equals(path))) {
                videoId = UriComponentsBuilder.fromUri(uri)
                        .build()
                        .getQueryParams()
                        .getFirst("v");
            }

            if (videoId == null || !videoId.matches("[A-Za-z0-9_-]{11}")) {
                throw new BusinessException(
                        ErrorCode.INVALID_REQUEST,
                        "Enter a valid HTTPS YouTube URL");
            }
            return videoId;
        } catch (URISyntaxException | IllegalArgumentException exception) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Enter a valid HTTPS YouTube URL");
        }
    }

    /**
     * Tạo URL watch chuẩn từ video ID đã được kiểm tra.
     */
    private String buildCanonicalYoutubeUrl(String videoId) {
        return "https://www.youtube.com/watch?v=" + videoId;
    }

}
