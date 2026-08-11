package com.smartlearnly.backend.auth.password.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.user.entity.UserAccount;
import com.smartlearnly.backend.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
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
    private CurrentUserService currentUserService;
    @Mock
    private AuthSessionService authSessionService;
    @Mock
    private EmailService emailService;

    private AuthProperties authProperties;
    private AuthPasswordService passwordService;

    @BeforeEach
    void setUp() {
        authProperties = new AuthProperties();
        authProperties.setPasswordResetTokenTtl(Duration.ofMinutes(30));
        authProperties.setFrontendBaseUrl("https://app.smartlearnly.test");
        passwordService = new AuthPasswordService(
                userRepository,
                passwordResetTokenRepository,
                passwordEncoder,
                auditLogService,
                authProperties,
                currentUserService,
                authSessionService,
                emailService);
    }

    /**
     * Kịch bản: yêu cầu quên mật khẩu cho tài khoản tồn tại.
     * Given: email được nhập với khoảng trắng/chữ hoa và repository tìm thấy user tương ứng.
     * When: forgotPassword được gọi.
     * Then: mọi token cũ bị vô hiệu, token ngẫu nhiên mới chỉ được lưu dưới dạng SHA-256,
     * link chứa token rõ được gửi qua email, TTL 30 phút và audit request được ghi.
     * Ý nghĩa bảo mật: database bị lộ cũng không cung cấp token reset có thể sử dụng trực tiếp.
     */
    @Test
    void forgotPasswordShouldInvalidateOldTokensAndEmailOnlyTheRawNewToken() {
        UserAccount user = createUser();
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("student@example.com"))
                .thenReturn(Optional.of(user));

        Instant beforeRequest = Instant.now();
        passwordService.forgotPassword(new ForgotPasswordRequest(" STUDENT@example.com "));

        verify(passwordResetTokenRepository).markAllUnusedAsUsed(eq(user.getId()), any(Instant.class));
        ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(passwordResetTokenRepository).save(tokenCaptor.capture());
        PasswordResetToken savedToken = tokenCaptor.getValue();
        assertThat(savedToken.getUser()).isSameAs(user);
        assertThat(savedToken.getTokenHash()).hasSize(64);
        assertThat(savedToken.getExpiresAt()).isAfter(beforeRequest.plus(Duration.ofMinutes(29)));

        ArgumentCaptor<String> linkCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordResetLink(
                eq("student@example.com"), eq("Student"), linkCaptor.capture());
        String resetLink = linkCaptor.getValue();
        assertThat(resetLink).startsWith("https://app.smartlearnly.test/reset-password?token=");
        String rawToken = resetLink.substring(resetLink.indexOf("token=") + "token=".length());
        assertThat(rawToken).isNotBlank();
        assertThat(savedToken.getTokenHash()).isEqualTo(hash(rawToken));
        assertThat(savedToken.getTokenHash()).doesNotContain(rawToken);
        verify(auditLogService).record(
                "student@example.com", "PASSWORD_RESET_REQUESTED", "USER", user.getId().toString());
    }

    /**
     * Kịch bản: yêu cầu quên mật khẩu cho email không tồn tại.
     * Given: repository trả Optional.empty.
     * When: forgotPassword xử lý request.
     * Then: phương thức kết thúc bình thường, không tạo token, không gửi email và không audit user.
     * Ý nghĩa bảo mật: client nhận cùng response công khai như email tồn tại, chống email enumeration.
     */
    @Test
    void forgotPasswordShouldSilentlyIgnoreUnknownEmail() {
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("missing@example.com"))
                .thenReturn(Optional.empty());

        passwordService.forgotPassword(new ForgotPasswordRequest("missing@example.com"));

        verifyNoInteractions(passwordResetTokenRepository, passwordEncoder, emailService, auditLogService,
                currentUserService, authSessionService);
    }

    /**
     * Kịch bản: xác nhận mật khẩu mới không khớp trong luồng reset.
     * Given: request chứa newPassword và confirmPassword khác nhau.
     * When: resetPassword được gọi.
     * Then: trả INVALID_REQUEST trước khi hash/lookup token hoặc ghi dữ liệu.
     * Ý nghĩa bảo mật: token hợp lệ không bị consume bởi một request nhập nhầm mật khẩu xác nhận.
     */
    @Test
    void resetPasswordShouldRejectMismatchedConfirmationBeforeTokenLookup() {
        assertThatThrownBy(() -> passwordService.resetPassword(
                new ResetPasswordRequest("raw-token", "NewPass1!", "Different1!")))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST))
                .hasMessage("Password confirmation does not match");

        verifyNoInteractions(passwordResetTokenRepository, passwordEncoder, userRepository,
                authSessionService, auditLogService, emailService, currentUserService);
    }

    /**
     * Kịch bản: token reset không tồn tại trong database.
     * Given: repository không tìm thấy bản hash SHA-256 của token client gửi.
     * When: resetPassword kiểm tra token.
     * Then: trả INVALID_OR_EXPIRED_TOKEN và không thay đổi mật khẩu/session.
     * Ý nghĩa bảo mật: chỉ token do server phát và còn lưu mới có quyền thay đổi credential.
     */
    @Test
    void resetPasswordShouldRejectUnknownToken() {
        when(passwordResetTokenRepository.findByTokenHash(hash("unknown-token")))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> passwordService.resetPassword(
                new ResetPasswordRequest("unknown-token", "NewPass1!", "NewPass1!")))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_OR_EXPIRED_TOKEN))
                .hasMessage("Password reset token is invalid or expired");

        verifyNoInteractions(passwordEncoder, userRepository, authSessionService, auditLogService);
    }

    /**
     * Kịch bản: token reset đã hết hạn.
     * Given: expiresAt nằm trong quá khứ dù token chưa có usedAt.
     * When: resetPassword kiểm tra isUsable.
     * Then: trả lỗi token chung, không encode/lưu mật khẩu và không thu hồi session.
     * Ý nghĩa bảo mật: thời hạn reset được thực thi ở service, không phụ thuộc frontend.
     */
    @Test
    void resetPasswordShouldRejectExpiredToken() {
        PasswordResetToken token = resetToken("expired-token");
        token.setExpiresAt(Instant.now().minusSeconds(10));
        when(passwordResetTokenRepository.findByTokenHash(hash("expired-token"))).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> passwordService.resetPassword(
                new ResetPasswordRequest("expired-token", "NewPass1!", "NewPass1!")))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_OR_EXPIRED_TOKEN))
                .hasMessage("Password reset token is invalid or expired");

        verifyNoInteractions(passwordEncoder, userRepository, authSessionService, auditLogService);
        verify(passwordResetTokenRepository, never()).save(any());
    }

    /**
     * Kịch bản: token reset đã được sử dụng trước đó.
     * Given: usedAt khác null và expiresAt vẫn ở tương lai.
     * When: client thử dùng lại cùng token.
     * Then: trả INVALID_OR_EXPIRED_TOKEN và không thay đổi bất kỳ credential nào.
     * Ý nghĩa bảo mật: token đặt lại mật khẩu là credential dùng đúng một lần, chống replay attack.
     */
    @Test
    void resetPasswordShouldRejectAlreadyUsedToken() {
        PasswordResetToken token = resetToken("used-token");
        token.setUsedAt(Instant.now().minusSeconds(30));
        when(passwordResetTokenRepository.findByTokenHash(hash("used-token"))).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> passwordService.resetPassword(
                new ResetPasswordRequest("used-token", "NewPass1!", "NewPass1!")))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_OR_EXPIRED_TOKEN));

        verifyNoInteractions(passwordEncoder, userRepository, authSessionService, auditLogService);
        verify(passwordResetTokenRepository, never()).save(any());
    }

    /**
     * Kịch bản: đặt lại mật khẩu thành công bằng token còn hiệu lực.
     * Given: token chưa dùng/còn hạn và encoder tạo hash mới.
     * When: resetPassword được thực thi.
     * Then: user nhận password hash/thời điểm đổi mới, mọi session cũ bị thu hồi, token có usedAt,
     * cả user và token được lưu, cuối cùng audit PASSWORD_RESET_COMPLETED được ghi.
     * Ý nghĩa bảo mật: sau reset, refresh token bị đánh cắp trước đó không còn sử dụng được.
     */
    @Test
    void resetPasswordShouldChangePasswordConsumeTokenAndRevokeAllSessions() {
        PasswordResetToken token = resetToken("valid-token");
        UserAccount user = token.getUser();
        when(passwordResetTokenRepository.findByTokenHash(hash("valid-token"))).thenReturn(Optional.of(token));
        when(passwordEncoder.encode("NewPass1!")).thenReturn("new-password-hash");

        Instant beforeReset = Instant.now();
        passwordService.resetPassword(
                new ResetPasswordRequest("valid-token", "NewPass1!", "NewPass1!"));

        assertThat(user.getPasswordHash()).isEqualTo("new-password-hash");
        assertThat(user.getPasswordChangedAt()).isAfterOrEqualTo(beforeReset);
        assertThat(token.getUsedAt()).isAfterOrEqualTo(beforeReset);
        verify(userRepository).save(user);
        verify(authSessionService).revokeAll(user);
        verify(passwordResetTokenRepository).save(token);
        verify(auditLogService).record(
                "student@example.com", "PASSWORD_RESET_COMPLETED", "USER", user.getId().toString());
    }

    /**
     * Kịch bản: xác nhận mật khẩu mới không khớp trong luồng đổi mật khẩu khi đã đăng nhập.
     * Given: request sai confirmPassword.
     * When: changeCurrentUserPassword được gọi.
     * Then: trả INVALID_REQUEST trước khi đọc danh tính hiện tại hoặc kiểm tra password hash.
     * Ý nghĩa bảo mật: không chạm tài khoản/session khi payload chưa nhất quán.
     */
    @Test
    void changePasswordShouldRejectMismatchedConfirmationBeforeResolvingCurrentUser() {
        assertThatThrownBy(() -> passwordService.changeCurrentUserPassword(
                new ChangePasswordRequest("Current1!", "NewPass1!", "Different1!")))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));

        verifyNoInteractions(currentUserService, passwordEncoder, userRepository,
                authSessionService, auditLogService, passwordResetTokenRepository, emailService);
    }

    /**
     * Kịch bản: tài khoản Google-only không có passwordHash cục bộ.
     * Given: current user hợp lệ nhưng passwordHash null.
     * When: user gọi đổi mật khẩu bằng current password.
     * Then: trả BUSINESS_RULE_VIOLATION mà không gọi encoder hay cập nhật user.
     * Ý nghĩa bảo mật: không giả định credential cục bộ tồn tại cho tài khoản đăng nhập liên kết.
     */
    @Test
    void changePasswordShouldRejectAccountWithoutLocalPassword() {
        UserAccount user = createUser();
        user.setPasswordHash(null);
        when(currentUserService.requireAuthenticatedUser()).thenReturn(user);

        assertThatThrownBy(() -> passwordService.changeCurrentUserPassword(
                new ChangePasswordRequest("Current1!", "NewPass1!", "NewPass1!")))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.BUSINESS_RULE_VIOLATION))
                .hasMessage("Password change is not available for this account");

        verifyNoInteractions(passwordEncoder, userRepository, authSessionService, auditLogService);
    }

    /**
     * Kịch bản: mật khẩu hiện tại người dùng nhập không đúng.
     * Given: current user có password hash nhưng encoder không khớp currentPassword.
     * When: đổi mật khẩu được yêu cầu.
     * Then: trả INVALID_CREDENTIALS và giữ nguyên hash/session.
     * Ý nghĩa bảo mật: một access token bị chiếm không đủ để đổi credential nếu không biết mật khẩu hiện tại.
     */
    @Test
    void changePasswordShouldRejectWrongCurrentPassword() {
        UserAccount user = createUser();
        user.setPasswordHash("encoded-current-password");
        when(currentUserService.requireAuthenticatedUser()).thenReturn(user);
        when(passwordEncoder.matches("WrongPass1!", "encoded-current-password")).thenReturn(false);

        assertThatThrownBy(() -> passwordService.changeCurrentUserPassword(
                new ChangePasswordRequest("WrongPass1!", "NewPass1!", "NewPass1!")))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS))
                .hasMessage("Current password is incorrect");

        verify(userRepository, never()).save(any());
        verifyNoInteractions(authSessionService, auditLogService);
    }

    /**
     * Kịch bản: mật khẩu mới thực chất trùng mật khẩu hiện tại.
     * Given: currentPassword và newPassword đều được encoder xác nhận khớp hash cũ.
     * When: changeCurrentUserPassword được gọi.
     * Then: trả BUSINESS_RULE_VIOLATION, không encode/lưu lại và không revoke session.
     * Ý nghĩa bảo mật: tránh tạo cảm giác đã đổi credential trong khi bí mật đăng nhập không thay đổi.
     */
    @Test
    void changePasswordShouldRejectReusingCurrentPassword() {
        UserAccount user = createUser();
        user.setPasswordHash("encoded-current-password");
        when(currentUserService.requireAuthenticatedUser()).thenReturn(user);
        when(passwordEncoder.matches("Current1!", "encoded-current-password")).thenReturn(true);
        when(passwordEncoder.matches("SameCurrent1!", "encoded-current-password")).thenReturn(true);

        assertThatThrownBy(() -> passwordService.changeCurrentUserPassword(
                new ChangePasswordRequest("Current1!", "SameCurrent1!", "SameCurrent1!")))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.BUSINESS_RULE_VIOLATION))
                .hasMessage("New password must be different from the current password");

        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).save(any());
        verifyNoInteractions(authSessionService, auditLogService);
    }

    /**
     * Kịch bản: đổi mật khẩu thành công khi đã đăng nhập.
     * Given: current password đúng, mật khẩu mới khác mật khẩu cũ và encoder sinh hash mới.
     * When: changeCurrentUserPassword hoàn tất.
     * Then: cập nhật hash/passwordChangedAt, lưu user, thu hồi toàn bộ session và audit PASSWORD_CHANGED.
     * Ý nghĩa bảo mật: mọi thiết bị phải đăng nhập lại sau khi credential bị thay đổi.
     */
    @Test
    void changePasswordShouldPersistNewHashAndRevokeAllSessions() {
        UserAccount user = createUser();
        user.setPasswordHash("encoded-current-password");
        when(currentUserService.requireAuthenticatedUser()).thenReturn(user);
        when(passwordEncoder.matches("Current1!", "encoded-current-password")).thenReturn(true);
        when(passwordEncoder.matches("NewPass1!", "encoded-current-password")).thenReturn(false);
        when(passwordEncoder.encode("NewPass1!")).thenReturn("encoded-new-password");

        Instant beforeChange = Instant.now();
        passwordService.changeCurrentUserPassword(
                new ChangePasswordRequest("Current1!", "NewPass1!", "NewPass1!"));

        assertThat(user.getPasswordHash()).isEqualTo("encoded-new-password");
        assertThat(user.getPasswordChangedAt()).isAfterOrEqualTo(beforeChange);
        verify(userRepository).save(user);
        verify(authSessionService).revokeAll(user);
        verify(auditLogService).record(
                "student@example.com", "PASSWORD_CHANGED", "USER", user.getId().toString());
    }

    private PasswordResetToken resetToken(String rawToken) {
        PasswordResetToken token = new PasswordResetToken();
        token.setUser(createUser());
        token.setTokenHash(hash(rawToken));
        token.setExpiresAt(Instant.now().plus(Duration.ofMinutes(10)));
        return token;
    }

    private UserAccount createUser() {
        UserAccount user = new UserAccount();
        user.setId(UUID.randomUUID());
        user.setEmail("student@example.com");
        user.setFullName("Student");
        user.setRole("TRAINEE");
        user.setStatus("active");
        user.setEmailVerifiedAt(Instant.now());
        user.setFailedLoginAttempts(0);
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
