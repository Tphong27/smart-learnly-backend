package com.smartlearnly.backend.auth.profile.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.auth.profile.dto.UpdateProfileRequest;
import com.smartlearnly.backend.common.audit.AuditLogService;
import com.smartlearnly.backend.common.config.SecurityProperties;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.common.security.SecurityContextAuthenticatedUserResolver;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class AuthProfileServiceTest {
    @Mock
    private UserRepository userRepository;
    @Mock
    private AuditLogService auditLogService;

    private AuthProfileService profileService;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        CurrentUserService currentUserService = new CurrentUserService(
                new SecurityContextAuthenticatedUserResolver(new SecurityProperties()),
                userRepository);
        profileService = new AuthProfileService(userRepository, currentUserService, auditLogService);
    }

    @Test
    void updateProfileShouldPersistProvidedFieldsOnly() {
        UserAccount user = createUser();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "student@example.com", "N/A", AuthorityUtils.NO_AUTHORITIES));
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("student@example.com"))
                .thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        profileService.updateCurrentUserProfile(
                new UpdateProfileRequest("Updated Name", null, "+84987654321", "New bio"));

        assertThat(user.getFullName()).isEqualTo("Updated Name");
        assertThat(user.getPhoneNumber()).isEqualTo("+84987654321");
        assertThat(user.getBio()).isEqualTo("New bio");
        verify(userRepository).save(user);
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
