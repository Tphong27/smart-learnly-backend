package com.smartlearnly.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.user.entity.UserAccount;
import com.smartlearnly.backend.user.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@ExtendWith(MockitoExtension.class)
class DatabaseUserDetailsServiceTest {
    @Mock
    private UserRepository userRepository;

    private DatabaseUserDetailsService userDetailsService;

    @BeforeEach
    void setUp() {
        userDetailsService = new DatabaseUserDetailsService(userRepository);
    }

    /**
     * Kịch bản: Spring Security tải một tài khoản có mật khẩu cục bộ.
     * Given: repository tìm thấy user chưa bị xóa với email/hash/role TRAINEE.
     * When: loadUserByUsername được gọi.
     * Then: UserDetails giữ đúng username/password và chuyển role nghiệp vụ thành authority ROLE_TRAINEE.
     * Ý nghĩa bảo mật: authorization của Spring nhận authority do database quyết định, không phải từ request client.
     */
    @Test
    void loadUserShouldMapDatabaseRoleToSpringSecurityAuthority() {
        UserAccount user = user("encoded-password");
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("student@example.com"))
                .thenReturn(Optional.of(user));

        UserDetails details = userDetailsService.loadUserByUsername("student@example.com");

        assertThat(details.getUsername()).isEqualTo("student@example.com");
        assertThat(details.getPassword()).isEqualTo("encoded-password");
        assertThat(details.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_TRAINEE");
    }

    /**
     * Kịch bản: email không thuộc user chưa bị xóa.
     * Given: repository trả Optional.empty.
     * When: Spring Security yêu cầu tải principal.
     * Then: ném UsernameNotFoundException với thông báo chung.
     * Ý nghĩa bảo mật: authentication dừng lại mà không tạo principal giả hoặc authority mặc định.
     */
    @Test
    void loadUserShouldRejectUnknownEmail() {
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("missing@example.com"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("missing@example.com"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessage("User not found");
    }

    /**
     * Kịch bản: tài khoản tồn tại nhưng chỉ hỗ trợ đăng nhập Google, passwordHash rỗng.
     * Given: repository tìm thấy user với passwordHash chỉ chứa whitespace.
     * When: provider username/password cố tải user.
     * Then: ném UsernameNotFoundException chuyên biệt và không tạo UserDetails.
     * Ý nghĩa bảo mật: tài khoản OAuth-only không thể đăng nhập bằng một mật khẩu rỗng/không tồn tại.
     */
    @Test
    void loadUserShouldRejectAccountWithoutLocalPassword() {
        UserAccount user = user("   ");
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("student@example.com"))
                .thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("student@example.com"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessage("Password authentication is not available for this account");
    }

    private UserAccount user(String passwordHash) {
        UserAccount user = new UserAccount();
        user.setId(UUID.randomUUID());
        user.setEmail("student@example.com");
        user.setPasswordHash(passwordHash);
        user.setRole("TRAINEE");
        user.setStatus("active");
        return user;
    }
}
