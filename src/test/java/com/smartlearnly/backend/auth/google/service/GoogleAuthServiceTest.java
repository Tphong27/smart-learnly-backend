package com.smartlearnly.backend.auth.google.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.auth.entity.LoginHistory;
import com.smartlearnly.backend.auth.google.dto.GoogleLoginRequest;
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
import org.mockito.ArgumentCaptor;
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

    /**
     * Kịch bản: Google subject đã liên kết trực tiếp với một tài khoản active.
     * Given: ID token được xác minh và findByGoogleId trả đúng user.
     * When: đăng nhập Google.
     * Then: không cần dò/link lại bằng email, lastLoginAt được cập nhật, history/audit success được ghi
     * và session được phát cùng metadata thiết bị/IP.
     * Ý nghĩa bảo mật: subject ổn định do Google ký là khóa liên kết ưu tiên thay vì email do client tự khai.
     */
    @Test
    void loginShouldUseExistingGoogleLinkAndIssueSession() {
        UserAccount user = activeUser();
        GoogleIdTokenService.GoogleIdentity identity = identity(
                "google-subject", " STUDENT@Example.com ", "Student", "https://example.com/new-avatar.png");
        when(googleIdTokenService.verify("google-id-token")).thenReturn(identity);
        when(userRepository.findByGoogleIdAndDeletedAtIsNull("google-subject")).thenReturn(Optional.of(user));

        Instant beforeLogin = Instant.now();
        googleAuthService.login(new GoogleLoginRequest("google-id-token"), "browser", "127.0.0.1");

        assertThat(user.getLastLoginAt()).isAfterOrEqualTo(beforeLogin);
        verify(userRepository, never()).findByEmailIgnoreCaseAndDeletedAtIsNull(any());
        verify(userRepository).save(user);
        ArgumentCaptor<LoginHistory> historyCaptor = ArgumentCaptor.forClass(LoginHistory.class);
        verify(loginHistoryRepository).save(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getUser()).isSameAs(user);
        assertThat(historyCaptor.getValue().getEmail()).isEqualTo("student@example.com");
        assertThat(historyCaptor.getValue().getLoginMethod()).isEqualTo("google");
        assertThat(historyCaptor.getValue().getStatus()).isEqualTo("success");
        verify(auditLogService).recordAuthentication(
                user, "student@example.com", AuditAction.GOOGLE_LOGIN_SUCCEEDED, AuditResult.SUCCESS,
                "Google login succeeded", "127.0.0.1", "browser", null);
        verify(authSessionService).issue(user, "browser", "127.0.0.1");
    }

    /**
     * Kịch bản: Google subject chưa liên kết nhưng email trùng tài khoản pending hiện có.
     * Given: không tìm thấy googleId, tìm thấy user theo email, user chưa xác thực và chưa có avatar.
     * When: đăng nhập Google bằng identity đã được Google xác minh.
     * Then: gắn googleId, đánh dấu email verified, chuyển active, nhận avatar, lưu user và phát session.
     * Ý nghĩa bảo mật: tránh tạo tài khoản trùng email và chỉ kích hoạt sau khi Google chứng minh email verified.
     */
    @Test
    void loginShouldLinkExistingPendingUserByEmailAndActivateIt() {
        UserAccount user = pendingUser();
        GoogleIdTokenService.GoogleIdentity identity = identity(
                "google-subject", "student@example.com", "Student", "https://example.com/avatar.png");
        when(googleIdTokenService.verify("google-id-token")).thenReturn(identity);
        when(userRepository.findByGoogleIdAndDeletedAtIsNull("google-subject")).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("student@example.com"))
                .thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        googleAuthService.login(new GoogleLoginRequest("google-id-token"), "browser", "127.0.0.1");

        assertThat(user.getGoogleId()).isEqualTo("google-subject");
        assertThat(user.getStatus()).isEqualTo("active");
        assertThat(user.getEmailVerifiedAt()).isNotNull();
        assertThat(user.getAvatarUrl()).isEqualTo("https://example.com/avatar.png");
        verify(userRepository, times(2)).save(user);
        verify(authSessionService).issue(user, "browser", "127.0.0.1");
    }

    /**
     * Kịch bản: liên kết Google vào tài khoản active đã có avatar do người dùng tự chọn.
     * Given: user cùng email đã verified và avatarUrl không null.
     * When: Google identity cung cấp một picture khác.
     * Then: chỉ gắn googleId, giữ nguyên avatar hiện tại và vẫn hoàn tất login.
     * Ý nghĩa sản phẩm: đăng nhập liên kết không được âm thầm ghi đè dữ liệu hồ sơ do user quản lý.
     */
    @Test
    void loginShouldPreserveExistingAvatarWhenLinkingByEmail() {
        UserAccount user = activeUser();
        user.setGoogleId(null);
        user.setAvatarUrl("https://example.com/custom-avatar.png");
        GoogleIdTokenService.GoogleIdentity identity = identity(
                "google-subject", "student@example.com", "Student", "https://google.example/avatar.png");
        when(googleIdTokenService.verify("google-id-token")).thenReturn(identity);
        when(userRepository.findByGoogleIdAndDeletedAtIsNull("google-subject")).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("student@example.com"))
                .thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        googleAuthService.login(new GoogleLoginRequest("google-id-token"), "browser", "127.0.0.1");

        assertThat(user.getGoogleId()).isEqualTo("google-subject");
        assertThat(user.getAvatarUrl()).isEqualTo("https://example.com/custom-avatar.png");
        verify(userRepository, times(2)).save(user);
    }

    /**
     * Kịch bản: người dùng Google hoàn toàn mới.
     * Given: không có user theo googleId hoặc email và identity có đầy đủ tên/avatar.
     * When: login Google lần đầu.
     * Then: tạo trainee active đã verified, failedLoginAttempts bằng 0, lưu identity rồi phát session.
     * Ý nghĩa bảo mật: chỉ identity đã qua GoogleIdTokenService mới có thể bootstrap tài khoản active.
     */
    @Test
    void loginShouldCreateVerifiedTraineeForNewGoogleIdentity() {
        GoogleIdTokenService.GoogleIdentity identity = identity(
                "new-google-subject", " NEW@Example.com ", "New Student", "https://example.com/avatar.png");
        when(googleIdTokenService.verify("google-id-token")).thenReturn(identity);
        when(userRepository.findByGoogleIdAndDeletedAtIsNull("new-google-subject")).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("new@example.com"))
                .thenReturn(Optional.empty());
        when(userRepository.save(any(UserAccount.class))).thenAnswer(invocation -> {
            UserAccount saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(UUID.randomUUID());
                saved.setCreatedAt(Instant.now());
                saved.setUpdatedAt(Instant.now());
            }
            return saved;
        });

        googleAuthService.login(new GoogleLoginRequest("google-id-token"), "browser", "127.0.0.1");

        ArgumentCaptor<UserAccount> userCaptor = ArgumentCaptor.forClass(UserAccount.class);
        verify(userRepository, times(2)).save(userCaptor.capture());
        UserAccount createdUser = userCaptor.getAllValues().get(0);
        assertThat(createdUser.getEmail()).isEqualTo("new@example.com");
        assertThat(createdUser.getGoogleId()).isEqualTo("new-google-subject");
        assertThat(createdUser.getFullName()).isEqualTo("New Student");
        assertThat(createdUser.getAvatarUrl()).isEqualTo("https://example.com/avatar.png");
        assertThat(createdUser.getRole()).isEqualTo("TRAINEE");
        assertThat(createdUser.getStatus()).isEqualTo("active");
        assertThat(createdUser.getEmailVerifiedAt()).isNotNull();
        assertThat(createdUser.getFailedLoginAttempts()).isZero();
        verify(authSessionService).issue(createdUser, "browser", "127.0.0.1");
    }

    /**
     * Kịch bản: Google identity mới không cung cấp tên hiển thị hữu ích.
     * Given: fullName chỉ chứa whitespace và không có user hiện hữu.
     * When: tài khoản Google mới được tạo.
     * Then: email đã chuẩn hóa được dùng làm fullName fallback để thỏa ràng buộc non-null.
     * Ý nghĩa ổn định: dữ liệu OAuth tùy chọn bị thiếu không làm hỏng luồng tạo user.
     */
    @Test
    void loginShouldUseNormalizedEmailAsNameWhenGoogleNameIsBlank() {
        GoogleIdTokenService.GoogleIdentity identity = identity(
                "new-google-subject", " NEW@Example.com ", "   ", null);
        when(googleIdTokenService.verify("google-id-token")).thenReturn(identity);
        when(userRepository.findByGoogleIdAndDeletedAtIsNull("new-google-subject")).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("new@example.com"))
                .thenReturn(Optional.empty());
        when(userRepository.save(any(UserAccount.class))).thenAnswer(invocation -> {
            UserAccount saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(UUID.randomUUID());
            }
            return saved;
        });

        googleAuthService.login(new GoogleLoginRequest("google-id-token"), "browser", "127.0.0.1");

        ArgumentCaptor<UserAccount> userCaptor = ArgumentCaptor.forClass(UserAccount.class);
        verify(userRepository, times(2)).save(userCaptor.capture());
        assertThat(userCaptor.getAllValues().get(0).getFullName()).isEqualTo("new@example.com");
    }

    /**
     * Kịch bản: Google subject hợp lệ nhưng tài khoản liên kết đã bị inactive.
     * Given: token Google xác minh thành công và repository trả user không active.
     * When: login tiếp tục kiểm tra trạng thái nội bộ.
     * Then: trả ACCOUNT_INACTIVE, không cập nhật lastLoginAt, không ghi history/audit success và không phát session.
     * Ý nghĩa bảo mật: OAuth bên ngoài không được phép vượt qua quyết định khóa/vô hiệu hóa nội bộ.
     */
    @Test
    void loginShouldRejectInactiveLinkedAccount() {
        UserAccount user = activeUser();
        user.setStatus("inactive");
        user.setLastLoginAt(null);
        GoogleIdTokenService.GoogleIdentity identity = identity(
                "google-subject", "student@example.com", "Student", null);
        when(googleIdTokenService.verify("google-id-token")).thenReturn(identity);
        when(userRepository.findByGoogleIdAndDeletedAtIsNull("google-subject")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> googleAuthService.login(
                new GoogleLoginRequest("google-id-token"), "browser", "127.0.0.1"))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.ACCOUNT_INACTIVE));

        assertThat(user.getLastLoginAt()).isNull();
        verify(userRepository, never()).save(any());
        verifyNoInteractions(authSessionService, loginHistoryRepository, auditLogService);
    }

    private GoogleIdTokenService.GoogleIdentity identity(
            String subject, String email, String fullName, String avatarUrl) {
        return new GoogleIdTokenService.GoogleIdentity(subject, email, fullName, avatarUrl);
    }

    private UserAccount activeUser() {
        UserAccount user = pendingUser();
        user.setGoogleId("google-subject");
        user.setStatus("active");
        user.setEmailVerifiedAt(Instant.now());
        return user;
    }

    private UserAccount pendingUser() {
        UserAccount user = new UserAccount();
        user.setId(UUID.randomUUID());
        user.setEmail("student@example.com");
        user.setFullName("Student");
        user.setRole("TRAINEE");
        user.setStatus("pending_verify");
        user.setFailedLoginAttempts(0);
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        return user;
    }
}
