package com.smartlearnly.backend.auth.session.service;

import com.smartlearnly.backend.auth.config.AuthProperties;
import com.smartlearnly.backend.auth.profile.dto.UserProfileResponse;
import com.smartlearnly.backend.auth.session.dto.AuthSessionResponse;
import com.smartlearnly.backend.auth.session.entity.RefreshToken;
import com.smartlearnly.backend.auth.session.repository.RefreshTokenRepository;
import com.smartlearnly.backend.common.audit.AuditAction;
import com.smartlearnly.backend.common.audit.AuditLogService;
import com.smartlearnly.backend.common.audit.AuditResult;
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
    private final AuditLogService auditLogService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    // Phát access token và refresh token mới, chỉ lưu bản hash của refresh token.
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
    // Thu hồi refresh token cũ rồi phát cặp token mới để chống tái sử dụng.
    public IssuedSession rotate(String rawRefreshToken, String deviceInfo, String ipAddress) {
        Instant now = Instant.now();
        RefreshToken savedToken = refreshTokenRepository.findByTokenHash(hashToken(rawRefreshToken))
                .filter(token -> token.isUsable(now))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_OR_EXPIRED_TOKEN, "Refresh token is invalid or expired"));
        savedToken.setRevokedAt(now);
        refreshTokenRepository.save(savedToken);
        return issue(savedToken.getUser(), deviceInfo, ipAddress);
    }

    // Thu hồi refresh token hiện tại và ghi audit nếu xác định được chủ phiên.
    @Transactional
    public void logout(String rawRefreshToken) {
        UserAccount user = revoke(rawRefreshToken);
        if (user != null) {
            auditLogService.recordAuthentication(
                    user, user.getEmail(), AuditAction.LOGOUT_SUCCEEDED, AuditResult.SUCCESS,
                    "Logout succeeded", null, null, null);
        }
    }

    // Thu hồi một refresh token nếu tồn tại và trả về chủ token cho nghiệp vụ logout.
    private UserAccount revoke(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return null;
        }
        RefreshToken token = refreshTokenRepository.findByTokenHash(hashToken(rawRefreshToken)).orElse(null);
        if (token == null) {
            return null;
        }
        if (token.getRevokedAt() == null) {
            token.setRevokedAt(Instant.now());
            refreshTokenRepository.save(token);
        }
        return token.getUser();
    }

    @Transactional
    // Thu hồi mọi session active sau khi đổi hoặc đặt lại mật khẩu.
    public void revokeAll(UserAccount user) {
        refreshTokenRepository.revokeAllActiveByUserId(user.getId(), Instant.now());
    }

    // Tạo response session gồm access token và hồ sơ người dùng hiện tại.
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

    // Sinh refresh token ngẫu nhiên đủ mạnh để trả duy nhất cho client.
    private String generateRawToken() {
        byte[] tokenBytes = new byte[48];
        secureRandom.nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }

    // Băm refresh token bằng SHA-256 trước khi lưu hoặc truy vấn database.
    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is not available", exception);
        }
    }

    // Chuẩn hóa và giới hạn metadata thiết bị/IP theo độ dài cột database.
    private String limit(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    public record IssuedSession(AuthSessionResponse response, String refreshToken) {
    }
}
