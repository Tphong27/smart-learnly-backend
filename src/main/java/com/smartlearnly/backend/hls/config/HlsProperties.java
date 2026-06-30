package com.smartlearnly.backend.hls.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.hls")
public class HlsProperties {

    private boolean enabled = false;
    private String tokenSecret = "HLS_TOKEN_SECRET_CHANGE_ME_IN_PRODUCTION";
    private String r2Secret;
    private String r2BasePath = "hls";
    private int segmentDuration = 10;
    private String qualities = "480p,720p,1080p";
    private String outputDir = "/tmp/hls";
    private int masterTokenTtlSeconds = 900;  // 15 minutes
    private int r2TokenTtlSeconds = 7200;      // 2 hours
    private int segmentTokenTtlSeconds = 7200; // Covers normal VOD playback sessions
    private int presignedUrlTtlSeconds = 60;    // 1 minute
}
