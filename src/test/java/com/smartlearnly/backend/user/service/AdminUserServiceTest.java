package com.smartlearnly.backend.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.user.dto.AdminUserPageResponse;
import com.smartlearnly.backend.user.dto.AdminUserResponse;
import com.smartlearnly.backend.user.entity.UserAccount;
import com.smartlearnly.backend.user.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {
    @Mock
    private UserRepository userRepository;

    private AdminUserService service;

    @BeforeEach
    void setUp() {
        service = new AdminUserService(userRepository);
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
                "active"
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
