package com.smartlearnly.backend.videoai.service;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.videoai.dto.VideoAiDtos.GeneratedSummary;
import com.smartlearnly.backend.videoai.dto.VideoAiDtos.GenerateSummaryResponse;
import com.smartlearnly.backend.videoai.service.YoutubeVideoMetadataService.YoutubeVideoMetadata;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

@Service
public class VideoSummaryService {

    private final YoutubeVideoMetadataService metadataService;
    private final YoutubeTranscriptService transcriptService;
    private final GeminiVideoSummaryService summaryService;

    public VideoSummaryService(
            YoutubeVideoMetadataService metadataService,
            YoutubeTranscriptService transcriptService,
            GeminiVideoSummaryService summaryService) {
        this.metadataService = metadataService;
        this.transcriptService = transcriptService;
        this.summaryService = summaryService;
    }

    public GenerateSummaryResponse generateVideoSummary(String youtubeUrl) {
        String videoId = extractVideoIdFromYoutubeUrl(youtubeUrl);
        YoutubeVideoMetadata metadata =
                metadataService.fetchYoutubeVideoMetadata(videoId);
        YoutubeTranscriptService.TranscriptResult transcript =
                transcriptService.fetchYoutubeTranscript(videoId);
        GeneratedSummary summary =
                summaryService.generateSummaryFromTranscript(
                        transcript.language(),
                        transcript.text());
        long durationSeconds = metadata.durationSeconds();
        return new GenerateSummaryResponse(
                videoId,
                buildCanonicalYoutubeUrl(videoId),
                durationSeconds,
                Math.toIntExact((durationSeconds + 59) / 60),
                summary);
    }

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
            throw invalidUrl();
        }

        if (current != null && current.equals(requested)) {
            return current;
        }
        return buildCanonicalYoutubeUrl(
                extractVideoIdFromYoutubeUrl(requested));
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    String extractVideoIdFromYoutubeUrl(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw invalidUrl();
        }

        try {
            URI uri = new URI(normalized);
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || uri.getHost() == null
                    || uri.getUserInfo() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443)) {
                throw invalidUrl();
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
                throw invalidUrl();
            }
            return videoId;
        } catch (URISyntaxException | IllegalArgumentException exception) {
            throw invalidUrl();
        }
    }

    private String buildCanonicalYoutubeUrl(String videoId) {
        return "https://www.youtube.com/watch?v=" + videoId;
    }

    private BusinessException invalidUrl() {
        return new BusinessException(
                ErrorCode.INVALID_REQUEST,
                "Enter a valid HTTPS YouTube URL");
    }

}
