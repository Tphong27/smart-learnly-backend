package com.smartlearnly.backend.auth.password.service;

import com.smartlearnly.backend.auth.config.AuthProperties;
import com.smartlearnly.backend.auth.password.dto.ChangePasswordRequest;
import com.smartlearnly.backend.auth.password.dto.ForgotPasswordRequest;
import com.smartlearnly.backend.auth.password.dto.ResetPasswordRequest;
import com.smartlearnly.backend.auth.password.entity.PasswordResetToken;
import com.smartlearnly.backend.auth.password.repository.PasswordResetTokenRepository;
import com.smartlearnly.backend.auth.service.EmailService;
import com.smartlearnly.backend.auth.session.service.AuthSessionService;
import com.smartlearnly.backend.common.audit.AuditLogService;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.user.entity.UserAccount;
import com.smartlearnly.backend.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthPasswordService {
    private static final Logger log = LoggerFactory.getLogger(AuthPasswordService.class);

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;
    private final AuthProperties authProperties;
    private final CurrentUserService currentUserService;
    private final AuthSessionService authSessionService;
    private final EmailService emailService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    // Tạo reset token cho tài khoản tồn tại nhưng luôn giữ response chống dò email.
    public void forgotPassword(ForgotPasswordRequest request) {
        findUserByEmail(request.email()).ifPresent(user -> {
            Instant now = Instant.now();
            passwordResetTokenRepository.markAllUnusedAsUsed(user.getId(), now);

            String rawToken = generateRawToken();
            PasswordResetToken token = new PasswordResetToken();
            token.setUser(user);
            token.setTokenHash(hashToken(rawToken));
            token.setExpiresAt(now.plus(authProperties.getPasswordResetTokenTtl()));
            passwordResetTokenRepository.save(token);

            logDebugToken(user.getEmail(), rawToken, token.getExpiresAt());
            emailService.sendPasswordResetLink(
                    user.getEmail(),
                    user.getFullName(),
                    authProperties.getFrontendBaseUrl() + "/reset-password?token=" + rawToken);
            auditLogService.record(user.getEmail(), "PASSWORD_RESET_REQUESTED", "USER", user.getId().toString());
        });
    }

    @Transactional
    // Đổi mật khẩu bằng reset token dùng một lần và thu hồi mọi session cũ.
    public void resetPassword(ResetPasswordRequest request) {
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Password confirmation does not match");
        }

        Instant now = Instant.now();
        PasswordResetToken token = passwordResetTokenRepository.findByTokenHash(hashToken(request.token()))
                .filter(savedToken -> savedToken.isUsable(now))
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.INVALID_OR_EXPIRED_TOKEN,
                        "Password reset token is invalid or expired"));

        UserAccount user = token.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setPasswordChangedAt(now);
        userRepository.save(user);
        authSessionService.revokeAll(user);

        token.setUsedAt(now);
        passwordResetTokenRepository.save(token);
        auditLogService.record(user.getEmail(), "PASSWORD_RESET_COMPLETED", "USER", user.getId().toString());
    }

    @Transactional
    // Kiểm tra mật khẩu hiện tại, đổi sang mật khẩu mới và thu hồi mọi session cũ.
    public void changeCurrentUserPassword(ChangePasswordRequest request) {
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Password confirmation does not match");
        }

        UserAccount user = currentUserService.requireAuthenticatedUser();
        if (user.getPasswordHash() == null || user.getPasswordHash().isBlank()) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Password change is not available for this account");
        }
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BusinessException(
                    ErrorCode.INVALID_CREDENTIALS,
                    "Current password is incorrect");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_RULE_VIOLATION,
                    "New password must be different from the current password");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setPasswordChangedAt(Instant.now());
        userRepository.save(user);
        authSessionService.revokeAll(user);
        auditLogService.record(user.getEmail(), "PASSWORD_CHANGED", "USER", user.getId().toString());
    }

    // Tìm tài khoản theo email đã chuẩn hóa mà không phân biệt hoa/thường.
    private Optional<UserAccount> findUserByEmail(String email) {
        return userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(normalizeEmail(email));
    }

    // Sinh reset token ngẫu nhiên đủ mạnh để chỉ gửi cho người dùng.
    private String generateRawToken() {
        byte[] tokenBytes = new byte[32];
        secureRandom.nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }

    // Băm reset token bằng SHA-256 trước khi lưu hoặc truy vấn database.
    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is not available", exception);
        }
    }

    // Chỉ ghi reset token rõ trong môi trường debug được bật tường minh.
    private void logDebugToken(String email, String rawToken, Instant expiresAt) {
        if (authProperties.isDebugLogTokens()) {
            log.info(
                    "Generated password-reset token for email={} token={} expiresAt={}",
                    email,
                    rawToken,
                    expiresAt);
        }
    }

    // Chuẩn hóa email để truy vấn auth ổn định.
    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
