package com.smartlearnly.backend.auth.registration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.auth.config.AuthProperties;
import com.smartlearnly.backend.auth.registration.dto.RegisterRequest;
import com.smartlearnly.backend.auth.registration.dto.ResendVerificationRequest;
import com.smartlearnly.backend.auth.registration.dto.VerifyEmailRequest;
import com.smartlearnly.backend.auth.registration.entity.OtpVerification;
import com.smartlearnly.backend.auth.registration.repository.OtpVerificationRepository;
import com.smartlearnly.backend.auth.service.EmailService;
import com.smartlearnly.backend.common.audit.AuditLogService;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.user.entity.UserAccount;
import com.smartlearnly.backend.user.repository.UserRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthRegistrationServiceTest {
    @Mock
    private UserRepository userRepository;
    @Mock
    private OtpVerificationRepository otpVerificationRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private EmailService emailService;

    private AuthRegistrationService registrationService;

    @BeforeEach
    void setUp() {
        registrationService = new AuthRegistrationService(
                userRepository,
                otpVerificationRepository,
                passwordEncoder,
                auditLogService,
                new AuthProperties(),
                emailService);
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

        registrationService.register(
                new RegisterRequest("New User", "NEW@example.com", "Secure@123", "Secure@123"));

        ArgumentCaptor<UserAccount> userCaptor = ArgumentCaptor.forClass(UserAccount.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getEmail()).isEqualTo("new@example.com");
        assertThat(userCaptor.getValue().getRole()).isEqualTo("TRAINEE");
        assertThat(userCaptor.getValue().getStatus()).isEqualTo("pending_verify");
        ArgumentCaptor<OtpVerification> otpCaptor = ArgumentCaptor.forClass(OtpVerification.class);
        verify(otpVerificationRepository).save(otpCaptor.capture());
        assertThat(otpCaptor.getValue().getOtpHash()).isEqualTo("encoded-otp");
        verify(emailService).sendVerificationOtp(
                eq("new@example.com"), eq("New User"), org.mockito.ArgumentMatchers.matches("\\d{6}"));
    }

    @Test
    void verifyEmailShouldActivatePendingUser() {
        UserAccount user = createUser();
        OtpVerification otp = createEmailVerificationOtp(user);
        when(otpVerificationRepository.findTopByEmailIgnoreCaseAndPurposeAndVerifiedAtIsNullOrderByCreatedAtDesc(
                "student@example.com", OtpVerification.EMAIL_VERIFY_PURPOSE))
                .thenReturn(Optional.of(otp));
        when(passwordEncoder.matches("123456", "encoded-otp")).thenReturn(true);

        registrationService.verifyEmail(new VerifyEmailRequest("student@example.com", "123456"));

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
                "student@example.com", OtpVerification.EMAIL_VERIFY_PURPOSE))
                .thenReturn(Optional.of(otp));
        when(passwordEncoder.matches("654321", "encoded-otp")).thenReturn(false);

        assertThatThrownBy(() -> registrationService.verifyEmail(
                new VerifyEmailRequest("student@example.com", "654321")))
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
                eq("student@example.com"), eq(OtpVerification.EMAIL_VERIFY_PURPOSE), any(Instant.class)))
                .thenReturn(3L);

        assertThatThrownBy(() -> registrationService.resendVerification(
                new ResendVerificationRequest("student@example.com")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RATE_LIMIT_EXCEEDED);

        verify(otpVerificationRepository, never()).save(any());
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
}
