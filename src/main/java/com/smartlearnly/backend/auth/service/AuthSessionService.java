package com.smartlearnly.backend.auth.service;

import com.smartlearnly.backend.auth.config.AuthProperties;
import com.smartlearnly.backend.auth.dto.AuthSessionResponse;
import com.smartlearnly.backend.auth.dto.UserProfileResponse;
import com.smartlearnly.backend.auth.entity.RefreshToken;
import com.smartlearnly.backend.auth.repository.RefreshTokenRepository;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.user.entity.UserAccount;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthSessionService {
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenService jwtTokenService;
    private final AuthProperties authProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public IssuedSession issue(UserAccount user, String deviceInfo, String ipAddress) {
        String rawRefreshToken = generateRawToken();
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setTokenHash(hashToken(rawRefreshToken));
        refreshToken.setDeviceInfo(limit(deviceInfo, 255));
        refreshToken.setIpAddress(limit(ipAddress, 45));
        refreshToken.setExpiresAt(Instant.now().plus(authProperties.getRefreshTokenTtl()));
        refreshTokenRepository.save(refreshToken);
        return new IssuedSession(toResponse(user), rawRefreshToken);
    }

    @Transactional
    public IssuedSession rotate(String rawRefreshToken, String deviceInfo, String ipAddress) {
        Instant now = Instant.now();
        RefreshToken savedToken = refreshTokenRepository.findByTokenHash(hashToken(rawRefreshToken))
                .filter(token -> token.isUsable(now))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_OR_EXPIRED_TOKEN, "Refresh token is invalid or expired"));
        savedToken.setRevokedAt(now);
        refreshTokenRepository.save(savedToken);
        return issue(savedToken.getUser(), deviceInfo, ipAddress);
    }

    @Transactional
    public void revoke(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(hashToken(rawRefreshToken)).ifPresent(token -> {
            if (token.getRevokedAt() == null) {
                token.setRevokedAt(Instant.now());
                refreshTokenRepository.save(token);
            }
        });
    }

    @Transactional
    public void revokeAll(UserAccount user) {
        refreshTokenRepository.revokeAllActiveByUserId(user.getId(), Instant.now());
    }

    private AuthSessionResponse toResponse(UserAccount user) {
        UserProfileResponse profile = new UserProfileResponse(
                user.getId(), user.getEmail(), user.getFullName(), user.getAvatarUrl(), user.getPhoneNumber(),
                user.getBio(), user.getRole(), user.getStatus(), user.isEmailVerified(), user.getEmailVerifiedAt(),
                user.getCreatedAt(), user.getUpdatedAt()
        );
        return new AuthSessionResponse(
                jwtTokenService.createAccessToken(user),
                "Bearer",
                authProperties.getAccessTokenTtl().toSeconds(),
                profile
        );
    }

    private String generateRawToken() {
        byte[] tokenBytes = new byte[48];
        secureRandom.nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is not available", exception);
        }
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    public record IssuedSession(AuthSessionResponse response, String refreshToken) {
    }
}
