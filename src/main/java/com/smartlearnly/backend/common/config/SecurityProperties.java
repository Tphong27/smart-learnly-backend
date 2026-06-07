package com.smartlearnly.backend.common.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.security")
public class SecurityProperties {
    private AuthenticationMode authenticationMode = AuthenticationMode.BASIC;
    private String jwtSecret;
    private Jwt jwt = new Jwt();

    public enum AuthenticationMode {
        BASIC,
        JWT
    }

    @Getter
    @Setter
    public static class Jwt {
        private String emailClaim = "email";
        private String userIdClaim = "user_id";
        private String authUserIdClaim = "sub";
        private String rolesClaim = "roles";
        private String rolePrefix = "ROLE_";
    }
}
