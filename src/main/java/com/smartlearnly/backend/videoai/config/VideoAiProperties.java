package com.smartlearnly.backend.videoai.config;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.video-ai")
public class VideoAiProperties {
    private boolean enabled = false;
    private int maxAttempts = 3;
    private Duration leaseDuration = Duration.ofHours(3);
}
