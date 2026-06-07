package com.smartlearnly.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.auth.config.AuthProperties;
import com.smartlearnly.backend.auth.entity.RefreshToken;
import com.smartlearnly.backend.auth.repository.RefreshTokenRepository;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.user.entity.UserAccount;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthSessionServiceTest {
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private JwtTokenService jwtTokenService;

    private AuthSessionService authSessionService;

    @BeforeEach
    void setUp() {
        AuthProperties properties = new AuthProperties();
        properties.setAccessTokenTtl(Duration.ofMinutes(15));
        properties.setRefreshTokenTtl(Duration.ofDays(7));
        authSessionService = new AuthSessionService(refreshTokenRepository, jwtTokenService, properties);
    }

    @Test
    void issueShouldStoreHashedRefreshToken() {
        UserAccount user = activeUser();
        when(jwtTokenService.createAccessToken(user)).thenReturn("access-token");

        AuthSessionService.IssuedSession session = authSessionService.issue(user, "browser", "127.0.0.1");

        assertThat(session.refreshToken()).isNotBlank();
        assertThat(session.response().accessToken()).isEqualTo("access-token");
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void rotateShouldRevokeOldTokenAndIssueNewToken() {
        UserAccount user = activeUser();
        RefreshToken oldToken = new RefreshToken();
        oldToken.setUser(user);
        oldToken.setTokenHash(hash("old-token"));
        oldToken.setExpiresAt(Instant.now().plusSeconds(300));
        when(refreshTokenRepository.findByTokenHash(hash("old-token"))).thenReturn(Optional.of(oldToken));
        when(jwtTokenService.createAccessToken(user)).thenReturn("new-access-token");

        AuthSessionService.IssuedSession session = authSessionService.rotate("old-token", "browser", "127.0.0.1");

        assertThat(oldToken.getRevokedAt()).isNotNull();
        assertThat(session.refreshToken()).isNotEqualTo("old-token");
    }

    @Test
    void rotateShouldRejectRevokedToken() {
        RefreshToken revokedToken = new RefreshToken();
        revokedToken.setUser(activeUser());
        revokedToken.setTokenHash(hash("revoked-token"));
        revokedToken.setExpiresAt(Instant.now().plusSeconds(300));
        revokedToken.setRevokedAt(Instant.now());
        when(refreshTokenRepository.findByTokenHash(hash("revoked-token"))).thenReturn(Optional.of(revokedToken));

        assertThatThrownBy(() -> authSessionService.rotate("revoked-token", null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Refresh token is invalid or expired");
    }

    private UserAccount activeUser() {
        UserAccount user = new UserAccount();
        user.setId(UUID.randomUUID());
        user.setEmail("active@example.com");
        user.setFullName("Active User");
        user.setRole("TRAINEE");
        user.setStatus("active");
        user.setEmailVerifiedAt(Instant.now());
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        return user;
    }

    private String hash(String rawToken) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(
                    digest.digest(rawToken.getBytes(java.nio.charset.StandardCharsets.UTF_8))
            );
        }
        catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
