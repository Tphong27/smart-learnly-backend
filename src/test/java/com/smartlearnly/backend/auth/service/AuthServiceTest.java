package com.smartlearnly.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.auth.config.AuthProperties;
import com.smartlearnly.backend.auth.dto.ChangePasswordRequest;
import com.smartlearnly.backend.auth.dto.ForgotPasswordRequest;
import com.smartlearnly.backend.auth.dto.GoogleLoginRequest;
import com.smartlearnly.backend.auth.dto.LoginRequest;
import com.smartlearnly.backend.auth.dto.RegisterRequest;
import com.smartlearnly.backend.auth.dto.ResendVerificationRequest;
import com.smartlearnly.backend.auth.dto.ResetPasswordRequest;
import com.smartlearnly.backend.auth.dto.UpdateProfileRequest;
import com.smartlearnly.backend.auth.dto.VerifyEmailRequest;
import com.smartlearnly.backend.auth.entity.OtpVerification;
import com.smartlearnly.backend.auth.entity.PasswordResetToken;
import com.smartlearnly.backend.auth.repository.LoginHistoryRepository;
import com.smartlearnly.backend.auth.repository.OtpVerificationRepository;
import com.smartlearnly.backend.auth.repository.PasswordResetTokenRepository;
import com.smartlearnly.backend.common.audit.AuditAction;
import com.smartlearnly.backend.common.audit.AuditLogService;
import com.smartlearnly.backend.common.audit.AuditResult;
import com.smartlearnly.backend.common.config.SecurityProperties;
import com.smartlearnly.backend.common.security.AuthenticatedUserResolver;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.SecurityContextAuthenticatedUserResolver;
import com.smartlearnly.backend.user.entity.UserAccount;
import com.smartlearnly.backend.user.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock
    private UserRepository userRepository;
    @Mock
    private OtpVerificationRepository otpVerificationRepository;
    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private AuthSessionService authSessionService;
    @Mock
    private EmailService emailService;
    @Mock
    private LoginHistoryRepository loginHistoryRepository;
    @Mock
    private GoogleIdTokenService googleIdTokenService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();

        AuthProperties properties = new AuthProperties();
        properties.setDebugLogTokens(false);
        properties.setEmailVerificationOtpTtl(Duration.ofMinutes(15));
        properties.setEmailVerificationOtpMaxAttempts(5);
        properties.setPasswordResetTokenTtl(Duration.ofMinutes(30));
        properties.setLoginMaxFailures(5);
        properties.setLoginLockDuration(Duration.ofMinutes(15));
        AuthenticatedUserResolver authenticatedUserResolver =
                new SecurityContextAuthenticatedUserResolver(new SecurityProperties());

        authService = new AuthService(
                userRepository,
                otpVerificationRepository,
                passwordResetTokenRepository,
                passwordEncoder,
                auditLogService,
                properties,
                authenticatedUserResolver,
                authSessionService,
                emailService,
                loginHistoryRepository,
                googleIdTokenService
        );
    }

    @Test
    void forgotPasswordShouldCreateResetTokenForExistingUser() {
        UserAccount user = createUser();
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("student@example.com"))
                .thenReturn(Optional.of(user));

        authService.forgotPassword(new ForgotPasswordRequest("student@example.com"));

        verify(passwordResetTokenRepository).markAllUnusedAsUsed(eq(user.getId()), any(Instant.class));
        ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(passwordResetTokenRepository).save(tokenCaptor.capture());

        PasswordResetToken savedToken = tokenCaptor.getValue();
        assertThat(savedToken.getUser()).isEqualTo(user);
        assertThat(savedToken.getTokenHash()).isNotBlank();
        assertThat(savedToken.getExpiresAt()).isAfter(Instant.now());
    }

    @Test
    void registerShouldCreatePendingTraineeAndSendVerificationOtp() {
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("new@example.com"))
                .thenReturn(Optional.empty());
        when(passwordEncoder.encode("Secure@123")).thenReturn("encoded-password");
        when(passwordEncoder.encode(org.mockito.ArgumentMatchers.matches("\\d{6}"))).thenReturn("encoded-otp");
        when(userRepository.save(any(UserAccount.class))).thenAnswer(invocation -> {
            UserAccount user = invocation.getArgument(0);
            user.setId(UUID.randomUUID());
            return user;
        });

        authService.register(new RegisterRequest("New User", "NEW@example.com", "Secure@123", "Secure@123"));

        ArgumentCaptor<UserAccount> userCaptor = ArgumentCaptor.forClass(UserAccount.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getEmail()).isEqualTo("new@example.com");
        assertThat(userCaptor.getValue().getRole()).isEqualTo("TRAINEE");
        assertThat(userCaptor.getValue().getStatus()).isEqualTo("pending_verify");
        ArgumentCaptor<OtpVerification> otpCaptor = ArgumentCaptor.forClass(OtpVerification.class);
        verify(otpVerificationRepository).save(otpCaptor.capture());
        assertThat(otpCaptor.getValue().getOtpHash()).isEqualTo("encoded-otp");
        assertThat(otpCaptor.getValue().getPurpose()).isEqualTo(OtpVerification.EMAIL_VERIFY_PURPOSE);
        verify(emailService).sendVerificationOtp(eq("new@example.com"), eq("New User"), org.mockito.ArgumentMatchers.matches("\\d{6}"));
    }

    @Test
    void googleLoginShouldLinkExistingUserByEmail() {
        UserAccount user = createUser();
        user.setStatus("active");
        user.setEmailVerifiedAt(Instant.now());
        GoogleIdTokenService.GoogleIdentity identity = new GoogleIdTokenService.GoogleIdentity(
                "google-subject",
                "student@example.com",
                "Student",
                "https://example.com/avatar.png"
        );
        when(googleIdTokenService.verify("google-id-token")).thenReturn(identity);
        when(userRepository.findByGoogleIdAndDeletedAtIsNull("google-subject")).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("student@example.com")).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        authService.loginWithGoogle(new GoogleLoginRequest("google-id-token"), "browser", "127.0.0.1");

        assertThat(user.getGoogleId()).isEqualTo("google-subject");
        assertThat(user.getAvatarUrl()).isEqualTo("https://example.com/avatar.png");
        verify(authSessionService).issue(user, "browser", "127.0.0.1");
    }

    @Test
    void loginShouldRejectPendingVerificationUser() {
        UserAccount user = createUser();
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("student@example.com"))
                .thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(
                new LoginRequest("student@example.com", "Secure@123"),
                "browser",
                "127.0.0.1"
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EMAIL_NOT_VERIFIED);
    }

    @Test
    void loginShouldIssueSessionAndClearPreviousFailures() {
        UserAccount user = createUser();
        user.setStatus("active");
        user.setEmailVerifiedAt(Instant.now());
        user.setPasswordHash("encoded-password");
        user.setFailedLoginAttempts(2);
        user.setLockedUntil(Instant.now().minusSeconds(1));
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("student@example.com"))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Secure@123", "encoded-password")).thenReturn(true);

        authService.login(
                new LoginRequest("student@example.com", "Secure@123"),
                "browser",
                "127.0.0.1"
        );

        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getLockedUntil()).isNull();
        assertThat(user.getLastLoginAt()).isNotNull();
        verify(authSessionService).issue(user, "browser", "127.0.0.1");
        verify(auditLogService).recordAuthentication(
                user, "student@example.com", AuditAction.LOGIN_SUCCEEDED, AuditResult.SUCCESS,
                "Login succeeded", "127.0.0.1", "browser", null
        );
    }

    @Test
    void loginShouldLockAccountAfterFiveInvalidPasswords() {
        UserAccount user = createUser();
        user.setStatus("active");
        user.setEmailVerifiedAt(Instant.now());
        user.setPasswordHash("encoded-password");
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("student@example.com"))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Wrong@123", "encoded-password")).thenReturn(false);

        for (int attempt = 0; attempt < 5; attempt++) {
            assertThatThrownBy(() -> authService.login(
                    new LoginRequest("student@example.com", "Wrong@123"),
                    "browser",
                    "127.0.0.1"
            )).isInstanceOf(BusinessException.class);
        }

        assertThat(user.getLockedUntil()).isAfter(Instant.now());
        assertThat(user.getFailedLoginAttempts()).isZero();
        verify(auditLogService).recordAuthentication(
                user, "student@example.com", AuditAction.LOGIN_BLOCKED, AuditResult.DENIED,
                "Login was blocked", "127.0.0.1", "browser", ErrorCode.ACCOUNT_LOCKED.name()
        );
    }

    @Test
    void logoutShouldAuditResolvedRefreshTokenOwnerWithoutLoggingToken() {
        UserAccount user = createUser();
        when(authSessionService.revoke("raw-refresh-token")).thenReturn(user);

        authService.logout("raw-refresh-token");

        verify(auditLogService).recordAuthentication(
                user, user.getEmail(), AuditAction.LOGOUT_SUCCEEDED, AuditResult.SUCCESS,
                "Logout succeeded", null, null, null
        );
    }
    @Test
    void forgotPasswordShouldNotCreateTokenForUnknownEmail() {
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("missing@example.com"))
                .thenReturn(Optional.empty());

        authService.forgotPassword(new ForgotPasswordRequest("missing@example.com"));

        verify(passwordResetTokenRepository, never()).save(any());
    }

    @Test
    void verifyEmailShouldActivatePendingUser() {
        UserAccount user = createUser();
        user.setStatus("pending_verify");
        user.setFailedLoginAttempts(0);
        user.setEmailVerifiedAt(null);

        OtpVerification otp = createEmailVerificationOtp(user);
        when(otpVerificationRepository.findTopByEmailIgnoreCaseAndPurposeAndVerifiedAtIsNullOrderByCreatedAtDesc(
                "student@example.com",
                OtpVerification.EMAIL_VERIFY_PURPOSE
        )).thenReturn(Optional.of(otp));
        when(passwordEncoder.matches("123456", "encoded-otp")).thenReturn(true);

        authService.verifyEmail(new VerifyEmailRequest("student@example.com", "123456"));

        assertThat(user.getStatus()).isEqualTo("active");
        assertThat(user.getEmailVerifiedAt()).isNotNull();
        assertThat(otp.getVerifiedAt()).isNotNull();
        verify(userRepository).save(user);
        verify(otpVerificationRepository).save(otp);
    }

    @Test
    void verifyEmailShouldCountInvalidOtpAttempt() {
        UserAccount user = createUser();
        OtpVerification otp = createEmailVerificationOtp(user);
        when(otpVerificationRepository.findTopByEmailIgnoreCaseAndPurposeAndVerifiedAtIsNullOrderByCreatedAtDesc(
                "student@example.com",
                OtpVerification.EMAIL_VERIFY_PURPOSE
        )).thenReturn(Optional.of(otp));
        when(passwordEncoder.matches("654321", "encoded-otp")).thenReturn(false);

        assertThatThrownBy(() -> authService.verifyEmail(new VerifyEmailRequest("student@example.com", "654321")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Email verification OTP is invalid or expired");

        assertThat(otp.getAttempts()).isEqualTo(1);
        verify(otpVerificationRepository).save(otp);
    }

    @Test
    void resendVerificationShouldRejectFourthRequestWithinWindow() {
        UserAccount user = createUser();
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("student@example.com"))
                .thenReturn(Optional.of(user));
        when(otpVerificationRepository.countByEmailIgnoreCaseAndPurposeAndCreatedAtAfter(
                eq("student@example.com"),
                eq(OtpVerification.EMAIL_VERIFY_PURPOSE),
                any(Instant.class)
        )).thenReturn(3L);

        assertThatThrownBy(() -> authService.resendVerification(new ResendVerificationRequest("student@example.com")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RATE_LIMIT_EXCEEDED);

        verify(otpVerificationRepository, never()).save(any());
    }

    @Test
    void resetPasswordShouldRejectExpiredToken() {
        PasswordResetToken token = new PasswordResetToken();
        token.setUser(createUser());
        token.setTokenHash(hash("expired-token"));
        token.setExpiresAt(Instant.now().minusSeconds(10));

        when(passwordResetTokenRepository.findByTokenHash(hash("expired-token")))
                .thenReturn(Optional.of(token));

        assertThatThrownBy(() -> authService.resetPassword(
                new ResetPasswordRequest("expired-token", "NewPass1!", "NewPass1!")
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Password reset token is invalid or expired");
    }

    @Test
    void updateProfileShouldPersistProvidedFieldsOnly() {
        UserAccount user = createUser();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "student@example.com",
                        "N/A",
                        AuthorityUtils.NO_AUTHORITIES
                )
        );
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("student@example.com"))
                .thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        authService.updateCurrentUserProfile(new UpdateProfileRequest("Updated Name", null, "+84987654321", "New bio"));

        assertThat(user.getFullName()).isEqualTo("Updated Name");
        assertThat(user.getPhoneNumber()).isEqualTo("+84987654321");
        assertThat(user.getBio()).isEqualTo("New bio");
        verify(userRepository).save(user);
    }

    @Test
    void changePasswordShouldRejectWrongCurrentPassword() {
        UserAccount user = createUser();
        user.setPasswordHash("encoded-password");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "student@example.com",
                        "N/A",
                        AuthorityUtils.NO_AUTHORITIES
                )
        );
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("student@example.com"))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("WrongPass1!", "encoded-password")).thenReturn(false);

        assertThatThrownBy(() -> authService.changeCurrentUserPassword(
                new ChangePasswordRequest("WrongPass1!", "NewPass1!", "NewPass1!")
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
    }

    private UserAccount createUser() {
        UserAccount user = new UserAccount();
        user.setId(UUID.randomUUID());
        user.setEmail("student@example.com");
        user.setFullName("Student");
        user.setRole("TRAINEE");
        user.setStatus("pending_verify");
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        return user;
    }

    private OtpVerification createEmailVerificationOtp(UserAccount user) {
        OtpVerification otp = new OtpVerification();
        otp.setUser(user);
        otp.setEmail(user.getEmail());
        otp.setOtpHash("encoded-otp");
        otp.setPurpose(OtpVerification.EMAIL_VERIFY_PURPOSE);
        otp.setAttempts(0);
        otp.setMaxAttempts(5);
        otp.setExpiresAt(Instant.now().plusSeconds(300));
        return otp;
    }

    private String hash(String rawToken) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(digest.digest(rawToken.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        }
        catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
