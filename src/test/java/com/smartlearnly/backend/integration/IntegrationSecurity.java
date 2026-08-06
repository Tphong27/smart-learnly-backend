package com.smartlearnly.backend.integration;

import java.util.List;
import java.util.UUID;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

public final class IntegrationSecurity {
    private IntegrationSecurity() {
    }

    public static RequestPostProcessor asUser(UUID userId, String email, String role) {
        return jwt()
                .jwt(token -> token
                        .subject(userId.toString())
                        .claim("user_id", userId.toString())
                        .claim("email", email)
                        .claim("roles", List.of(role)))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}