package com.smartlearnly.backend.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.user.dto.AdminUserPageResponse;
import com.smartlearnly.backend.user.dto.AdminUserResponse;
import com.smartlearnly.backend.user.dto.CreateAdminUserRequest;
import com.smartlearnly.backend.user.dto.UpdateAdminUserRequest;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.user.entity.UserAccount;
import com.smartlearnly.backend.user.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {
    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    private AdminUserService service;

    @BeforeEach
    void setUp() {
        service = new AdminUserService(userRepository, passwordEncoder);
    }

    @Test
    void createShouldGeneratePasswordAndPersistOnlyBasicCreateFields() {
        CreateAdminUserRequest request = new CreateAdminUserRequest(
                " New User ",
                " NEW.USER@EXAMPLE.COM ",
                " +84901234567 ",
                "trainee",
                "ACTIVE",
                true
        );
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("new.user@example.com"))
                .thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("encoded-temporary-password");
        when(userRepository.save(any(UserAccount.class))).thenAnswer(invocation -> {
            UserAccount saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        AdminUserResponse response = service.create(request);

        ArgumentCaptor<String> passwordCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<UserAccount> userCaptor = ArgumentCaptor.forClass(UserAccount.class);
        org.mockito.Mockito.verify(passwordEncoder).encode(passwordCaptor.capture());
        org.mockito.Mockito.verify(userRepository).save(userCaptor.capture());

        UserAccount saved = userCaptor.getValue();
        assertThat(passwordCaptor.getValue())
                .startsWith("Aa1!")
                .hasSize(40);
        assertThat(saved.getEmail()).isEqualTo("new.user@example.com");
        assertThat(saved.getFullName()).isEqualTo("New User");
        assertThat(saved.getPhoneNumber()).isEqualTo("+84901234567");
        assertThat(saved.getRole()).isEqualTo("TRAINEE");
        assertThat(saved.getStatus()).isEqualTo("active");
        assertThat(saved.getAvatarUrl()).isNull();
        assertThat(saved.getBio()).isNull();
        assertThat(saved.getEmailVerifiedAt()).isNotNull();
        assertThat(saved.getPasswordHash()).isEqualTo("encoded-temporary-password");
        assertThat(response.email()).isEqualTo("new.user@example.com");
    }

    @Test
    void listShouldNormalizeFiltersClampSizeAndMapUsers() {
        UserAccount trainer = user("trainer@example.com", "Trainer Name", "TRAINER", "active");
        when(userRepository.searchAdminUsers(anyString(), anyString(), anyString(), any(Pageable.class)))
                .thenAnswer(invocation -> new PageImpl<>(
                        List.of(trainer),
                        invocation.getArgument(3),
                        1
                ));

        AdminUserPageResponse response = service.list(
                " trainer ",
                " ACTIVE ",
                " Jane_%\\ ",
                -5,
                150
        );

        ArgumentCaptor<String> roleCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> statusCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keywordCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        org.mockito.Mockito.verify(userRepository).searchAdminUsers(
                roleCaptor.capture(),
                statusCaptor.capture(),
                keywordCaptor.capture(),
                pageableCaptor.capture()
        );

        assertThat(roleCaptor.getValue()).isEqualTo("TRAINER");
        assertThat(statusCaptor.getValue()).isEqualTo("active");
        assertThat(keywordCaptor.getValue()).isEqualTo("%Jane\\_\\%\\\\%");
        assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(100);
        assertThat(response.page()).isZero();
        assertThat(response.size()).isEqualTo(100);
        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.totalPages()).isEqualTo(1);
        assertThat(response.content()).containsExactly(new AdminUserResponse(
                trainer.getId(),
                "trainer@example.com",
                "Trainer Name",
                "https://example.com/avatar.png",
                "TRAINER",
                "active",
                null,
                false,
                null,
                null
        ));
    }

    @Test
    void listShouldDefaultBlankStatusAndNonPositiveSize() {
        UserAccount trainer = user("trainer@example.com", "Trainer Name", "TRAINER", "active");
        when(userRepository.searchAdminUsers(any(), anyString(), any(), any(Pageable.class)))
                .thenAnswer(invocation -> new PageImpl<>(
                        List.of(trainer),
                        invocation.getArgument(3),
                        1
                ));

        service.list(" ", " ", " ", 2, 0);

        ArgumentCaptor<String> roleCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> statusCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keywordCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        org.mockito.Mockito.verify(userRepository).searchAdminUsers(
                roleCaptor.capture(),
                statusCaptor.capture(),
                keywordCaptor.capture(),
                pageableCaptor.capture()
        );

        assertThat(roleCaptor.getValue()).isNull();
        assertThat(statusCaptor.getValue()).isEqualTo("active");
        assertThat(keywordCaptor.getValue()).isNull();
        assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(20);
    }

    @Test
    void getShouldReturnNonDeletedUser() {
        UserAccount trainer = user("trainer@example.com", "Trainer Name", "TRAINER", "active");
        when(userRepository.findByIdAndDeletedAtIsNull(trainer.getId())).thenReturn(Optional.of(trainer));

        AdminUserResponse response = service.get(trainer.getId());

        assertThat(response.id()).isEqualTo(trainer.getId());
        assertThat(response.email()).isEqualTo("trainer@example.com");
    }

    @Test
    void getShouldRejectMissingOrDeletedUser() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(userId))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void updateShouldNormalizeAndPersistEditableFields() {
        UserAccount trainer = user("trainer@example.com", "Trainer Name", "TRAINER", "active");
        trainer.setEmailVerifiedAt(Instant.now());
        when(userRepository.findByIdAndDeletedAtIsNull(trainer.getId())).thenReturn(Optional.of(trainer));
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("updated@example.com"))
                .thenReturn(Optional.empty());
        when(userRepository.save(trainer)).thenReturn(trainer);

        AdminUserResponse response = service.update(trainer.getId(), new UpdateAdminUserRequest(
                " Updated Trainer ",
                " UPDATED@EXAMPLE.COM ",
                " ",
                "sme",
                "INACTIVE",
                false
        ));

        assertThat(trainer.getFullName()).isEqualTo("Updated Trainer");
        assertThat(trainer.getEmail()).isEqualTo("updated@example.com");
        assertThat(trainer.getPhoneNumber()).isNull();
        assertThat(trainer.getRole()).isEqualTo("SME");
        assertThat(trainer.getStatus()).isEqualTo("inactive");
        assertThat(trainer.getEmailVerifiedAt()).isNull();
        assertThat(response.emailVerified()).isFalse();
    }

    @Test
    void updateShouldRejectEmailOwnedByAnotherActiveUser() {
        UserAccount trainer = user("trainer@example.com", "Trainer Name", "TRAINER", "active");
        UserAccount owner = user("owner@example.com", "Owner", "TRAINEE", "active");
        when(userRepository.findByIdAndDeletedAtIsNull(trainer.getId())).thenReturn(Optional.of(trainer));
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("owner@example.com"))
                .thenReturn(Optional.of(owner));

        assertThatThrownBy(() -> service.update(trainer.getId(), new UpdateAdminUserRequest(
                null,
                "owner@example.com",
                null,
                null,
                null,
                null
        )))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.CONFLICT);
    }

    private UserAccount user(String email, String fullName, String role, String status) {
        UserAccount user = new UserAccount();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        user.setFullName(fullName);
        user.setAvatarUrl("https://example.com/avatar.png");
        user.setRole(role);
        user.setStatus(status);
        return user;
    }
}
