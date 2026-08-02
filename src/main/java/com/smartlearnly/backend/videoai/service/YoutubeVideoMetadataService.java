package com.smartlearnly.backend.videoai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.videoai.config.VideoAiProperties;
import java.io.IOException;
import java.time.DateTimeException;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Lấy metadata của video từ YouTube và kiểm tra các điều kiện cần thiết trước
 * khi tạo bản tóm tắt.
 */
@Slf4j
@Service
public class YoutubeVideoMetadataService {

        private final VideoAiProperties properties;
        private final RestClient youtubeClient;
        private final ObjectMapper youtubeJson = new ObjectMapper();

        @Autowired
        public YoutubeVideoMetadataService(VideoAiProperties properties) {
                this(properties, createYoutubeClient(properties));
        }

        YoutubeVideoMetadataService(
                        VideoAiProperties properties,
                        RestClient youtubeClient) {
                this.properties = properties;
                this.youtubeClient = youtubeClient;
        }

        /**
         * Gọi YouTube Data API và trả metadata đã vượt qua các kiểm tra:
         * video tồn tại, cho phép nhúng, có caption và không vượt quá thời lượng.
         */
        public YoutubeVideoMetadata fetchYoutubeVideoMetadata(String videoId) {
                validateYoutubeMetadataConfiguration();
                try {
                        String responseBody = youtubeClient.get()
                                        .uri(uriBuilder -> uriBuilder
                                                        .path("/videos")
                                                        .queryParam("part", "contentDetails,status")
                                                        .queryParam("id", videoId)
                                                        .queryParam("key", properties.getYoutubeApiKey())
                                                        .build())
                                        .retrieve()
                                        .body(String.class);
                        JsonNode response = youtubeJson.readTree(
                                        responseBody == null ? "{}" : responseBody);

                        JsonNode items = response == null ? null : response.path("items");
                        if (items == null || !items.isArray() || items.isEmpty()) {
                                throw new BusinessException(
                                                ErrorCode.RESOURCE_NOT_FOUND,
                                                "YouTube video was not found");
                        }

                        JsonNode video = items.get(0);
                        boolean embeddable = video.path("status")
                                        .path("embeddable")
                                        .asBoolean(false);
                        if (!embeddable) {
                                throw new BusinessException(
                                                ErrorCode.BUSINESS_RULE_VIOLATION,
                                                "This YouTube video cannot be embedded");
                        }

                        boolean captionsAvailable = "true".equalsIgnoreCase(
                                        video.path("contentDetails").path("caption").asText());
                        if (!captionsAvailable) {
                                throw new BusinessException(
                                                ErrorCode.BUSINESS_RULE_VIOLATION,
                                                "This YouTube video does not have captions");
                        }

                        long durationSeconds = Duration.parse(
                                        video.path("contentDetails")
                                                        .path("duration")
                                                        .asText())
                                        .toSeconds();
                        long maximumSeconds = Math.max(1, properties.getMaxVideoDurationMinutes()) * 60L;
                        if (durationSeconds <= 0 || durationSeconds > maximumSeconds) {
                                throw new BusinessException(
                                                ErrorCode.BUSINESS_RULE_VIOLATION,
                                                "YouTube video must be 120 minutes or shorter");
                        }
                        return new YoutubeVideoMetadata(durationSeconds);
                } catch (RestClientResponseException exception) {
                        log.warn(
                                        "YouTube metadata request failed: status={} videoId={}",
                                        exception.getStatusCode().value(),
                                        videoId);
                        throw new BusinessException(
                                        ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE,
                                        "YouTube is temporarily unavailable");

                } catch (RestClientException | DateTimeException | IOException exception) {
                        log.warn(
                                        "YouTube metadata request failed: videoId={} errorType={}",
                                        videoId,
                                        exception.getClass().getSimpleName());
                        throw new BusinessException(
                                        ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE,
                                        "YouTube is temporarily unavailable");

                }
        }

        private void validateYoutubeMetadataConfiguration() {
                if (!properties.isEnabled()
                                || properties.getYoutubeApiKey() == null
                                || properties.getYoutubeApiKey().isBlank()) {
                        throw new BusinessException(
                                        ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE,
                                        "YouTube summary is not configured");
                }
        }

        private static RestClient createYoutubeClient(VideoAiProperties properties) {
                SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
                Duration timeout = properties.getYoutubeApiTimeout();
                if (timeout == null || timeout.isZero() || timeout.isNegative()) {
                        timeout = Duration.ofSeconds(20);
                }
                factory.setConnectTimeout(timeout);
                factory.setReadTimeout(timeout);
                return RestClient.builder()
                                .baseUrl(properties.getYoutubeApiBaseUrl())
                                .requestFactory(factory)
                                .build();
        }

        public record YoutubeVideoMetadata(long durationSeconds) {
        }
}
