package com.smartlearnly.backend.videoai.generation;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.video-ai.generation")
public class VideoAiGenerationProperties {
    private boolean enabled = false;
    private String apiKey;
    private String apiBaseUrl = "https://generativelanguage.googleapis.com/v1beta";
    private String model = "gemini-3.5-flash";
    private Duration timeout = Duration.ofSeconds(90);
    private int maxTranscriptCharacters = 120000;
}
