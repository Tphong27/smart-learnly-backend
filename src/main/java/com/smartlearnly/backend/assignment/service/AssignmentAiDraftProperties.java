package com.smartlearnly.backend.assignment.service;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.assignment-ai")
public class AssignmentAiDraftProperties {

    private boolean enabled = true;
    private String provider = "gemini";
    private String apiKey;
    private String apiBaseUrl = "https://generativelanguage.googleapis.com/v1beta";
    private String model = "gemini-flash-latest";
    private String fallbackModel = "gemini-flash-lite-latest";
    private Duration timeout = Duration.ofSeconds(60);
}
