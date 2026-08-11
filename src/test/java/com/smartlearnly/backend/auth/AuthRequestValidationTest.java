package com.smartlearnly.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartlearnly.backend.auth.google.dto.GoogleLoginRequest;
import com.smartlearnly.backend.auth.login.dto.LoginRequest;
import com.smartlearnly.backend.auth.password.dto.ChangePasswordRequest;
import com.smartlearnly.backend.auth.password.dto.ForgotPasswordRequest;
import com.smartlearnly.backend.auth.password.dto.ResetPasswordRequest;
import com.smartlearnly.backend.auth.profile.dto.UpdateProfileRequest;
import com.smartlearnly.backend.auth.register.dto.RegisterRequest;
import com.smartlearnly.backend.auth.register.dto.ResendVerificationRequest;
import com.smartlearnly.backend.auth.register.dto.VerifyEmailRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class AuthRequestValidationTest {
    private static final String VALID_EMAIL = "student@example.com";
    private static final String VALID_PASSWORD = "Secure@123";

    private static jakarta.validation.ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidatorFactory() {
        validatorFactory.close();
    }

    /**
     * Field kiểm tra: RegisterRequest.fullName.
     * Given: fullName lần lượt là null, chuỗi rỗng, dấu cách hoặc tab; các field còn lại đều hợp lệ.
     * When: Bean Validation kiểm tra request đăng ký.
     * Then: luôn có violation đúng field fullName với message "Full name is required".
     * Giá trị biên này chứng minh @NotBlank chặn cả null, empty và whitespace chứ không chỉ empty string.
     */
    @ParameterizedTest(name = "register fullName bắt buộc: [{index}] value={0}")
    @NullSource
    @ValueSource(strings = {"", " ", "   ", "\t", "\n"})
    void registerFullNameShouldRejectNullEmptyAndWhitespace(String fullName) {
        RegisterRequest request = new RegisterRequest(
                fullName, VALID_EMAIL, VALID_PASSWORD, VALID_PASSWORD);

        assertViolation(request, "fullName", "Full name is required");
    }

    /**
     * Field kiểm tra: RegisterRequest.fullName tại giới hạn độ dài.
     * Given: một tên dài đúng 150 ký tự và một tên dài 151 ký tự.
     * When: cùng được validate với payload đăng ký hợp lệ.
     * Then: 150 ký tự được chấp nhận; 151 ký tự bị từ chối bằng message max-length chính xác.
     * Test bắt lỗi off-by-one khi thay đổi @Size(max = 150).
     */
    @Test
    void registerFullNameShouldAccept150CharactersAndReject151() {
        RegisterRequest atLimit = new RegisterRequest(
                "a".repeat(150), VALID_EMAIL, VALID_PASSWORD, VALID_PASSWORD);
        RegisterRequest aboveLimit = new RegisterRequest(
                "a".repeat(151), VALID_EMAIL, VALID_PASSWORD, VALID_PASSWORD);

        assertNoViolationFor(atLimit, "fullName");
        assertViolation(aboveLimit, "fullName", "Full name must not exceed 150 characters");
    }

    /**
     * Field kiểm tra: RegisterRequest.email bắt buộc.
     * Given: email là null, empty hoặc chỉ whitespace; fullName/password hợp lệ.
     * When: validate request đăng ký.
     * Then: field email phải có message "Email is required" trong mọi trường hợp.
     * Đây là contract @NotBlank độc lập với việc @Email có thể tạo thêm violation cho whitespace.
     */
    @ParameterizedTest(name = "register email bắt buộc: [{index}] value={0}")
    @NullSource
    @ValueSource(strings = {"", " ", "   ", "\t"})
    void registerEmailShouldRejectNullEmptyAndWhitespace(String email) {
        RegisterRequest request = new RegisterRequest(
                "Student", email, VALID_PASSWORD, VALID_PASSWORD);

        assertViolation(request, "email", "Email is required");
    }

    /**
     * Field kiểm tra: RegisterRequest.email đúng cấu trúc địa chỉ email.
     * Given: các chuỗi thiếu @, thiếu local-part, thiếu domain hoặc chứa whitespace nội bộ.
     * When: @Email kiểm tra từng giá trị.
     * Then: tất cả bị từ chối bằng cùng message format để frontend hiển thị nhất quán.
     * Các case được chọn không phụ thuộc việc domain có TLD hay không của từng implementation validator.
     */
    @ParameterizedTest(name = "register email sai format: [{index}] value={0}")
    @ValueSource(strings = {"plainaddress", "@example.com", "user@", "user example@example.com", "user@@example.com"})
    void registerEmailShouldRejectMalformedAddress(String email) {
        RegisterRequest request = new RegisterRequest(
                "Student", email, VALID_PASSWORD, VALID_PASSWORD);

        assertViolation(request, "email", "Email must be a valid email address");
    }

    /**
     * Field kiểm tra: RegisterRequest.email tại giới hạn cột users.email VARCHAR(255).
     * Given: hai email hợp lệ về cấu trúc dài lần lượt đúng 255 và 256 ký tự.
     * When: @Size(max = 255) được thực thi.
     * Then: email 255 ký tự qua validation, email 256 ký tự nhận message giới hạn.
     * Test bảo vệ request boundary khớp database và ngăn lỗi persistence do dữ liệu quá dài.
     */
    @Test
    void registerEmailShouldAccept255CharactersAndReject256() {
        RegisterRequest atLimit = new RegisterRequest(
                "Student", validEmail255(), VALID_PASSWORD, VALID_PASSWORD);
        RegisterRequest aboveLimit = new RegisterRequest(
                "Student", validEmail256(), VALID_PASSWORD, VALID_PASSWORD);

        assertNoViolationFor(atLimit, "email");
        assertViolation(aboveLimit, "email", "Email must not exceed 255 characters");
    }

    /**
     * Field kiểm tra: RegisterRequest.password bắt buộc.
     * Given: password null, empty hoặc whitespace; confirmPassword vẫn hợp lệ để cô lập field đang test.
     * When: request đăng ký được validate.
     * Then: luôn có message "Password is required" trên field password.
     * Test đảm bảo password không thể bị bỏ qua dù @Size/@Pattern bỏ qua null theo đặc tả Bean Validation.
     */
    @ParameterizedTest(name = "register password bắt buộc: [{index}] value={0}")
    @NullSource
    @ValueSource(strings = {"", " ", "   ", "\t"})
    void registerPasswordShouldRejectNullEmptyAndWhitespace(String password) {
        RegisterRequest request = new RegisterRequest(
                "Student", VALID_EMAIL, password, VALID_PASSWORD);

        assertViolation(request, "password", "Password is required");
    }

    /**
     * Field kiểm tra: RegisterRequest.password ở ngoài khoảng 8–100 ký tự.
     * Given: mật khẩu có đủ bốn nhóm ký tự nhưng dài 7 hoặc 101 ký tự.
     * When: @Size kiểm tra password.
     * Then: cả hai bị từ chối bằng message khoảng độ dài chính xác.
     * Việc vẫn đủ uppercase/lowercase/digit/special giúp test chỉ nhắm đến rule độ dài.
     */
    @Test
    void registerPasswordShouldRejectValuesOutsideEightToOneHundredCharacters() {
        assertViolation(
                new RegisterRequest("Student", VALID_EMAIL, strongPassword(7), VALID_PASSWORD),
                "password",
                "Password must be between 8 and 100 characters");
        assertViolation(
                new RegisterRequest("Student", VALID_EMAIL, strongPassword(101), VALID_PASSWORD),
                "password",
                "Password must be between 8 and 100 characters");
    }

    /**
     * Field kiểm tra: RegisterRequest.password về độ mạnh.
     * Given: mỗi password dài hợp lệ nhưng lần lượt thiếu chữ hoa, chữ thường, chữ số hoặc ký tự đặc biệt.
     * When: regex độ mạnh được validate.
     * Then: từng giá trị đều nhận cùng message composition rule.
     * Test ngăn việc regex bị nới lỏng và bỏ sót một nhóm ký tự bắt buộc.
     */
    @ParameterizedTest(name = "register password thiếu nhóm ký tự: [{index}] value={0}")
    @ValueSource(strings = {"secure@123", "SECURE@123", "Secure@Test", "Secure123"})
    void registerPasswordShouldRequireUpperLowerDigitAndSpecialCharacter(String password) {
        RegisterRequest request = new RegisterRequest(
                "Student", VALID_EMAIL, password, VALID_PASSWORD);

        assertViolation(
                request,
                "password",
                "Password must contain uppercase, lowercase, number, and special character");
    }

    /**
     * Field kiểm tra: RegisterRequest.password tại đúng hai biên hợp lệ.
     * Given: password 8 ký tự và password 100 ký tự, đều đủ bốn nhóm ký tự.
     * When: validate request đăng ký.
     * Then: field password không có violation ở cả hai biên.
     * Test dương này tránh trường hợp test âm vẫn pass khi regex vô tình chặn cả dữ liệu hợp lệ.
     */
    @Test
    void registerPasswordShouldAcceptExactlyEightAndOneHundredCharacters() {
        String eightCharacters = strongPassword(8);
        String oneHundredCharacters = strongPassword(100);

        assertNoViolationFor(
                new RegisterRequest("Student", VALID_EMAIL, eightCharacters, eightCharacters),
                "password");
        assertNoViolationFor(
                new RegisterRequest("Student", VALID_EMAIL, oneHundredCharacters, oneHundredCharacters),
                "password");
    }

    /**
     * Field kiểm tra: RegisterRequest.confirmPassword bắt buộc.
     * Given: confirmation null, empty hoặc whitespace trong khi password chính hợp lệ.
     * When: Bean Validation chạy trước service.
     * Then: field confirmPassword có message bắt buộc.
     * Rule confirmation khớp password là rule liên-field và tiếp tục được kiểm tra ở AuthRegistrationServiceTest.
     */
    @ParameterizedTest(name = "register confirmPassword bắt buộc: [{index}] value={0}")
    @NullSource
    @ValueSource(strings = {"", " ", "   ", "\t"})
    void registerConfirmationShouldRejectNullEmptyAndWhitespace(String confirmation) {
        RegisterRequest request = new RegisterRequest(
                "Student", VALID_EMAIL, VALID_PASSWORD, confirmation);

        assertViolation(request, "confirmPassword", "Password confirmation is required");
    }

    /**
     * Field kiểm tra: RegisterRequest.confirmPassword giới hạn 8–100 ký tự.
     * Given: confirmation dài 7, 8, 100 và 101 ký tự.
     * When: @Size được thực thi độc lập với rule equality ở service.
     * Then: 7/101 bị từ chối, 8/100 không có violation độ dài.
     * Test chặn payload confirmation quá lớn và giữ boundary đồng nhất với password chính.
     */
    @Test
    void registerConfirmationShouldEnforceEightToOneHundredCharacterBoundary() {
        assertViolation(
                new RegisterRequest("Student", VALID_EMAIL, VALID_PASSWORD, "a".repeat(7)),
                "confirmPassword",
                "Password confirmation must be between 8 and 100 characters");
        assertNoViolationFor(
                new RegisterRequest("Student", VALID_EMAIL, VALID_PASSWORD, "a".repeat(8)),
                "confirmPassword");
        assertNoViolationFor(
                new RegisterRequest("Student", VALID_EMAIL, strongPassword(100), "a".repeat(100)),
                "confirmPassword");
        assertViolation(
                new RegisterRequest("Student", VALID_EMAIL, VALID_PASSWORD, "a".repeat(101)),
                "confirmPassword",
                "Password confirmation must be between 8 and 100 characters");
    }

    /**
     * Field kiểm tra: LoginRequest.email áp dụng cùng contract bắt buộc/format/max-255 như đăng ký.
     * Given: email null, malformed và email dài 256; password login hợp lệ.
     * When: validate từng request login.
     * Then: mỗi case trả đúng message tương ứng; email đúng 255 ký tự vẫn được chấp nhận.
     * Test bảo đảm endpoint login không nhận dữ liệu vượt cột dù không thực hiện insert.
     */
    @Test
    void loginEmailShouldEnforceRequiredFormatAndMaximumLength() {
        assertViolation(new LoginRequest(null, VALID_PASSWORD), "email", "Email is required");
        assertViolation(
                new LoginRequest("not-an-email", VALID_PASSWORD),
                "email",
                "Email must be a valid email address");
        assertNoViolationFor(new LoginRequest(validEmail255(), VALID_PASSWORD), "email");
        assertViolation(
                new LoginRequest(validEmail256(), VALID_PASSWORD),
                "email",
                "Email must not exceed 255 characters");
    }

    /**
     * Field kiểm tra: LoginRequest.password chỉ yêu cầu có giá trị và không vượt 100 ký tự.
     * Given: password null, whitespace, đúng 100 và dài 101 ký tự.
     * When: validate login request.
     * Then: null/whitespace nhận required, 100 được chấp nhận, 101 nhận max-length.
     * Login không kiểm tra composition vì mật khẩu sai phải đi qua authentication, nhưng giới hạn max ngăn input quá lớn.
     */
    @Test
    void loginPasswordShouldRequireValueAndRejectMoreThanOneHundredCharacters() {
        assertViolation(new LoginRequest(VALID_EMAIL, null), "password", "Password is required");
        assertViolation(new LoginRequest(VALID_EMAIL, "   "), "password", "Password is required");
        assertNoViolationFor(new LoginRequest(VALID_EMAIL, "p".repeat(100)), "password");
        assertViolation(
                new LoginRequest(VALID_EMAIL, "p".repeat(101)),
                "password",
                "Password must not exceed 100 characters");
    }

    /**
     * Field kiểm tra: VerifyEmailRequest.email có required/format/max-255 giống register.
     * Given: email empty, malformed, đúng 255 và 256 ký tự; OTP luôn hợp lệ.
     * When: validate request xác thực email.
     * Then: message đúng cho từng lỗi, còn giá trị 255 ký tự không bị từ chối.
     * Test giữ contract email nhất quán trên endpoint public sử dụng OTP.
     */
    @Test
    void verifyEmailAddressShouldEnforceRequiredFormatAndMaximumLength() {
        assertViolation(new VerifyEmailRequest("", "123456"), "email", "Email is required");
        assertViolation(
                new VerifyEmailRequest("invalid-email", "123456"),
                "email",
                "Email must be a valid email address");
        assertNoViolationFor(new VerifyEmailRequest(validEmail255(), "123456"), "email");
        assertViolation(
                new VerifyEmailRequest(validEmail256(), "123456"),
                "email",
                "Email must not exceed 255 characters");
    }

    /**
     * Field kiểm tra: VerifyEmailRequest.otpCode bắt buộc.
     * Given: OTP null, empty hoặc whitespace.
     * When: validate request xác thực email.
     * Then: luôn có message "OTP code is required" trên otpCode.
     * Test bảo đảm @Pattern không phải lớp duy nhất vì regex constraint bỏ qua null.
     */
    @ParameterizedTest(name = "verify OTP bắt buộc: [{index}] value={0}")
    @NullSource
    @ValueSource(strings = {"", " ", "   ", "\t"})
    void verificationOtpShouldRejectNullEmptyAndWhitespace(String otpCode) {
        VerifyEmailRequest request = new VerifyEmailRequest(VALID_EMAIL, otpCode);

        assertViolation(request, "otpCode", "OTP code is required");
    }

    /**
     * Field kiểm tra: VerifyEmailRequest.otpCode phải đúng sáu chữ số ASCII.
     * Given: OTP dài 5, dài 7, chứa chữ, chứa dấu, có khoảng trắng hoặc dùng chữ số full-width Unicode.
     * When: regex ^\\d{6}$ được validate.
     * Then: mọi case đều bị từ chối bằng message format OTP chính xác.
     * Test ngăn mã gần đúng hoặc ký tự nhìn giống số đi vào bước so hash.
     */
    @ParameterizedTest(name = "verify OTP sai format: [{index}] value={0}")
    @ValueSource(strings = {"12345", "1234567", "12345A", "12-456", " 123456", "123456 ", "１２３４５６"})
    void verificationOtpShouldRejectAnythingExceptSixAsciiDigits(String otpCode) {
        VerifyEmailRequest request = new VerifyEmailRequest(VALID_EMAIL, otpCode);

        assertViolation(request, "otpCode", "OTP code must contain exactly 6 digits");
    }

    /**
     * Field kiểm tra: VerifyEmailRequest.otpCode với các biên số hợp lệ.
     * Given: mã nhỏ nhất biểu diễn được "000000" và mã lớn nhất "999999".
     * When: validate request.
     * Then: cả hai không có violation otpCode.
     * Test chứng minh leading zero được giữ hợp lệ và OTP được xử lý như chuỗi sáu chữ số, không phải số nguyên.
     */
    @ParameterizedTest(name = "verify OTP hợp lệ: [{index}] value={0}")
    @ValueSource(strings = {"000000", "123456", "999999"})
    void verificationOtpShouldAcceptEverySixDigitBoundary(String otpCode) {
        assertNoViolationFor(new VerifyEmailRequest(VALID_EMAIL, otpCode), "otpCode");
    }

    /**
     * Field kiểm tra: ResendVerificationRequest.email.
     * Given: email null, malformed, đúng 255 và dài 256 ký tự.
     * When: validate request resend OTP.
     * Then: required/format/max-length được báo đúng và biên 255 được chấp nhận.
     * Test đồng bộ endpoint resend với register/verify để không có đường vào email quá dài.
     */
    @Test
    void resendVerificationEmailShouldUseTheSharedEmailContract() {
        assertViolation(new ResendVerificationRequest(null), "email", "Email is required");
        assertViolation(
                new ResendVerificationRequest("invalid-email"),
                "email",
                "Email must be a valid email address");
        assertNoViolationFor(new ResendVerificationRequest(validEmail255()), "email");
        assertViolation(
                new ResendVerificationRequest(validEmail256()),
                "email",
                "Email must not exceed 255 characters");
    }

    /**
     * Field kiểm tra: ForgotPasswordRequest.email.
     * Given: email whitespace, malformed, đúng 255 và dài 256 ký tự.
     * When: validate forgot-password request.
     * Then: required/format/max-length hoạt động độc lập và biên database được tôn trọng.
     * Việc chống email enumeration ở response không có nghĩa là backend được bỏ validation định dạng/kích thước input.
     */
    @Test
    void forgotPasswordEmailShouldUseTheSharedEmailContract() {
        assertViolation(new ForgotPasswordRequest("   "), "email", "Email is required");
        assertViolation(
                new ForgotPasswordRequest("invalid-email"),
                "email",
                "Email must be a valid email address");
        assertNoViolationFor(new ForgotPasswordRequest(validEmail255()), "email");
        assertViolation(
                new ForgotPasswordRequest(validEmail256()),
                "email",
                "Email must not exceed 255 characters");
    }

    /**
     * Field kiểm tra: ResetPasswordRequest.token bắt buộc và tối đa 512 ký tự.
     * Given: token null, whitespace, đúng 512 và dài 513 ký tự; password mới luôn hợp lệ.
     * When: validate reset request.
     * Then: null/blank nhận required, 512 qua, 513 nhận "Reset token is too long".
     * Test giới hạn credential do client kiểm soát trước khi service hash và query database.
     */
    @Test
    void resetTokenShouldRequireValueAndEnforceFiveHundredTwelveCharacterMaximum() {
        assertViolation(
                new ResetPasswordRequest(null, VALID_PASSWORD, VALID_PASSWORD),
                "token",
                "Reset token is required");
        assertViolation(
                new ResetPasswordRequest("   ", VALID_PASSWORD, VALID_PASSWORD),
                "token",
                "Reset token is required");
        assertNoViolationFor(
                new ResetPasswordRequest("t".repeat(512), VALID_PASSWORD, VALID_PASSWORD),
                "token");
        assertViolation(
                new ResetPasswordRequest("t".repeat(513), VALID_PASSWORD, VALID_PASSWORD),
                "token",
                "Reset token is too long");
    }

    /**
     * Field kiểm tra: ResetPasswordRequest.newPassword dùng đầy đủ policy 8–100 và composition.
     * Given: password null, dài 7, dài 101, thiếu chữ hoa và hai password mạnh đúng biên 8/100.
     * When: validate reset request.
     * Then: từng lỗi nhận message tương ứng; hai biên hợp lệ không có violation.
     * Test đảm bảo reset password không trở thành đường vòng tạo mật khẩu yếu hơn đăng ký.
     */
    @Test
    void resetNewPasswordShouldUseTheSameStrengthPolicyAsRegistration() {
        assertViolation(
                new ResetPasswordRequest("token", null, VALID_PASSWORD),
                "newPassword",
                "New password is required");
        assertViolation(
                new ResetPasswordRequest("token", "Aa1!aaa", VALID_PASSWORD),
                "newPassword",
                "New password must be between 8 and 100 characters");
        assertViolation(
                new ResetPasswordRequest("token", strongPassword(101), VALID_PASSWORD),
                "newPassword",
                "New password must be between 8 and 100 characters");
        assertViolation(
                new ResetPasswordRequest("token", "secure@123", VALID_PASSWORD),
                "newPassword",
                "New password must contain uppercase, lowercase, number, and special character");
        assertNoViolationFor(
                new ResetPasswordRequest("token", strongPassword(8), strongPassword(8)),
                "newPassword");
        assertNoViolationFor(
                new ResetPasswordRequest("token", strongPassword(100), strongPassword(100)),
                "newPassword");
    }

    /**
     * Field kiểm tra: ResetPasswordRequest.confirmPassword bắt buộc và dài 8–100 ký tự.
     * Given: confirmation null, dài 7, đúng 8/100 và dài 101 ký tự.
     * When: validate reset request.
     * Then: required/size message đúng; hai biên hợp lệ không có violation field.
     * Rule bằng newPassword được kiểm tra riêng trong AuthPasswordServiceTest để không nhầm field rule với business rule.
     */
    @Test
    void resetConfirmationShouldRequireValueAndEnforcePasswordLengthBoundary() {
        assertViolation(
                new ResetPasswordRequest("token", VALID_PASSWORD, null),
                "confirmPassword",
                "Password confirmation is required");
        assertViolation(
                new ResetPasswordRequest("token", VALID_PASSWORD, "a".repeat(7)),
                "confirmPassword",
                "Password confirmation must be between 8 and 100 characters");
        assertNoViolationFor(
                new ResetPasswordRequest("token", VALID_PASSWORD, "a".repeat(8)),
                "confirmPassword");
        assertNoViolationFor(
                new ResetPasswordRequest("token", strongPassword(100), "a".repeat(100)),
                "confirmPassword");
        assertViolation(
                new ResetPasswordRequest("token", VALID_PASSWORD, "a".repeat(101)),
                "confirmPassword",
                "Password confirmation must be between 8 and 100 characters");
    }

    /**
     * Field kiểm tra: ChangePasswordRequest.currentPassword bắt buộc và tối đa 100 ký tự.
     * Given: current password null, whitespace, đúng 100 và dài 101 ký tự.
     * When: validate change-password request.
     * Then: null/blank nhận required, 100 qua và 101 bị từ chối bằng max-length message.
     * Không áp composition cho current password để tài khoản cũ vẫn có thể chứng minh credential hiện tại.
     */
    @Test
    void currentPasswordShouldRequireValueAndRejectMoreThanOneHundredCharacters() {
        assertViolation(
                new ChangePasswordRequest(null, VALID_PASSWORD, VALID_PASSWORD),
                "currentPassword",
                "Current password is required");
        assertViolation(
                new ChangePasswordRequest("   ", VALID_PASSWORD, VALID_PASSWORD),
                "currentPassword",
                "Current password is required");
        assertNoViolationFor(
                new ChangePasswordRequest("c".repeat(100), VALID_PASSWORD, VALID_PASSWORD),
                "currentPassword");
        assertViolation(
                new ChangePasswordRequest("c".repeat(101), VALID_PASSWORD, VALID_PASSWORD),
                "currentPassword",
                "Current password must not exceed 100 characters");
    }

    /**
     * Field kiểm tra: ChangePasswordRequest.newPassword dùng cùng policy mạnh như register/reset.
     * Given: new password blank, ngoài 8–100, thiếu special và hợp lệ đúng biên.
     * When: validate change-password request.
     * Then: từng rule trả đúng message, password 8/100 ký tự hợp lệ được chấp nhận.
     * Test ngăn endpoint authenticated tạo credential yếu hoặc dài ngoài contract.
     */
    @Test
    void changedNewPasswordShouldUseTheSharedStrengthPolicy() {
        assertViolation(
                new ChangePasswordRequest("Current@123", "", VALID_PASSWORD),
                "newPassword",
                "New password is required");
        assertViolation(
                new ChangePasswordRequest("Current@123", "Aa1!aaa", VALID_PASSWORD),
                "newPassword",
                "New password must be between 8 and 100 characters");
        assertViolation(
                new ChangePasswordRequest("Current@123", strongPassword(101), VALID_PASSWORD),
                "newPassword",
                "New password must be between 8 and 100 characters");
        assertViolation(
                new ChangePasswordRequest("Current@123", "Secure123", VALID_PASSWORD),
                "newPassword",
                "New password must contain uppercase, lowercase, number, and special character");
        assertNoViolationFor(
                new ChangePasswordRequest("Current@123", strongPassword(8), strongPassword(8)),
                "newPassword");
        assertNoViolationFor(
                new ChangePasswordRequest("Current@123", strongPassword(100), strongPassword(100)),
                "newPassword");
    }

    /**
     * Field kiểm tra: ChangePasswordRequest.confirmPassword bắt buộc và dài 8–100 ký tự.
     * Given: confirmation null, 7, 8, 100 và 101 ký tự.
     * When: validate request đổi mật khẩu.
     * Then: required/size violation đúng; hai biên hợp lệ không bị chặn.
     * Equality với newPassword và khác currentPassword là business rule đã được test ở AuthPasswordServiceTest.
     */
    @Test
    void changedPasswordConfirmationShouldUseTheSharedLengthBoundary() {
        assertViolation(
                new ChangePasswordRequest("Current@123", VALID_PASSWORD, null),
                "confirmPassword",
                "Password confirmation is required");
        assertViolation(
                new ChangePasswordRequest("Current@123", VALID_PASSWORD, "a".repeat(7)),
                "confirmPassword",
                "Password confirmation must be between 8 and 100 characters");
        assertNoViolationFor(
                new ChangePasswordRequest("Current@123", VALID_PASSWORD, "a".repeat(8)),
                "confirmPassword");
        assertNoViolationFor(
                new ChangePasswordRequest("Current@123", strongPassword(100), "a".repeat(100)),
                "confirmPassword");
        assertViolation(
                new ChangePasswordRequest("Current@123", VALID_PASSWORD, "a".repeat(101)),
                "confirmPassword",
                "Password confirmation must be between 8 and 100 characters");
    }

    /**
     * Field kiểm tra: GoogleLoginRequest.idToken bắt buộc.
     * Given: token null, empty hoặc whitespace.
     * When: request Google login được validate.
     * Then: mọi case nhận message "Google ID token is required".
     * Chưa đặt max-length vì tài liệu sản phẩm hiện không quy định giới hạn kích thước JWT Google.
     */
    @ParameterizedTest(name = "Google idToken bắt buộc: [{index}] value={0}")
    @NullSource
    @ValueSource(strings = {"", " ", "   ", "\t"})
    void googleIdTokenShouldRejectNullEmptyAndWhitespace(String idToken) {
        assertViolation(
                new GoogleLoginRequest(idToken),
                "idToken",
                "Google ID token is required");
    }

    /**
     * Field kiểm tra: UpdateProfileRequest.fullName là optional nhưng nếu có thì không blank và tối đa 150.
     * Given: null, whitespace, đúng 150 và dài 151 ký tự.
     * When: validate PATCH profile.
     * Then: null được phép để giữ nguyên field; whitespace/151 bị từ chối; 150 được chấp nhận.
     * Test thể hiện đúng semantics PATCH: omitted khác với provided-but-invalid.
     */
    @Test
    void profileFullNameShouldBeOptionalButNonBlankAndAtMostOneHundredFiftyWhenProvided() {
        assertNoViolationFor(new UpdateProfileRequest(null, null, null, null), "fullName");
        assertViolation(
                new UpdateProfileRequest("   ", null, null, null),
                "fullName",
                "Full name must not be blank");
        assertNoViolationFor(new UpdateProfileRequest("a".repeat(150), null, null, null), "fullName");
        assertViolation(
                new UpdateProfileRequest("a".repeat(151), null, null, null),
                "fullName",
                "Full name must not exceed 150 characters");
    }

    /**
     * Field kiểm tra: UpdateProfileRequest.avatarUrl optional và tối đa 2048 ký tự.
     * Given: null, empty, URL/string đúng 2048 và dài 2049 ký tự.
     * When: validate profile request.
     * Then: null/empty/2048 hợp lệ theo contract hiện tại, 2049 bị từ chối.
     * Test chỉ xác nhận giới hạn hiện có; DTO chưa có rule bắt buộc chuỗi phải là URL hợp lệ.
     */
    @Test
    void profileAvatarUrlShouldBeOptionalAndAtMostTwoThousandFortyEightCharacters() {
        assertNoViolationFor(new UpdateProfileRequest(null, null, null, null), "avatarUrl");
        assertNoViolationFor(new UpdateProfileRequest(null, "", null, null), "avatarUrl");
        assertNoViolationFor(new UpdateProfileRequest(null, "u".repeat(2048), null, null), "avatarUrl");
        assertViolation(
                new UpdateProfileRequest(null, "u".repeat(2049), null, null),
                "avatarUrl",
                "Avatar URL must not exceed 2048 characters");
    }

    /**
     * Field kiểm tra: UpdateProfileRequest.phoneNumber với các dạng di động Việt Nam hợp lệ.
     * Given: null để giữ nguyên, empty để xóa và số bắt đầu 03/05/07/08/09 ở dạng 0 hoặc +84.
     * When: regex PhoneNumberRules được validate.
     * Then: mọi giá trị đều không có violation phoneNumber.
     * Test dương bao phủ hai country-prefix contract và toàn bộ nhóm đầu số được cho phép.
     */
    @ParameterizedTest(name = "phone hợp lệ: [{index}] value={0}")
    @NullSource
    @ValueSource(strings = {"", "0351234567", "0551234567", "0791234567", "0881234567", "0901234567", "+84901234567"})
    void profilePhoneShouldAcceptSupportedVietnameseMobileFormats(String phoneNumber) {
        assertNoViolationFor(new UpdateProfileRequest(null, null, phoneNumber, null), "phoneNumber");
    }

    /**
     * Field kiểm tra: UpdateProfileRequest.phoneNumber từ chối format không được hỗ trợ.
     * Given: thiếu dấu +, đầu số 01/02/04/06, sai độ dài, chứa space/dấu gạch/chữ hoặc whitespace-only.
     * When: regex số điện thoại được validate.
     * Then: từng giá trị nhận message chuẩn có ví dụ 090... và +8490....
     * Test ngăn dữ liệu nhìn gần giống số điện thoại nhưng không thể dùng cho liên hệ thực tế.
     */
    @ParameterizedTest(name = "phone không hợp lệ: [{index}] value={0}")
    @ValueSource(strings = {
            "84901234567", "0123456789", "0223456789", "0423456789", "0623456789",
            "090123456", "09012345678", "+840901234567", "090 123 4567", "090-123-4567", "phone", "   "
    })
    void profilePhoneShouldRejectUnsupportedFormat(String phoneNumber) {
        assertViolation(
                new UpdateProfileRequest(null, null, phoneNumber, null),
                "phoneNumber",
                "Phone number must be a valid Vietnamese mobile number, for example 0901234567 or +84901234567");
    }

    /**
     * Field kiểm tra: UpdateProfileRequest.bio optional và tối đa 1000 ký tự.
     * Given: null, empty, bio đúng 1000 và dài 1001 ký tự.
     * When: validate PATCH profile.
     * Then: null/empty/1000 hợp lệ, 1001 nhận message max-length.
     * Test bắt lỗi off-by-one và giữ field tùy chọn có thể được xóa bằng empty string.
     */
    @Test
    void profileBioShouldBeOptionalAndAtMostOneThousandCharacters() {
        assertNoViolationFor(new UpdateProfileRequest(null, null, null, null), "bio");
        assertNoViolationFor(new UpdateProfileRequest(null, null, null, ""), "bio");
        assertNoViolationFor(new UpdateProfileRequest(null, null, null, "b".repeat(1000)), "bio");
        assertViolation(
                new UpdateProfileRequest(null, null, null, "b".repeat(1001)),
                "bio",
                "Bio must not exceed 1000 characters");
    }

    /**
     * Trường hợp tổng hợp dương: các request đại diện với mọi field hợp lệ.
     * Given: register/login/verify/resend/forgot/reset/change/Google/profile đều dùng dữ liệu đúng contract.
     * When: validate toàn bộ DTO.
     * Then: không request nào có violation.
     * Test hồi quy dương bảo đảm ma trận negative/boundary không vô tình làm API từ chối dữ liệu người dùng hợp lệ.
     */
    @Test
    void representativeAuthRequestsShouldPassAllFieldValidation() {
        assertThat(validate(new RegisterRequest(
                "Student", VALID_EMAIL, VALID_PASSWORD, VALID_PASSWORD))).isEmpty();
        assertThat(validate(new LoginRequest(VALID_EMAIL, VALID_PASSWORD))).isEmpty();
        assertThat(validate(new VerifyEmailRequest(VALID_EMAIL, "123456"))).isEmpty();
        assertThat(validate(new ResendVerificationRequest(VALID_EMAIL))).isEmpty();
        assertThat(validate(new ForgotPasswordRequest(VALID_EMAIL))).isEmpty();
        assertThat(validate(new ResetPasswordRequest(
                "reset-token", VALID_PASSWORD, VALID_PASSWORD))).isEmpty();
        assertThat(validate(new ChangePasswordRequest(
                "Current@123", VALID_PASSWORD, VALID_PASSWORD))).isEmpty();
        assertThat(validate(new GoogleLoginRequest("header.payload.signature"))).isEmpty();
        assertThat(validate(new UpdateProfileRequest(
                "Updated Student", "https://example.com/avatar.png", "+84901234567", "Bio"))).isEmpty();
    }

    private <T> void assertViolation(T request, String field, String expectedMessage) {
        assertThat(validate(request)).anySatisfy(violation -> {
            assertThat(violation.getPropertyPath().toString()).isEqualTo(field);
            assertThat(violation.getMessage()).isEqualTo(expectedMessage);
        });
    }

    private <T> void assertNoViolationFor(T request, String field) {
        assertThat(validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .doesNotContain(field);
    }

    private <T> Set<ConstraintViolation<T>> validate(T request) {
        return validator.validate(request);
    }

    private String strongPassword(int length) {
        return "Aa1!" + "a".repeat(length - 4);
    }

    private String validEmail255() {
        return "a".repeat(64)
                + "@"
                + "b".repeat(63)
                + "."
                + "c".repeat(63)
                + "."
                + "d".repeat(62);
    }

    private String validEmail256() {
        return "a".repeat(64)
                + "@"
                + "b".repeat(63)
                + "."
                + "c".repeat(63)
                + "."
                + "d".repeat(63);
    }
}
