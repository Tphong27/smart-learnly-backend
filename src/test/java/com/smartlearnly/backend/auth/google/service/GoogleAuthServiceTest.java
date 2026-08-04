package com.smartlearnly.backend.auth.google.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.auth.google.dto.GoogleLoginRequest;
import com.smartlearnly.backend.auth.repository.LoginHistoryRepository;
import com.smartlearnly.backend.auth.session.service.AuthSessionService;
import com.smartlearnly.backend.common.audit.AuditLogService;
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

@ExtendWith(MockitoExtension.class)
class GoogleAuthServiceTest {
    @Mock
    private GoogleIdTokenService googleIdTokenService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AuthSessionService authSessionService;
    @Mock
    private LoginHistoryRepository loginHistoryRepository;
    @Mock
    private AuditLogService auditLogService;

    private GoogleAuthService googleAuthService;

    @BeforeEach
    void setUp() {
        googleAuthService = new GoogleAuthService(
                googleIdTokenService,
                userRepository,
                authSessionService,
                loginHistoryRepository,
                auditLogService);
    }

    @Test
    void loginShouldLinkExistingUserByEmail() {
        UserAccount user = createUser();
        GoogleIdTokenService.GoogleIdentity identity = new GoogleIdTokenService.GoogleIdentity(
                "google-subject",
                "student@example.com",
                "Student",
                "https://example.com/avatar.png");
        when(googleIdTokenService.verify("google-id-token")).thenReturn(identity);
        when(userRepository.findByGoogleIdAndDeletedAtIsNull("google-subject")).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("student@example.com"))
                .thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        googleAuthService.login(new GoogleLoginRequest("google-id-token"), "browser", "127.0.0.1");

        assertThat(user.getGoogleId()).isEqualTo("google-subject");
        assertThat(user.getAvatarUrl()).isEqualTo("https://example.com/avatar.png");
        verify(authSessionService).issue(user, "browser", "127.0.0.1");
    }

    private UserAccount createUser() {
        UserAccount user = new UserAccount();
        user.setId(UUID.randomUUID());
        user.setEmail("student@example.com");
        user.setFullName("Student");
        user.setRole("TRAINEE");
        user.setStatus("active");
        user.setEmailVerifiedAt(Instant.now());
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        return user;
    }
}
