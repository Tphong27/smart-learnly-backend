package com.smartlearnly.backend.auth.login.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.auth.config.AuthProperties;
import com.smartlearnly.backend.auth.login.dto.LoginRequest;
import com.smartlearnly.backend.auth.repository.LoginHistoryRepository;
import com.smartlearnly.backend.auth.session.service.AuthSessionService;
import com.smartlearnly.backend.common.audit.AuditAction;
import com.smartlearnly.backend.common.audit.AuditLogService;
import com.smartlearnly.backend.common.audit.AuditResult;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthLoginServiceTest {
    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private AuthSessionService authSessionService;
    @Mock
    private LoginHistoryRepository loginHistoryRepository;

    private AuthLoginService loginService;

    @BeforeEach
    void setUp() {
        AuthProperties properties = new AuthProperties();
        properties.setLoginMaxFailures(5);
        loginService = new AuthLoginService(
                userRepository,
                passwordEncoder,
                auditLogService,
                properties,
                authSessionService,
                loginHistoryRepository);
    }

    @Test
    void loginShouldRejectPendingVerificationUser() {
        UserAccount user = createUser();
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("student@example.com"))
                .thenReturn(Optional.of(user));

        assertThatThrownBy(() -> loginService.login(
                new LoginRequest("student@example.com", "Secure@123"),
                "browser",
                "127.0.0.1"))
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

        loginService.login(new LoginRequest("student@example.com", "Secure@123"), "browser", "127.0.0.1");

        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getLockedUntil()).isNull();
        assertThat(user.getLastLoginAt()).isNotNull();
        verify(authSessionService).issue(user, "browser", "127.0.0.1");
        verify(auditLogService).recordAuthentication(
                user, "student@example.com", AuditAction.LOGIN_SUCCEEDED, AuditResult.SUCCESS,
                "Login succeeded", "127.0.0.1", "browser", null);
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
            assertThatThrownBy(() -> loginService.login(
                    new LoginRequest("student@example.com", "Wrong@123"),
                    "browser",
                    "127.0.0.1"))
                    .isInstanceOf(BusinessException.class);
        }

        assertThat(user.getLockedUntil()).isAfter(Instant.now());
        assertThat(user.getFailedLoginAttempts()).isZero();
        verify(auditLogService).recordAuthentication(
                user, "student@example.com", AuditAction.LOGIN_BLOCKED, AuditResult.DENIED,
                "Login was blocked", "127.0.0.1", "browser", ErrorCode.ACCOUNT_LOCKED.name());
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
}
