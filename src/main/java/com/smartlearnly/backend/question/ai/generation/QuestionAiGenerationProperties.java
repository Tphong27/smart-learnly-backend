package com.smartlearnly.backend.question.ai.generation;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.question-ai-generation")
public class QuestionAiGenerationProperties {
    private boolean enabled = true;
    private String provider = "gemini";
    private String apiKey;
    private String apiBaseUrl = "https://generativelanguage.googleapis.com/v1beta";
    private String model = "gemini-3.5-flash";
    private Duration timeout = Duration.ofSeconds(60);
    private int maxBatchesPerUserDay = 5;
}
