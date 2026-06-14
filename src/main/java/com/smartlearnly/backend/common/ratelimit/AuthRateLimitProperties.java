package com.smartlearnly.backend.common.ratelimit;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.rate-limit.auth")
public class AuthRateLimitProperties {
    private boolean enabled = true;
    private int capacity = 5;
    private Duration window = Duration.ofMinutes(15);
}