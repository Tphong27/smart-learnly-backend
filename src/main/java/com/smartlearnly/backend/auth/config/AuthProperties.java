package com.smartlearnly.backend.auth.config;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.auth")
public class AuthProperties {
    private Duration emailVerificationOtpTtl = Duration.ofMinutes(15);
    private int emailVerificationOtpMaxAttempts = 5;
    private int emailVerificationRequestLimit = 3;
    private Duration emailVerificationRequestWindow = Duration.ofMinutes(15);
    private Duration passwordResetTokenTtl = Duration.ofMinutes(30);
    private Duration accessTokenTtl = Duration.ofMinutes(15);
    private Duration refreshTokenTtl = Duration.ofDays(7);
    private String frontendBaseUrl = "http://localhost:5173";
    private boolean refreshCookieSecure;
    private int loginMaxFailures = 5;
    private Duration loginLockDuration = Duration.ofMinutes(30);
    private boolean debugLogTokens;
}
