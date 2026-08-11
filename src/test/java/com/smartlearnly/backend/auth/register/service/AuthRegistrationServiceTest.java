package com.smartlearnly.backend.auth.register.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.auth.config.AuthProperties;
import com.smartlearnly.backend.auth.register.dto.RegisterRequest;
import com.smartlearnly.backend.auth.register.dto.ResendVerificationRequest;
import com.smartlearnly.backend.auth.register.dto.VerifyEmailRequest;
import com.smartlearnly.backend.auth.register.entity.OtpVerification;
import com.smartlearnly.backend.auth.register.repository.OtpVerificationRepository;
import com.smartlearnly.backend.auth.service.EmailService;
import com.smartlearnly.backend.common.audit.AuditLogService;
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
class AuthRegistrationServiceTest {
    @Mock
    private UserRepository userRepository;
    @Mock
    private OtpVerificationRepository otpVerificationRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private EmailService emailService;

    private AuthProperties authProperties;
    private AuthRegistrationService registrationService;

    @BeforeEach
    void setUp() {
        authProperties = new AuthProperties();
        authProperties.setEmailVerificationOtpTtl(Duration.ofMinutes(15));
        authProperties.setEmailVerificationOtpMaxAttempts(5);
        authProperties.setEmailVerificationRequestLimit(3);
        authProperties.setEmailVerificationRequestWindow(Duration.ofMinutes(15));
        registrationService = new AuthRegistrationService(
                userRepository,
                otpVerificationRepository,
                passwordEncoder,
                auditLogService,
                authProperties,
                emailService);
    }

    /**
     * Kịch bản: đăng ký thành công bằng email có khoảng trắng và chữ hoa.
     * Given: email chưa tồn tại, mật khẩu xác nhận khớp và repository trả lại user đã có ID.
     * When: người dùng gửi yêu cầu đăng ký.
     * Then: email được chuẩn hóa, trainee ở trạng thái chờ xác thực được lưu, OTP sáu chữ số
     * được băm/lưu/gửi qua email và audit đăng ký được ghi nhận.
     * Ý nghĩa bảo mật: database không lưu OTP rõ và mọi biến thể hoa/thường của cùng email
     * phải được xem là một danh tính duy nhất.
     */
    @Test
    void registerShouldCreatePendingTraineeAndSendVerificationOtp() {
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("new@example.com"))
                .thenReturn(Optional.empty());
        when(passwordEncoder.encode("Secure@123")).thenReturn("encoded-password");
        when(passwordEncoder.encode(matches("\\d{6}"))).thenReturn("encoded-otp");
        when(userRepository.save(any(UserAccount.class))).thenAnswer(invocation -> {
            UserAccount user = invocation.getArgument(0);
            user.setId(UUID.randomUUID());
            return user;
        });

        Instant beforeRegistration = Instant.now();
        registrationService.register(
                new RegisterRequest("  New User  ", "  NEW@example.com  ", "Secure@123", "Secure@123"));

        ArgumentCaptor<UserAccount> userCaptor = ArgumentCaptor.forClass(UserAccount.class);
        verify(userRepository).save(userCaptor.capture());
        UserAccount savedUser = userCaptor.getValue();
        assertThat(savedUser.getEmail()).isEqualTo("new@example.com");
        assertThat(savedUser.getFullName()).isEqualTo("New User");
        assertThat(savedUser.getPasswordHash()).isEqualTo("encoded-password");
        assertThat(savedUser.getRole()).isEqualTo("TRAINEE");
        assertThat(savedUser.getStatus()).isEqualTo("pending_verify");
        assertThat(savedUser.getFailedLoginAttempts()).isZero();

        verify(otpVerificationRepository).markAllUnverifiedAsVerified(
                eq(savedUser.getId()), eq(OtpVerification.EMAIL_VERIFY_PURPOSE), any(Instant.class));
        ArgumentCaptor<OtpVerification> otpCaptor = ArgumentCaptor.forClass(OtpVerification.class);
        verify(otpVerificationRepository).save(otpCaptor.capture());
        OtpVerification savedOtp = otpCaptor.getValue();
        assertThat(savedOtp.getUser()).isSameAs(savedUser);
        assertThat(savedOtp.getEmail()).isEqualTo("new@example.com");
        assertThat(savedOtp.getOtpHash()).isEqualTo("encoded-otp");
        assertThat(savedOtp.getPurpose()).isEqualTo(OtpVerification.EMAIL_VERIFY_PURPOSE);
        assertThat(savedOtp.getAttempts()).isZero();
        assertThat(savedOtp.getMaxAttempts()).isEqualTo(5);
        assertThat(savedOtp.getExpiresAt()).isAfter(beforeRegistration.plus(Duration.ofMinutes(14)));
        verify(emailService).sendVerificationOtp(
                eq("new@example.com"), eq("New User"), matches("\\d{6}"));
        verify(auditLogService).record(
                "new@example.com", "ACCOUNT_REGISTERED", "USER", savedUser.getId().toString());
    }

    /**
     * Kịch bản: mật khẩu xác nhận không trùng với mật khẩu chính.
     * Given: request chứa hai giá trị mật khẩu khác nhau.
     * When: service xử lý đăng ký.
     * Then: trả INVALID_REQUEST ngay trước mọi truy vấn hoặc ghi dữ liệu.
     * Ý nghĩa bảo mật: không tạo tài khoản với mật khẩu ngoài ý muốn và không phát OTP rác.
     */
    @Test
    void registerShouldRejectMismatchedPasswordConfirmationBeforeDatabaseAccess() {
        assertThatThrownBy(() -> registrationService.register(
                new RegisterRequest("New User", "new@example.com", "Secure@123", "Different@123")))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST))
                .hasMessage("Password confirmation does not match");

        verifyNoInteractions(userRepository, otpVerificationRepository, passwordEncoder, emailService, auditLogService);
    }

    /**
     * Kịch bản: email đã thuộc về một tài khoản chưa bị xóa.
     * Given: repository tìm thấy user sau khi email được trim và chuyển về chữ thường.
     * When: đăng ký lại cùng email bằng cách viết khác hoa/thường.
     * Then: trả CONFLICT và không băm mật khẩu, không lưu user, không tạo OTP.
     * Ý nghĩa bảo mật: ngăn tạo danh tính trùng lặp và giữ tính duy nhất của email đăng nhập.
     */
    @Test
    void registerShouldRejectExistingEmailAfterNormalization() {
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("student@example.com"))
                .thenReturn(Optional.of(createUser()));

        assertThatThrownBy(() -> registrationService.register(
                new RegisterRequest("Student", " STUDENT@Example.com ", "Secure@123", "Secure@123")))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT))
                .hasMessage("Email already exists");

        verify(userRepository, never()).save(any());
        verifyNoInteractions(passwordEncoder, otpVerificationRepository, emailService, auditLogService);
    }

    /**
     * Kịch bản: xác thực thành công tài khoản đang chờ bằng OTP mới nhất còn hiệu lực.
     * Given: OTP chưa dùng, chưa hết hạn, chưa vượt số lần thử và mã rõ khớp bản hash.
     * When: người dùng gửi email/OTP hợp lệ.
     * Then: user chuyển sang active, thời điểm xác thực được gán, OTP bị đánh dấu đã dùng
     * và sự kiện EMAIL_VERIFIED được audit.
     * Ý nghĩa bảo mật: một OTP hợp lệ chỉ hoàn tất đúng một lần và kích hoạt đúng chủ tài khoản.
     */
    @Test
    void verifyEmailShouldActivatePendingUserAndConsumeOtp() {
        UserAccount user = createUser();
        OtpVerification otp = createEmailVerificationOtp(user);
        whenLatestOtpRequested("student@example.com", otp);
        when(passwordEncoder.matches("123456", "encoded-otp")).thenReturn(true);

        registrationService.verifyEmail(new VerifyEmailRequest(" STUDENT@example.com ", "123456"));

        assertThat(user.getStatus()).isEqualTo("active");
        assertThat(user.getEmailVerifiedAt()).isNotNull();
        assertThat(otp.getVerifiedAt()).isNotNull();
        verify(userRepository).save(user);
        verify(otpVerificationRepository).save(otp);
        verify(auditLogService).record(
                "student@example.com", "EMAIL_VERIFIED", "USER", user.getId().toString());
    }

    /**
     * Kịch bản: OTP hợp lệ được gửi cho user vốn đã xác thực/active.
     * Given: user đã có emailVerifiedAt nhưng OTP chưa bị đánh dấu sử dụng.
     * When: OTP khớp được xác nhận.
     * Then: không ghi lại user hoặc thay đổi trạng thái, nhưng OTP vẫn bị consume và audit được ghi.
     * Ý nghĩa bảo mật: tránh cập nhật dư thừa đồng thời đảm bảo OTP không thể được tái sử dụng.
     */
    @Test
    void verifyEmailShouldConsumeOtpWithoutSavingAlreadyVerifiedUser() {
        UserAccount user = createUser();
        user.setStatus("active");
        user.setEmailVerifiedAt(Instant.now().minusSeconds(60));
        OtpVerification otp = createEmailVerificationOtp(user);
        whenLatestOtpRequested("student@example.com", otp);
        when(passwordEncoder.matches("123456", "encoded-otp")).thenReturn(true);

        registrationService.verifyEmail(new VerifyEmailRequest("student@example.com", "123456"));

        verify(userRepository, never()).save(any());
        assertThat(otp.getVerifiedAt()).isNotNull();
        verify(otpVerificationRepository).save(otp);
        verify(auditLogService).record(
                "student@example.com", "EMAIL_VERIFIED", "USER", user.getId().toString());
    }

    /**
     * Kịch bản: OTP tìm thấy nhưng đã hết hạn.
     * Given: expiresAt nằm trước thời điểm kiểm tra.
     * When: người dùng gửi đúng chuỗi mã cũ.
     * Then: trả INVALID_OR_EXPIRED_TOKEN mà không gọi password matcher và không cập nhật dữ liệu.
     * Ý nghĩa bảo mật: OTP hết hạn không được hồi sinh dù mã nhập trùng với hash đã lưu.
     */
    @Test
    void verifyEmailShouldRejectExpiredOtpBeforeComparingCode() {
        OtpVerification otp = createEmailVerificationOtp(createUser());
        otp.setExpiresAt(Instant.now().minusSeconds(1));
        whenLatestOtpRequested("student@example.com", otp);

        assertThatThrownBy(() -> registrationService.verifyEmail(
                new VerifyEmailRequest("student@example.com", "123456")))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_OR_EXPIRED_TOKEN))
                .hasMessage("Email verification OTP is invalid or expired");

        verifyNoInteractions(passwordEncoder);
        verify(otpVerificationRepository, never()).save(any());
        verify(userRepository, never()).save(any());
    }

    /**
     * Kịch bản: OTP còn hạn nhưng mã người dùng nhập không khớp.
     * Given: OTP bắt đầu với attempts bằng 0 và password encoder trả false.
     * When: xác minh mã sai.
     * Then: attempts tăng chính xác một, OTP được lưu và trả lỗi token chung.
     * Ý nghĩa bảo mật: giới hạn brute-force OTP nhưng không tiết lộ mã sai hay token hết hạn.
     */
    @Test
    void verifyEmailShouldCountInvalidOtpAttempt() {
        UserAccount user = createUser();
        OtpVerification otp = createEmailVerificationOtp(user);
        whenLatestOtpRequested("student@example.com", otp);
        when(passwordEncoder.matches("654321", "encoded-otp")).thenReturn(false);

        assertThatThrownBy(() -> registrationService.verifyEmail(
                new VerifyEmailRequest("student@example.com", "654321")))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_OR_EXPIRED_TOKEN))
                .hasMessage("Email verification OTP is invalid or expired");

        assertThat(otp.getAttempts()).isEqualTo(1);
        verify(otpVerificationRepository).save(otp);
        verify(userRepository, never()).save(any());
        verifyNoInteractions(auditLogService);
    }

    /**
     * Kịch bản: yêu cầu gửi lại OTP cho email không tồn tại.
     * Given: repository không tìm thấy tài khoản sau khi chuẩn hóa email.
     * When: gọi resendVerification.
     * Then: service hoàn tất im lặng, không tạo OTP và không gửi email.
     * Ý nghĩa bảo mật: response không giúp kẻ tấn công phân biệt email đã đăng ký hay chưa.
     */
    @Test
    void resendVerificationShouldSilentlyIgnoreUnknownEmail() {
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("missing@example.com"))
                .thenReturn(Optional.empty());

        registrationService.resendVerification(new ResendVerificationRequest(" MISSING@example.com "));

        verifyNoInteractions(otpVerificationRepository, passwordEncoder, emailService, auditLogService);
    }

    /**
     * Kịch bản: yêu cầu gửi lại OTP cho tài khoản đã xác thực.
     * Given: repository trả user active đã có emailVerifiedAt.
     * When: gọi resendVerification.
     * Then: user bị loại bởi điều kiện pending, không phát OTP mới và không gửi email.
     * Ý nghĩa bảo mật: tránh tạo thêm credential xác thực không cần thiết cho tài khoản đã active.
     */
    @Test
    void resendVerificationShouldIgnoreAlreadyVerifiedUser() {
        UserAccount verifiedUser = createUser();
        verifiedUser.setStatus("active");
        verifiedUser.setEmailVerifiedAt(Instant.now());
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("student@example.com"))
                .thenReturn(Optional.of(verifiedUser));

        registrationService.resendVerification(new ResendVerificationRequest("student@example.com"));

        verifyNoInteractions(otpVerificationRepository, passwordEncoder, emailService, auditLogService);
    }

    /**
     * Kịch bản: gửi lại OTP hợp lệ cho tài khoản còn pending và chưa chạm rate limit.
     * Given: số yêu cầu gần đây thấp hơn giới hạn cấu hình.
     * When: user yêu cầu resend.
     * Then: mọi OTP chưa dùng trước đó bị vô hiệu, OTP mới được lưu với TTL/max-attempt đúng
     * và chỉ mã rõ sáu chữ số được chuyển cho email service.
     * Ý nghĩa bảo mật: tại một thời điểm chỉ OTP mới nhất có hiệu lực.
     */
    @Test
    void resendVerificationShouldInvalidateOldOtpAndSendANewOne() {
        UserAccount user = createUser();
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("student@example.com"))
                .thenReturn(Optional.of(user));
        when(otpVerificationRepository.countByEmailIgnoreCaseAndPurposeAndCreatedAtAfter(
                eq("student@example.com"), eq(OtpVerification.EMAIL_VERIFY_PURPOSE), any(Instant.class)))
                .thenReturn(2L);
        when(passwordEncoder.encode(matches("\\d{6}"))).thenReturn("new-encoded-otp");

        registrationService.resendVerification(new ResendVerificationRequest("student@example.com"));

        verify(otpVerificationRepository).markAllUnverifiedAsVerified(
                eq(user.getId()), eq(OtpVerification.EMAIL_VERIFY_PURPOSE), any(Instant.class));
        ArgumentCaptor<OtpVerification> otpCaptor = ArgumentCaptor.forClass(OtpVerification.class);
        verify(otpVerificationRepository).save(otpCaptor.capture());
        assertThat(otpCaptor.getValue().getOtpHash()).isEqualTo("new-encoded-otp");
        assertThat(otpCaptor.getValue().getMaxAttempts()).isEqualTo(5);
        verify(emailService).sendVerificationOtp(
                eq("student@example.com"), eq("Student"), matches("\\d{6}"));
    }

    /**
     * Kịch bản: yêu cầu OTP thứ tư trong cửa sổ giới hạn 15 phút.
     * Given: repository đếm được ba yêu cầu, bằng đúng limit cấu hình.
     * When: user tiếp tục yêu cầu resend.
     * Then: trả RATE_LIMIT_EXCEEDED trước khi vô hiệu OTP cũ, tạo OTP mới hoặc gửi email.
     * Ý nghĩa bảo mật: giảm spam email và brute-force/reset abuse mà vẫn giữ OTP hiện tại dùng được.
     */
    @Test
    void resendVerificationShouldRejectFourthRequestWithinWindow() {
        UserAccount user = createUser();
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("student@example.com"))
                .thenReturn(Optional.of(user));
        when(otpVerificationRepository.countByEmailIgnoreCaseAndPurposeAndCreatedAtAfter(
                eq("student@example.com"), eq(OtpVerification.EMAIL_VERIFY_PURPOSE), any(Instant.class)))
                .thenReturn(3L);

        assertThatThrownBy(() -> registrationService.resendVerification(
                new ResendVerificationRequest("student@example.com")))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.RATE_LIMIT_EXCEEDED));

        verify(otpVerificationRepository, never()).markAllUnverifiedAsVerified(any(), any(), any());
        verify(otpVerificationRepository, never()).save(any());
        verifyNoInteractions(passwordEncoder, emailService, auditLogService);
    }

    private void whenLatestOtpRequested(String email, OtpVerification otp) {
        when(otpVerificationRepository.findTopByEmailIgnoreCaseAndPurposeAndVerifiedAtIsNullOrderByCreatedAtDesc(
                email, OtpVerification.EMAIL_VERIFY_PURPOSE))
                .thenReturn(Optional.of(otp));
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

    private OtpVerification createEmailVerificationOtp(UserAccount user) {
        OtpVerification otp = new OtpVerification();
        otp.setUser(user);
        otp.setEmail(user.getEmail());
        otp.setOtpHash("encoded-otp");
        otp.setPurpose(OtpVerification.EMAIL_VERIFY_PURPOSE);
        otp.setAttempts(0);
        otp.setMaxAttempts(5);
        otp.setExpiresAt(Instant.now().plusSeconds(300));
        return otp;
    }
}
