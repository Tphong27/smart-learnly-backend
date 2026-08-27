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
    private static final int DEFAULT_MAX_BATCHES_PER_USER_DAY = 1_000_000;

    private boolean enabled = true;
    private String provider = "gemini";
    private String apiKey;
    private String apiBaseUrl = "https://generativelanguage.googleapis.com/v1beta";
    private String model = "gemini-2.5-flash";
    private String fallbackModel = "gemini-3.5-flash-lite";
    private Duration timeout = Duration.ofSeconds(60);
    private int maxBatchesPerUserDay = DEFAULT_MAX_BATCHES_PER_USER_DAY;
}
