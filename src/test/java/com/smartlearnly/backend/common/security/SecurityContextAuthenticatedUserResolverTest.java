package com.smartlearnly.backend.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartlearnly.backend.common.config.SecurityProperties;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class SecurityContextAuthenticatedUserResolverTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldResolveBasicAuthenticationPrincipal() {
        SecurityProperties properties = new SecurityProperties();
        SecurityContextAuthenticatedUserResolver resolver = new SecurityContextAuthenticatedUserResolver(properties);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "active.trainee@smartlearnly.dev",
                        "N/A",
                        AuthorityUtils.createAuthorityList("ROLE_TRAINEE")
                )
        );

        CurrentUser currentUser = resolver.resolve().orElseThrow();

        assertThat(currentUser.email()).isEqualTo("active.trainee@smartlearnly.dev");
        assertThat(currentUser.id()).isNull();
        assertThat(currentUser.authUserId()).isNull();
        assertThat(currentUser.roles()).containsExactly("TRAINEE");
    }

    @Test
    void shouldResolveJwtClaims() {
        SecurityProperties properties = new SecurityProperties();
        SecurityContextAuthenticatedUserResolver resolver = new SecurityContextAuthenticatedUserResolver(properties);

        UUID userId = UUID.randomUUID();
        UUID authUserId = UUID.randomUUID();
        Jwt jwt = new Jwt(
                "token-value",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "HS256"),
                Map.of(
                        "user_id", userId.toString(),
                        "sub", authUserId.toString(),
                        "email", "jwt.user@smartlearnly.dev",
                        "roles", List.of("TRAINEE", "LEARNER")
                )
        );
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(
                jwt,
                AuthorityUtils.createAuthorityList("ROLE_TRAINEE", "ROLE_LEARNER")
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);

        CurrentUser currentUser = resolver.resolve().orElseThrow();

        assertThat(currentUser.id()).isEqualTo(userId);
        assertThat(currentUser.authUserId()).isEqualTo(authUserId);
        assertThat(currentUser.email()).isEqualTo("jwt.user@smartlearnly.dev");
        assertThat(currentUser.roles()).containsExactly("TRAINEE", "LEARNER");
    }
}
