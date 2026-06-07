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
    private Duration emailVerificationTokenTtl = Duration.ofHours(24);
    private Duration passwordResetTokenTtl = Duration.ofMinutes(30);
    private boolean debugLogTokens = true;
}
