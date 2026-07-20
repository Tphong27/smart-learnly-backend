package com.smartlearnly.backend.hls.config;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.hls")
public class HlsProperties {

    private static final String DEFAULT_TOKEN_SECRET = "HLS_TOKEN_SECRET_CHANGE_ME_IN_PRODUCTION";
    private static final int MIN_SECRET_LENGTH = 32;

    private boolean enabled = false;
    private String processingProvider = "local";
    private String tokenSecret = DEFAULT_TOKEN_SECRET;
    private String r2Secret;
    private String r2BasePath = "hls";
    private String rawBucket;
    private String outputBucket;
    private String aiAudioBucket;
    private String callbackSecret;
    private int callbackTimestampToleranceSeconds = 300;
    private int segmentDuration = 10;
    private String qualities = "480p,720p";
    private String ffmpegPreset = "veryfast";
    private String outputDir = "/tmp/hls";
    private int masterTokenTtlSeconds = 900;  // 15 minutes
    private int r2TokenTtlSeconds = 7200;      // 2 hours
    private int segmentTokenTtlSeconds = 7200; // Covers normal VOD playback sessions
    private int presignedUrlTtlSeconds = 60;    // 1 minute

    @PostConstruct
    void validateSecurityConfiguration() {
        if (!enabled) {
            return;
        }

        requireStrongSecret(
                "app.hls.token-secret",
                tokenSecret,
                DEFAULT_TOKEN_SECRET
        );

        if (usesGithubActions()) {
            requireStrongSecret(
                    "app.hls.callback-secret",
                    callbackSecret,
                    null
            );
        }
    }

    private static void requireStrongSecret(String propertyName, String value, String defaultValue) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() < MIN_SECRET_LENGTH || normalized.equals(defaultValue)) {
            throw new IllegalStateException(
                    propertyName + " must be configured with at least "
                            + MIN_SECRET_LENGTH
                            + " characters when HLS is enabled"
            );
        }
    }

    public boolean usesGithubActions() {
        return "github-actions".equalsIgnoreCase(processingProvider);
    }

    public boolean usesLocalProcessing() {
        return "local".equalsIgnoreCase(processingProvider);
    }

    public String normalizedQualities() {
        Set<String> selected = new LinkedHashSet<>();
        if (qualities != null) {
            for (String quality : qualities.split(",")) {
                String normalized = quality.trim().toLowerCase(Locale.ROOT);
                if (Set.of("480p", "720p", "1080p").contains(normalized)) {
                    selected.add(normalized);
                }
            }
        }
        if (selected.isEmpty()) {
            selected.add("720p");
        }
        return String.join(",", selected);
    }

    public int normalizedSegmentDuration() {
        return Math.min(30, Math.max(2, segmentDuration));
    }

    public String normalizedFfmpegPreset() {
        String normalized = ffmpegPreset == null
                ? ""
                : ffmpegPreset.trim().toLowerCase(Locale.ROOT);
        if (Set.of(
                "ultrafast",
                "superfast",
                "veryfast",
                "faster",
                "fast",
                "medium"
        ).contains(normalized)) {
            return normalized;
        }
        return "veryfast";
    }
}
