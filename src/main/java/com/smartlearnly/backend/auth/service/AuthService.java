package com.smartlearnly.backend.auth.service;

import com.smartlearnly.backend.auth.config.AuthProperties;
import com.smartlearnly.backend.auth.dto.ChangePasswordRequest;
import com.smartlearnly.backend.auth.dto.ForgotPasswordRequest;
import com.smartlearnly.backend.auth.dto.ResetPasswordRequest;
import com.smartlearnly.backend.auth.dto.ResendVerificationRequest;
import com.smartlearnly.backend.auth.dto.UpdateProfileRequest;
import com.smartlearnly.backend.auth.dto.UserProfileResponse;
import com.smartlearnly.backend.auth.dto.VerifyEmailRequest;
import com.smartlearnly.backend.auth.entity.EmailVerificationToken;
import com.smartlearnly.backend.auth.entity.PasswordResetToken;
import com.smartlearnly.backend.auth.repository.EmailVerificationTokenRepository;
import com.smartlearnly.backend.auth.repository.PasswordResetTokenRepository;
import com.smartlearnly.backend.common.audit.AuditLogService;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.SecurityUtils;
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
public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;
    private final AuthProperties authProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        findActiveUserByEmail(request.email()).ifPresent(user -> {
            Instant now = Instant.now();
            passwordResetTokenRepository.markAllUnusedAsUsed(user.getId(), now);

            String rawToken = generateRawToken();
            PasswordResetToken token = new PasswordResetToken();
            token.setUser(user);
            token.setTokenHash(hashToken(rawToken));
            token.setExpiresAt(now.plus(authProperties.getPasswordResetTokenTtl()));
            passwordResetTokenRepository.save(token);

            logDebugToken("password-reset", user.getEmail(), rawToken, token.getExpiresAt());
            auditLogService.record(user.getEmail(), "PASSWORD_RESET_REQUESTED", "USER", user.getId().toString());
        });
    }

    @Transactional
    public void resendVerification(ResendVerificationRequest request) {
        findActiveUserByEmail(request.email())
                .filter(user -> !user.isEmailVerified())
                .ifPresent(user -> {
                    Instant now = Instant.now();
                    emailVerificationTokenRepository.markAllUnusedAsUsed(user.getId(), now);

                    String rawToken = generateRawToken();
                    EmailVerificationToken token = new EmailVerificationToken();
                    token.setUser(user);
                    token.setTokenHash(hashToken(rawToken));
                    token.setExpiresAt(now.plus(authProperties.getEmailVerificationTokenTtl()));
                    emailVerificationTokenRepository.save(token);

                    logDebugToken("email-verification", user.getEmail(), rawToken, token.getExpiresAt());
                    auditLogService.record(user.getEmail(), "EMAIL_VERIFICATION_RESENT", "USER", user.getId().toString());
                });
    }

    @Transactional
    public void verifyEmail(VerifyEmailRequest request) {
        Instant now = Instant.now();
        EmailVerificationToken token = emailVerificationTokenRepository.findByTokenHash(hashToken(request.token()))
                .filter(savedToken -> savedToken.isUsable(now))
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.INVALID_OR_EXPIRED_TOKEN,
                        "Email verification token is invalid or expired"
                ));

        UserAccount user = token.getUser();
        if (!user.isEmailVerified()) {
            user.setEmailVerifiedAt(now);
            if ("pending_verify".equalsIgnoreCase(user.getStatus())) {
                user.setStatus("active");
            }
            userRepository.save(user);
        }

        token.setUsedAt(now);
        emailVerificationTokenRepository.save(token);
        auditLogService.record(user.getEmail(), "EMAIL_VERIFIED", "USER", user.getId().toString());
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        validatePasswordConfirmation(request.newPassword(), request.confirmPassword());

        Instant now = Instant.now();
        PasswordResetToken token = passwordResetTokenRepository.findByTokenHash(hashToken(request.token()))
                .filter(savedToken -> savedToken.isUsable(now))
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.INVALID_OR_EXPIRED_TOKEN,
                        "Password reset token is invalid or expired"
                ));

        UserAccount user = token.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setPasswordChangedAt(now);
        userRepository.save(user);

        token.setUsedAt(now);
        passwordResetTokenRepository.save(token);
        auditLogService.record(user.getEmail(), "PASSWORD_RESET_COMPLETED", "USER", user.getId().toString());
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getCurrentUserProfile() {
        UserAccount user = getAuthenticatedUser();
        return toUserProfileResponse(user);
    }

    @Transactional
    public UserProfileResponse updateCurrentUserProfile(UpdateProfileRequest request) {
        UserAccount user = getAuthenticatedUser();
        boolean changed = false;

        if (request.fullName() != null) {
            user.setFullName(request.fullName().trim());
            changed = true;
        }
        if (request.avatarUrl() != null) {
            user.setAvatarUrl(normalizeNullable(request.avatarUrl()));
            changed = true;
        }
        if (request.phoneNumber() != null) {
            user.setPhoneNumber(normalizeNullable(request.phoneNumber()));
            changed = true;
        }
        if (request.bio() != null) {
            user.setBio(normalizeNullable(request.bio()));
            changed = true;
        }

        if (!changed) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "At least one profile field must be provided"
            );
        }

        UserAccount savedUser = userRepository.save(user);
        auditLogService.record(savedUser.getEmail(), "PROFILE_UPDATED", "USER", savedUser.getId().toString());
        return toUserProfileResponse(savedUser);
    }

    @Transactional
    public void changeCurrentUserPassword(ChangePasswordRequest request) {
        validatePasswordConfirmation(request.newPassword(), request.confirmPassword());

        UserAccount user = getAuthenticatedUser();
        if (user.getPasswordHash() == null || user.getPasswordHash().isBlank()) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Password change is not available for this account"
            );
        }
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BusinessException(
                    ErrorCode.INVALID_CREDENTIALS,
                    "Current password is incorrect"
            );
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_RULE_VIOLATION,
                    "New password must be different from the current password"
            );
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setPasswordChangedAt(Instant.now());
        userRepository.save(user);
        auditLogService.record(user.getEmail(), "PASSWORD_CHANGED", "USER", user.getId().toString());
    }

    private Optional<UserAccount> findActiveUserByEmail(String email) {
        return userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(normalizeEmail(email));
    }

    private UserAccount getAuthenticatedUser() {
        String currentEmail = SecurityUtils.currentPrincipalName()
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED));

        return userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(currentEmail)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Authenticated user was not found"
                ));
    }

    private UserProfileResponse toUserProfileResponse(UserAccount user) {
        return new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getAvatarUrl(),
                user.getPhoneNumber(),
                user.getBio(),
                user.getRole(),
                user.getStatus(),
                user.isEmailVerified(),
                user.getEmailVerifiedAt(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }

    private void logDebugToken(String tokenType, String email, String rawToken, Instant expiresAt) {
        if (authProperties.isDebugLogTokens()) {
            log.info(
                    "Generated {} token for email={} token={} expiresAt={}",
                    tokenType,
                    email,
                    rawToken,
                    expiresAt
            );
        }
    }

    private void validatePasswordConfirmation(String newPassword, String confirmPassword) {
        if (!newPassword.equals(confirmPassword)) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Password confirmation does not match"
            );
        }
    }

    private String generateRawToken() {
        byte[] tokenBytes = new byte[32];
        secureRandom.nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }

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

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
