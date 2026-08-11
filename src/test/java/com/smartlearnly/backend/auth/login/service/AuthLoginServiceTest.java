package com.smartlearnly.backend.auth.login.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.auth.config.AuthProperties;
import com.smartlearnly.backend.auth.entity.LoginHistory;
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
        properties.setLoginLockDuration(Duration.ofMinutes(30));
        loginService = new AuthLoginService(
                userRepository,
                passwordEncoder,
                auditLogService,
                properties,
                authSessionService,
                loginHistoryRepository);
    }

    /**
     * Kịch bản: đăng nhập bằng email không tồn tại.
     * Given: email có khoảng trắng/chữ hoa nhưng không ánh xạ đến user chưa bị xóa.
     * When: login được gọi với mật khẩu bất kỳ.
     * Then: trả INVALID_CREDENTIALS, lưu lịch sử failed không gắn user và ghi security audit thất bại.
     * Ý nghĩa bảo mật: dùng lỗi chung thay vì tiết lộ rằng email chưa đăng ký, đồng thời vẫn giữ bằng chứng giám sát.
     */
    @Test
    void loginShouldRejectUnknownEmailAndRecordFailedAttempt() {
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("missing@example.com"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> loginService.login(
                new LoginRequest(" MISSING@example.com ", "Wrong@123"), "browser", "127.0.0.1"))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS));

        ArgumentCaptor<LoginHistory> historyCaptor = ArgumentCaptor.forClass(LoginHistory.class);
        verify(loginHistoryRepository).save(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getUser()).isNull();
        assertThat(historyCaptor.getValue().getEmail()).isEqualTo("missing@example.com");
        assertThat(historyCaptor.getValue().getLoginMethod()).isEqualTo("email");
        assertThat(historyCaptor.getValue().getStatus()).isEqualTo("failed");
        verify(auditLogService).recordAuthentication(
                null, "missing@example.com", AuditAction.LOGIN_FAILED, AuditResult.FAILURE,
                "Login failed", "127.0.0.1", "browser", ErrorCode.INVALID_CREDENTIALS.name());
        verifyNoInteractions(passwordEncoder, authSessionService);
    }

    /**
     * Kịch bản: tài khoản vẫn nằm trong thời gian khóa tạm.
     * Given: lockedUntil ở tương lai dù client có thể gửi đúng mật khẩu.
     * When: login bắt đầu kiểm tra trạng thái tài khoản.
     * Then: trả ACCOUNT_LOCKED trước khi so khớp mật khẩu, lưu lịch sử blocked và ghi audit denied.
     * Ý nghĩa bảo mật: khóa tạm phải có hiệu lực tuyệt đối, không thể được bỏ qua bằng credential đúng.
     */
    @Test
    void loginShouldRejectCurrentlyLockedAccountBeforePasswordCheck() {
        UserAccount user = activeUser();
        user.setLockedUntil(Instant.now().plus(Duration.ofMinutes(10)));
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("student@example.com"))
                .thenReturn(Optional.of(user));

        assertThatThrownBy(() -> loginService.login(
                new LoginRequest("student@example.com", "Secure@123"), "browser", "127.0.0.1"))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.ACCOUNT_LOCKED))
                .hasMessageContaining("Account is locked until");

        ArgumentCaptor<LoginHistory> historyCaptor = ArgumentCaptor.forClass(LoginHistory.class);
        verify(loginHistoryRepository).save(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getStatus()).isEqualTo("blocked");
        verify(auditLogService).recordAuthentication(
                user, "student@example.com", AuditAction.LOGIN_BLOCKED, AuditResult.DENIED,
                "Login was blocked", "127.0.0.1", "browser", ErrorCode.ACCOUNT_LOCKED.name());
        verifyNoInteractions(passwordEncoder, authSessionService);
    }

    /**
     * Kịch bản: tài khoản mới đăng ký chưa xác thực email.
     * Given: status là pending_verify và emailVerifiedAt chưa được gán.
     * When: user thử đăng nhập bằng email/mật khẩu.
     * Then: trả EMAIL_NOT_VERIFIED, ghi audit denied và không kiểm tra mật khẩu hoặc phát session.
     * Ý nghĩa bảo mật: tài khoản chưa chứng minh quyền sở hữu email không được nhận access/refresh token.
     */
    @Test
    void loginShouldRejectPendingVerificationUser() {
        UserAccount user = createUser();
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("student@example.com"))
                .thenReturn(Optional.of(user));

        assertThatThrownBy(() -> loginService.login(
                new LoginRequest("student@example.com", "Secure@123"), "browser", "127.0.0.1"))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.EMAIL_NOT_VERIFIED));

        verify(auditLogService).recordAuthentication(
                user, "student@example.com", AuditAction.LOGIN_FAILED, AuditResult.DENIED,
                "Login was denied", "127.0.0.1", "browser", ErrorCode.EMAIL_NOT_VERIFIED.name());
        verifyNoInteractions(passwordEncoder, authSessionService, loginHistoryRepository);
    }

    /**
     * Kịch bản: tài khoản đã bị vô hiệu hóa bởi nghiệp vụ/quản trị.
     * Given: status là inactive, khác pending_verify.
     * When: user gửi thông tin đăng nhập.
     * Then: trả ACCOUNT_INACTIVE và không thực hiện password authentication.
     * Ý nghĩa bảo mật: trạng thái vô hiệu phải chặn cả tài khoản từng xác thực email thành công.
     */
    @Test
    void loginShouldRejectInactiveUser() {
        UserAccount user = activeUser();
        user.setStatus("inactive");
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("student@example.com"))
                .thenReturn(Optional.of(user));

        assertThatThrownBy(() -> loginService.login(
                new LoginRequest("student@example.com", "Secure@123"), "browser", "127.0.0.1"))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.ACCOUNT_INACTIVE));

        verify(auditLogService).recordAuthentication(
                user, "student@example.com", AuditAction.LOGIN_FAILED, AuditResult.DENIED,
                "Login was denied", "127.0.0.1", "browser", ErrorCode.ACCOUNT_INACTIVE.name());
        verifyNoInteractions(passwordEncoder, authSessionService, loginHistoryRepository);
    }

    /**
     * Kịch bản: tài khoản chỉ liên kết Google nên không có passwordHash cục bộ.
     * Given: user active nhưng passwordHash bằng null.
     * When: thử đăng nhập bằng email/password.
     * Then: xử lý giống mật khẩu sai, tăng failure counter và không phát session.
     * Ý nghĩa bảo mật: không cho phép bỏ qua password authentication trên tài khoản Google-only.
     */
    @Test
    void loginShouldRejectPasswordAuthenticationForGoogleOnlyAccount() {
        UserAccount user = activeUser();
        user.setPasswordHash(null);
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("student@example.com"))
                .thenReturn(Optional.of(user));

        assertThatThrownBy(() -> loginService.login(
                new LoginRequest("student@example.com", "AnyPassword1!"), "browser", "127.0.0.1"))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS));

        assertThat(user.getFailedLoginAttempts()).isEqualTo(1);
        verify(userRepository).save(user);
        verify(passwordEncoder, never()).matches(any(), any());
        verifyNoInteractions(authSessionService);
    }

    /**
     * Kịch bản: mật khẩu sai nhưng chưa đạt ngưỡng khóa.
     * Given: user active có một lần sai trước đó và encoder trả false.
     * When: tiếp tục nhập sai.
     * Then: counter tăng lên hai, tài khoản chưa bị khóa, lịch sử/audit đều mang trạng thái failed.
     * Ý nghĩa bảo mật: hệ thống theo dõi liên tục các lần thử sai mà chưa khóa sớm hơn cấu hình.
     */
    @Test
    void loginShouldIncrementFailuresWithoutLockingBeforeThreshold() {
        UserAccount user = activeUser();
        user.setFailedLoginAttempts(1);
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("student@example.com"))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Wrong@123", "encoded-password")).thenReturn(false);

        assertThatThrownBy(() -> loginService.login(
                new LoginRequest("student@example.com", "Wrong@123"), "browser", "127.0.0.1"))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS));

        assertThat(user.getFailedLoginAttempts()).isEqualTo(2);
        assertThat(user.getLockedUntil()).isNull();
        ArgumentCaptor<LoginHistory> historyCaptor = ArgumentCaptor.forClass(LoginHistory.class);
        verify(loginHistoryRepository).save(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getStatus()).isEqualTo("failed");
        verify(auditLogService).recordAuthentication(
                user, "student@example.com", AuditAction.LOGIN_FAILED, AuditResult.FAILURE,
                "Login failed", "127.0.0.1", "browser", ErrorCode.INVALID_CREDENTIALS.name());
    }

    /**
     * Kịch bản: lần nhập sai hiện tại chạm đúng ngưỡng năm lần.
     * Given: user đã có bốn failure và encoder tiếp tục trả false.
     * When: login lần thứ năm thất bại.
     * Then: lockedUntil được đặt khoảng 30 phút, counter reset về 0, history/audit chuyển sang blocked
     * và client nhận ACCOUNT_LOCKED thay vì INVALID_CREDENTIALS.
     * Ý nghĩa bảo mật: ngưỡng khóa được áp dụng ngay tại lần thử gây chạm giới hạn.
     */
    @Test
    void loginShouldLockAccountOnFifthInvalidPassword() {
        UserAccount user = activeUser();
        user.setFailedLoginAttempts(4);
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("student@example.com"))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Wrong@123", "encoded-password")).thenReturn(false);

        Instant beforeAttempt = Instant.now();
        assertThatThrownBy(() -> loginService.login(
                new LoginRequest("student@example.com", "Wrong@123"), "browser", "127.0.0.1"))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.ACCOUNT_LOCKED));

        assertThat(user.getLockedUntil()).isAfter(beforeAttempt.plus(Duration.ofMinutes(29)));
        assertThat(user.getFailedLoginAttempts()).isZero();
        ArgumentCaptor<LoginHistory> historyCaptor = ArgumentCaptor.forClass(LoginHistory.class);
        verify(loginHistoryRepository).save(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getStatus()).isEqualTo("blocked");
        verify(auditLogService).recordAuthentication(
                user, "student@example.com", AuditAction.LOGIN_BLOCKED, AuditResult.DENIED,
                "Login was blocked", "127.0.0.1", "browser", ErrorCode.ACCOUNT_LOCKED.name());
        verifyNoInteractions(authSessionService);
    }

    /**
     * Kịch bản: đăng nhập thành công sau khi khóa cũ đã hết hạn.
     * Given: user active, mật khẩu đúng, còn failure counter và lockedUntil nằm trong quá khứ.
     * When: login được xác thực.
     * Then: xóa counter/lock, cập nhật lastLoginAt, lưu history success, audit success và phát session.
     * Ý nghĩa bảo mật: chỉ reset dấu vết lỗi sau một lần xác thực thực sự thành công.
     */
    @Test
    void loginShouldIssueSessionAndClearPreviousFailures() {
        UserAccount user = activeUser();
        user.setFailedLoginAttempts(2);
        user.setLockedUntil(Instant.now().minusSeconds(1));
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("student@example.com"))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Secure@123", "encoded-password")).thenReturn(true);

        Instant beforeLogin = Instant.now();
        loginService.login(new LoginRequest("student@example.com", "Secure@123"), "browser", "127.0.0.1");

        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getLockedUntil()).isNull();
        assertThat(user.getLastLoginAt()).isAfterOrEqualTo(beforeLogin);
        verify(userRepository).save(user);
        ArgumentCaptor<LoginHistory> historyCaptor = ArgumentCaptor.forClass(LoginHistory.class);
        verify(loginHistoryRepository).save(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getStatus()).isEqualTo("success");
        assertThat(historyCaptor.getValue().getLoginMethod()).isEqualTo("email");
        verify(authSessionService).issue(user, "browser", "127.0.0.1");
        verify(auditLogService).recordAuthentication(
                user, "student@example.com", AuditAction.LOGIN_SUCCEEDED, AuditResult.SUCCESS,
                "Login succeeded", "127.0.0.1", "browser", null);
    }

    private UserAccount activeUser() {
        UserAccount user = createUser();
        user.setStatus("active");
        user.setEmailVerifiedAt(Instant.now());
        user.setPasswordHash("encoded-password");
        return user;
    }

    private UserAccount createUser() {
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
