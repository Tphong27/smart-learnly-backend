package com.smartlearnly.backend.file.service;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.file.config.StorageProperties;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriUtils;

@Service
@RequiredArgsConstructor
public class SupabaseStorageClient implements FileStorageService {
    private static final Logger log = LoggerFactory.getLogger(SupabaseStorageClient.class);

    private final StorageProperties storageProperties;
    private final RestClient.Builder restClientBuilder;

    @Override
    public StoredFile store(String bucket, String objectPath, String contentType, byte[] content) {
        validateConfiguration();
        String encodedBucket = UriUtils.encodePathSegment(bucket, StandardCharsets.UTF_8);
        String encodedPath = UriUtils.encodePath(objectPath, StandardCharsets.UTF_8);
        String uploadUrl = normalizeBaseUrl() + "/storage/v1/object/" + encodedBucket + "/" + encodedPath;

        try {
            restClientBuilder.build()
                    .post()
                    .uri(uploadUrl)
                    .header("apikey", storageProperties.getSupabaseServiceRoleKey())
                    .header("Authorization", "Bearer " + storageProperties.getSupabaseServiceRoleKey())
                    .header("x-upsert", "false")
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(content)
                    .retrieve()
                    .toBodilessEntity();
        }
        catch (RestClientResponseException exception) {
            String responseBody = exception.getResponseBodyAsString();
            log.warn(
                    "Supabase storage upload failed for bucket={} path={} status={} body={}",
                    bucket,
                    objectPath,
                    exception.getStatusCode(),
                    truncate(responseBody)
            );
            throw new BusinessException(
                    ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE,
                    resolveStorageFailureMessage(responseBody)
            );
        }
        catch (RestClientException | IllegalArgumentException exception) {
            log.warn("Supabase storage upload failed for bucket={} path={}", bucket, objectPath, exception);
            throw new BusinessException(
                    ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE,
                    "File storage service is unavailable"
            );
        }

        String publicUrl = normalizeBaseUrl()
                + "/storage/v1/object/public/"
                + encodedBucket
                + "/"
                + encodedPath;
        String fileName = objectPath.substring(objectPath.lastIndexOf('/') + 1);
        return new StoredFile(publicUrl, objectPath, fileName, contentType, content.length);
    }

    public void deleteObject(String bucket, String objectPath) {
        validateConfiguration();
        if (bucket == null || bucket.isBlank() || objectPath == null || objectPath.isBlank()) {
            return;
        }
        String deleteUrl = objectUrl(bucket, objectPath);
        try {
            restClientBuilder.build()
                    .delete()
                    .uri(deleteUrl)
                    .header("apikey", storageProperties.getSupabaseServiceRoleKey())
                    .header("Authorization", "Bearer " + storageProperties.getSupabaseServiceRoleKey())
                    .retrieve()
                    .toBodilessEntity();
        }
        catch (RestClientException exception) {
            log.warn("Supabase storage delete failed for bucket={} path={}", bucket, objectPath, exception);
        }
    }

    public String createSignedUrl(String bucket, String objectPath, int ttlSeconds) {
        validateConfiguration();
        String encodedBucket = UriUtils.encodePathSegment(bucket, StandardCharsets.UTF_8);
        String encodedPath = UriUtils.encodePath(objectPath, StandardCharsets.UTF_8);
        String signUrl = normalizeBaseUrl() + "/storage/v1/object/sign/" + encodedBucket + "/" + encodedPath;
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("expiresIn", Math.max(1, ttlSeconds));
            Map<?, ?> response = restClientBuilder.build()
                    .post()
                    .uri(signUrl)
                    .header("apikey", storageProperties.getSupabaseServiceRoleKey())
                    .header("Authorization", "Bearer " + storageProperties.getSupabaseServiceRoleKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            Object signed = response == null ? null : (response.get("signedURL") != null ? response.get("signedURL") : response.get("signedUrl"));
            if (signed == null || signed.toString().isBlank()) {
                throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE, "File storage service did not return a signed URL");
            }
            String value = signed.toString();
            return value.startsWith("http") ? value : normalizeBaseUrl() + value;
        }
        catch (RestClientException | IllegalArgumentException exception) {
            log.warn("Supabase storage signed URL failed for bucket={} path={}", bucket, objectPath, exception);
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE, "File storage service is unavailable");
        }
    }

    private void validateConfiguration() {
        if (storageProperties.getSupabaseUrl() == null
                || storageProperties.getSupabaseUrl().isBlank()
                || storageProperties.getSupabaseServiceRoleKey() == null
                || storageProperties.getSupabaseServiceRoleKey().isBlank()) {
            throw new BusinessException(
                    ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE,
                    "File storage service is not configured"
            );
        }
    }

    private String normalizeBaseUrl() {
        return storageProperties.getSupabaseUrl().replaceAll("/+$", "");
    }

    private String objectUrl(String bucket, String objectPath) {
        String encodedBucket = UriUtils.encodePathSegment(bucket, StandardCharsets.UTF_8);
        String encodedPath = UriUtils.encodePath(objectPath, StandardCharsets.UTF_8);
        return normalizeBaseUrl() + "/storage/v1/object/" + encodedBucket + "/" + encodedPath;
    }

    private String resolveStorageFailureMessage(String responseBody) {
        if (responseBody != null
                && responseBody.toLowerCase(Locale.ROOT).contains("bucket not found")) {
            return "File storage bucket was not found";
        }
        return "File storage service is unavailable";
    }

    private String truncate(String value) {
        if (value == null || value.length() <= 500) {
            return value;
        }
        return value.substring(0, 500) + "...";
    }
}
