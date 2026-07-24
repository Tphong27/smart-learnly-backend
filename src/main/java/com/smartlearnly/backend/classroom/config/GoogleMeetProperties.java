package com.smartlearnly.backend.classroom.config;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.integrations.google-meet")
public class GoogleMeetProperties {
    private boolean enabled = false;
    private String refreshToken;
    private String tokenBaseUrl = "https://oauth2.googleapis.com";
    private String apiBaseUrl = "https://meet.googleapis.com";
    private Duration timeout = Duration.ofSeconds(20);
}