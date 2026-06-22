package com.smartlearnly.backend.auth.service;

import com.smartlearnly.backend.auth.config.AuthProperties;
import com.smartlearnly.backend.auth.dto.ChangePasswordRequest;
import com.smartlearnly.backend.auth.dto.ForgotPasswordRequest;
import com.smartlearnly.backend.auth.dto.LoginRequest;
import com.smartlearnly.backend.auth.dto.GoogleLoginRequest;
import com.smartlearnly.backend.auth.dto.RegisterRequest;
import com.smartlearnly.backend.auth.dto.ResetPasswordRequest;
import com.smartlearnly.backend.auth.dto.ResendVerificationRequest;
import com.smartlearnly.backend.auth.dto.UpdateProfileRequest;
import com.smartlearnly.backend.auth.dto.UserProfileResponse;
import com.smartlearnly.backend.auth.dto.VerifyEmailRequest;
import com.smartlearnly.backend.auth.entity.LoginHistory;
import com.smartlearnly.backend.auth.entity.OtpVerification;
import com.smartlearnly.backend.auth.entity.PasswordResetToken;
import com.smartlearnly.backend.auth.repository.LoginHistoryRepository;
import com.smartlearnly.backend.auth.repository.OtpVerificationRepository;
import com.smartlearnly.backend.auth.repository.PasswordResetTokenRepository;
import com.smartlearnly.backend.common.audit.AuditAction;
import com.smartlearnly.backend.common.audit.AuditLogService;
import com.smartlearnly.backend.common.audit.AuditResult;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.AuthenticatedUserResolver;
import com.smartlearnly.backend.common.security.CurrentUser;
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
    private final OtpVerificationRepository otpVerificationRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;
    private final AuthProperties authProperties;
    private final AuthenticatedUserResolver authenticatedUserResolver;
    private final AuthSessionService authSessionService;
    private final EmailService emailService;
    private final LoginHistoryRepository loginHistoryRepository;
    private final GoogleIdTokenService googleIdTokenService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public void register(RegisterRequest request) {
        validatePasswordConfirmation(request.password(), request.confirmPassword());
        String email = normalizeEmail(request.email());
        if (userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email).isPresent()) {
            throw new BusinessException(ErrorCode.CONFLICT, "Email already exists");
        }

        UserAccount user = new UserAccount();
        user.setEmail(email);
        user.setFullName(request.fullName().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole("TRAINEE");
        user.setStatus("pending_verify");
        user.setFailedLoginAttempts(0);
        UserAccount savedUser = userRepository.save(user);

        issueVerificationOtp(savedUser);
        auditLogService.record(savedUser.getEmail(), "ACCOUNT_REGISTERED", "USER", savedUser.getId().toString());
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public AuthSessionService.IssuedSession login(LoginRequest request, String deviceInfo, String ipAddress) {
        String email = normalizeEmail(request.email());
        UserAccount user = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email)
                .orElseThrow(() -> {
                    recordLogin(null, email, ipAddress, deviceInfo, "email", "failed");
                    auditLogService.recordAuthentication(null, email, AuditAction.LOGIN_FAILED, AuditResult.FAILURE,
                            "Login failed", ipAddress, deviceInfo, ErrorCode.INVALID_CREDENTIALS.name());
                    return new BusinessException(ErrorCode.INVALID_CREDENTIALS);
                });

        Instant now = Instant.now();
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
            recordLogin(user, email, ipAddress, deviceInfo, "email", "blocked");
            auditLogService.recordAuthentication(user, email, AuditAction.LOGIN_BLOCKED, AuditResult.DENIED,
                    "Login was blocked", ipAddress, deviceInfo, ErrorCode.ACCOUNT_LOCKED.name());
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED, "Account is locked until " + user.getLockedUntil());
        }
        if (!"active".equalsIgnoreCase(user.getStatus())) {
            ErrorCode errorCode = "pending_verify".equalsIgnoreCase(user.getStatus()) || !user.isEmailVerified()
                    ? ErrorCode.EMAIL_NOT_VERIFIED
                    : ErrorCode.ACCOUNT_INACTIVE;
            auditLogService.recordAuthentication(
                    user, email, AuditAction.LOGIN_FAILED, AuditResult.DENIED,
                    "Login was denied", ipAddress, deviceInfo, errorCode.name()
            );
            throw new BusinessException(errorCode);
        }
        if (user.getPasswordHash() == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            registerFailedLogin(user, now);
            boolean locked = user.getLockedUntil() != null && user.getLockedUntil().isAfter(now);
            recordLogin(user, email, ipAddress, deviceInfo, "email", locked ? "blocked" : "failed");
            auditLogService.recordAuthentication(
                    user,
                    email,
                    locked ? AuditAction.LOGIN_BLOCKED : AuditAction.LOGIN_FAILED,
                    locked ? AuditResult.DENIED : AuditResult.FAILURE,
                    locked ? "Login was blocked" : "Login failed",
                    ipAddress,
                    deviceInfo,
                    locked ? ErrorCode.ACCOUNT_LOCKED.name() : ErrorCode.INVALID_CREDENTIALS.name()
            );
            throw new BusinessException(locked ? ErrorCode.ACCOUNT_LOCKED : ErrorCode.INVALID_CREDENTIALS);
        }

        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(now);
        userRepository.save(user);
        recordLogin(user, email, ipAddress, deviceInfo, "email", "success");
        auditLogService.recordAuthentication(user, email, AuditAction.LOGIN_SUCCEEDED, AuditResult.SUCCESS,
                "Login succeeded", ipAddress, deviceInfo, null);
        return authSessionService.issue(user, deviceInfo, ipAddress);
    }

    @Transactional
    public AuthSessionService.IssuedSession loginWithGoogle(
            GoogleLoginRequest request,
            String deviceInfo,
            String ipAddress
    ) {
        GoogleIdTokenService.GoogleIdentity identity = googleIdTokenService.verify(request.idToken());
        String email = normalizeEmail(identity.email());
        UserAccount user = userRepository.findByGoogleIdAndDeletedAtIsNull(identity.subject())
                .orElseGet(() -> linkOrCreateGoogleUser(identity, email));

        if (!"active".equalsIgnoreCase(user.getStatus())) {
            throw new BusinessException(ErrorCode.ACCOUNT_INACTIVE);
        }

        user.setLastLoginAt(Instant.now());
        userRepository.save(user);
        recordLogin(user, email, ipAddress, deviceInfo, "google", "success");
        auditLogService.recordAuthentication(user, email, AuditAction.GOOGLE_LOGIN_SUCCEEDED, AuditResult.SUCCESS,
                "Google login succeeded", ipAddress, deviceInfo, null);
        return authSessionService.issue(user, deviceInfo, ipAddress);
    }

    @Transactional
    public AuthSessionService.IssuedSession refresh(String refreshToken, String deviceInfo, String ipAddress) {
        return authSessionService.rotate(refreshToken, deviceInfo, ipAddress);
    }

    @Transactional
    public void logout(String refreshToken) {
        UserAccount user = authSessionService.revoke(refreshToken);
        if (user != null) {
            auditLogService.recordAuthentication(
                    user, user.getEmail(), AuditAction.LOGOUT_SUCCEEDED, AuditResult.SUCCESS,
                    "Logout succeeded", null, null, null
            );
        }
    }

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
            emailService.sendPasswordResetLink(
                    user.getEmail(),
                    user.getFullName(),
                    authProperties.getFrontendBaseUrl() + "/reset-password?token=" + rawToken
            );
            auditLogService.record(user.getEmail(), "PASSWORD_RESET_REQUESTED", "USER", user.getId().toString());
        });
    }

    @Transactional
    public void resendVerification(ResendVerificationRequest request) {
        findActiveUserByEmail(request.email())
                .filter(user -> !user.isEmailVerified())
                .ifPresent(user -> {
                    issueVerificationOtp(user);
                });
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public void verifyEmail(VerifyEmailRequest request) {
        Instant now = Instant.now();
        String email = normalizeEmail(request.email());
        OtpVerification otp = otpVerificationRepository
                .findTopByEmailIgnoreCaseAndPurposeAndVerifiedAtIsNullOrderByCreatedAtDesc(
                        email,
                        OtpVerification.EMAIL_VERIFY_PURPOSE
                )
                .filter(savedOtp -> savedOtp.isUsable(now))
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.INVALID_OR_EXPIRED_TOKEN,
                        "Email verification OTP is invalid or expired"
                ));

        if (!passwordEncoder.matches(request.otpCode(), otp.getOtpHash())) {
            otp.setAttempts(otp.getAttempts() + 1);
            otpVerificationRepository.save(otp);
            throw new BusinessException(
                    ErrorCode.INVALID_OR_EXPIRED_TOKEN,
                    "Email verification OTP is invalid or expired"
            );
        }

        UserAccount user = otp.getUser();
        if (!user.isEmailVerified()) {
            user.setEmailVerifiedAt(now);
            if ("pending_verify".equalsIgnoreCase(user.getStatus())) {
                user.setStatus("active");
            }
            userRepository.save(user);
        }

        otp.setVerifiedAt(now);
        otpVerificationRepository.save(otp);
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
        authSessionService.revokeAll(user);

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
        authSessionService.revokeAll(user);
        auditLogService.record(user.getEmail(), "PASSWORD_CHANGED", "USER", user.getId().toString());
    }

    private Optional<UserAccount> findActiveUserByEmail(String email) {
        return userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(normalizeEmail(email));
    }

    private UserAccount linkOrCreateGoogleUser(GoogleIdTokenService.GoogleIdentity identity, String email) {
        Optional<UserAccount> existingUser = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email);
        if (existingUser.isPresent()) {
            UserAccount user = existingUser.get();
            user.setGoogleId(identity.subject());
            if (!user.isEmailVerified()) {
                user.setEmailVerifiedAt(Instant.now());
                user.setStatus("active");
            }
            if (user.getAvatarUrl() == null && identity.avatarUrl() != null) {
                user.setAvatarUrl(identity.avatarUrl());
            }
            return userRepository.save(user);
        }

        UserAccount user = new UserAccount();
        user.setEmail(email);
        user.setGoogleId(identity.subject());
        user.setFullName(identity.fullName() == null || identity.fullName().isBlank() ? email : identity.fullName());
        user.setAvatarUrl(identity.avatarUrl());
        user.setRole("TRAINEE");
        user.setStatus("active");
        user.setEmailVerifiedAt(Instant.now());
        user.setFailedLoginAttempts(0);
        return userRepository.save(user);
    }

    private void issueVerificationOtp(UserAccount user) {
        Instant now = Instant.now();
        long recentRequests = otpVerificationRepository.countByEmailIgnoreCaseAndPurposeAndCreatedAtAfter(
                user.getEmail(),
                OtpVerification.EMAIL_VERIFY_PURPOSE,
                now.minus(authProperties.getEmailVerificationRequestWindow())
        );
        if (recentRequests >= authProperties.getEmailVerificationRequestLimit()) {
            throw new BusinessException(
                    ErrorCode.RATE_LIMIT_EXCEEDED,
                    "Too many verification OTP requests. Please try again later"
            );
        }

        otpVerificationRepository.markAllUnverifiedAsVerified(
                user.getId(),
                OtpVerification.EMAIL_VERIFY_PURPOSE,
                now
        );

        String otpCode = generateOtpCode();
        OtpVerification otp = new OtpVerification();
        otp.setUser(user);
        otp.setEmail(user.getEmail());
        otp.setOtpHash(passwordEncoder.encode(otpCode));
        otp.setPurpose(OtpVerification.EMAIL_VERIFY_PURPOSE);
        otp.setExpiresAt(now.plus(authProperties.getEmailVerificationOtpTtl()));
        otp.setAttempts(0);
        otp.setMaxAttempts(authProperties.getEmailVerificationOtpMaxAttempts());
        otpVerificationRepository.save(otp);

        logDebugToken("email-verification-otp", user.getEmail(), otpCode, otp.getExpiresAt());
        emailService.sendVerificationOtp(
                user.getEmail(),
                user.getFullName(),
                otpCode
        );
    }

    private void registerFailedLogin(UserAccount user, Instant now) {
        int failures = Optional.ofNullable(user.getFailedLoginAttempts()).orElse(0) + 1;
        user.setFailedLoginAttempts(failures);
        if (failures >= authProperties.getLoginMaxFailures()) {
            user.setLockedUntil(now.plus(authProperties.getLoginLockDuration()));
            user.setFailedLoginAttempts(0);
        }
        userRepository.save(user);
    }

    private void recordLogin(
            UserAccount user,
            String email,
            String ipAddress,
            String userAgent,
            String method,
            String status
    ) {
        LoginHistory history = new LoginHistory();
        history.setUser(user);
        history.setEmail(email);
        history.setIpAddress(ipAddress);
        history.setUserAgent(userAgent);
        history.setLoginMethod(method);
        history.setStatus(status);
        loginHistoryRepository.save(history);
    }

    private UserAccount getAuthenticatedUser() {
        CurrentUser currentUser = authenticatedUserResolver.resolve()
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED));

        if (currentUser.id() != null) {
            return userRepository.findByIdAndDeletedAtIsNull(currentUser.id())
                    .orElseThrow(() -> new BusinessException(
                            ErrorCode.RESOURCE_NOT_FOUND,
                            "Authenticated user was not found"
                    ));
        }

        if (currentUser.authUserId() != null) {
            Optional<UserAccount> userByAuthUserId = userRepository.findByAuthUserIdAndDeletedAtIsNull(currentUser.authUserId());
            if (userByAuthUserId.isPresent()) {
                return userByAuthUserId.get();
            }
        }

        if (currentUser.email() != null && !currentUser.email().isBlank()) {
            return userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(currentUser.email())
                    .orElseThrow(() -> new BusinessException(
                            ErrorCode.RESOURCE_NOT_FOUND,
                            "Authenticated user was not found"
                    ));
        }

        throw new BusinessException(ErrorCode.UNAUTHENTICATED, "Authenticated user identity is missing required claims");
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

    private String generateOtpCode() {
        return "%06d".formatted(secureRandom.nextInt(1_000_000));
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
