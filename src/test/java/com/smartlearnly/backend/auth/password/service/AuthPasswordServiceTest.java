package com.smartlearnly.backend.auth.password.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.auth.config.AuthProperties;
import com.smartlearnly.backend.auth.password.dto.ChangePasswordRequest;
import com.smartlearnly.backend.auth.password.dto.ForgotPasswordRequest;
import com.smartlearnly.backend.auth.password.dto.ResetPasswordRequest;
import com.smartlearnly.backend.auth.password.entity.PasswordResetToken;
import com.smartlearnly.backend.auth.password.repository.PasswordResetTokenRepository;
import com.smartlearnly.backend.auth.service.EmailService;
import com.smartlearnly.backend.auth.session.service.AuthSessionService;
import com.smartlearnly.backend.common.audit.AuditLogService;
import com.smartlearnly.backend.common.config.SecurityProperties;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.common.security.SecurityContextAuthenticatedUserResolver;
import com.smartlearnly.backend.user.entity.UserAccount;
import com.smartlearnly.backend.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthPasswordServiceTest {
    @Mock
    private UserRepository userRepository;
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

    private AuthPasswordService passwordService;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        CurrentUserService currentUserService = new CurrentUserService(
                new SecurityContextAuthenticatedUserResolver(new SecurityProperties()),
                userRepository);
        passwordService = new AuthPasswordService(
                userRepository,
                passwordResetTokenRepository,
                passwordEncoder,
                auditLogService,
                new AuthProperties(),
                currentUserService,
                authSessionService,
                emailService);
    }

    @Test
    void forgotPasswordShouldCreateResetTokenForExistingUser() {
        UserAccount user = createUser();
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("student@example.com"))
                .thenReturn(Optional.of(user));

        passwordService.forgotPassword(new ForgotPasswordRequest("student@example.com"));

        verify(passwordResetTokenRepository).markAllUnusedAsUsed(eq(user.getId()), any(Instant.class));
        ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(passwordResetTokenRepository).save(tokenCaptor.capture());
        assertThat(tokenCaptor.getValue().getUser()).isEqualTo(user);
        assertThat(tokenCaptor.getValue().getTokenHash()).isNotBlank();
        assertThat(tokenCaptor.getValue().getExpiresAt()).isAfter(Instant.now());
    }

    @Test
    void forgotPasswordShouldNotCreateTokenForUnknownEmail() {
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("missing@example.com"))
                .thenReturn(Optional.empty());

        passwordService.forgotPassword(new ForgotPasswordRequest("missing@example.com"));

        verify(passwordResetTokenRepository, never()).save(any());
    }

    @Test
    void resetPasswordShouldRejectExpiredToken() {
        PasswordResetToken token = new PasswordResetToken();
        token.setUser(createUser());
        token.setTokenHash(hash("expired-token"));
        token.setExpiresAt(Instant.now().minusSeconds(10));
        when(passwordResetTokenRepository.findByTokenHash(hash("expired-token"))).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> passwordService.resetPassword(
                new ResetPasswordRequest("expired-token", "NewPass1!", "NewPass1!")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Password reset token is invalid or expired");
    }

    @Test
    void changePasswordShouldRejectWrongCurrentPassword() {
        UserAccount user = createUser();
        user.setPasswordHash("encoded-password");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "student@example.com", "N/A", AuthorityUtils.NO_AUTHORITIES));
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("student@example.com"))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("WrongPass1!", "encoded-password")).thenReturn(false);

        assertThatThrownBy(() -> passwordService.changeCurrentUserPassword(
                new ChangePasswordRequest("WrongPass1!", "NewPass1!", "NewPass1!")))
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

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
