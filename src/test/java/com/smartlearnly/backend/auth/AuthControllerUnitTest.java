package com.smartlearnly.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.admin.settings.service.SystemSettingsService;
import com.smartlearnly.backend.auth.google.controller.GoogleAuthController;
import com.smartlearnly.backend.auth.google.dto.GoogleLoginRequest;
import com.smartlearnly.backend.auth.google.service.GoogleAuthService;
import com.smartlearnly.backend.auth.login.dto.LoginRequest;
import com.smartlearnly.backend.auth.login.service.AuthLoginService;
import com.smartlearnly.backend.auth.password.controller.AuthPasswordController;
import com.smartlearnly.backend.auth.password.dto.ChangePasswordRequest;
import com.smartlearnly.backend.auth.password.dto.ForgotPasswordRequest;
import com.smartlearnly.backend.auth.password.dto.ResetPasswordRequest;
import com.smartlearnly.backend.auth.password.service.AuthPasswordService;
import com.smartlearnly.backend.auth.profile.controller.AuthProfileController;
import com.smartlearnly.backend.auth.profile.dto.UpdateProfileRequest;
import com.smartlearnly.backend.auth.profile.dto.UserProfileResponse;
import com.smartlearnly.backend.auth.profile.service.AuthProfileService;
import com.smartlearnly.backend.auth.register.controller.AuthRegistrationController;
import com.smartlearnly.backend.auth.register.dto.RegisterRequest;
import com.smartlearnly.backend.auth.register.dto.ResendVerificationRequest;
import com.smartlearnly.backend.auth.register.dto.VerifyEmailRequest;
import com.smartlearnly.backend.auth.register.service.AuthRegistrationService;
import com.smartlearnly.backend.auth.session.controller.AuthSessionController;
import com.smartlearnly.backend.auth.session.dto.AuthSessionResponse;
import com.smartlearnly.backend.auth.session.service.AuthSessionHttpSupport;
import com.smartlearnly.backend.auth.session.service.AuthSessionService;
import com.smartlearnly.backend.common.api.ApiResponse;
import com.smartlearnly.backend.file.dto.CourseThumbnailUploadResponse;
import com.smartlearnly.backend.file.service.CourseThumbnailService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.multipart.MultipartFile;

class AuthControllerUnitTest {

    /**
     * Kịch bản: HTTP register đã qua Bean Validation và đi vào controller.
     * Given: một RegisterRequest hợp lệ và registration service mock.
     * When: controller xử lý request.
     * Then: chuyển nguyên DTO cho service và trả success body với thông báo yêu cầu kiểm tra email.
     * Ý nghĩa kiến trúc: controller chỉ điều phối transport, không lặp lại business logic đăng ký.
     */
    @Test
    void registrationControllerShouldDelegateRegisterAndReturnPublicMessage() {
        AuthRegistrationService service = mock(AuthRegistrationService.class);
        AuthRegistrationController controller = new AuthRegistrationController(service);
        RegisterRequest request = new RegisterRequest(
                "Student", "student@example.com", "Secure@123", "Secure@123");

        ApiResponse<Void> response = controller.register(request);

        verify(service).register(request);
        assertThat(response.success()).isTrue();
        assertThat(response.data()).isNull();
        assertThat(response.message()).isEqualTo(
                "Registration successful. Check your email to verify the account.");
    }

    /**
     * Kịch bản: HTTP xác thực email đã có payload hợp lệ.
     * Given: VerifyEmailRequest chứa email và OTP sáu chữ số.
     * When: controller gọi verifyEmail.
     * Then: delegate đúng DTO và trả message xác thực thành công không kèm dữ liệu nhạy cảm.
     * Ý nghĩa API: logic consume OTP nằm duy nhất ở service/transaction.
     */
    @Test
    void registrationControllerShouldDelegateEmailVerification() {
        AuthRegistrationService service = mock(AuthRegistrationService.class);
        AuthRegistrationController controller = new AuthRegistrationController(service);
        VerifyEmailRequest request = new VerifyEmailRequest("student@example.com", "123456");

        ApiResponse<Void> response = controller.verifyEmail(request);

        verify(service).verifyEmail(request);
        assertThat(response.success()).isTrue();
        assertThat(response.message()).isEqualTo("Email has been verified successfully");
    }

    /**
     * Kịch bản: HTTP gửi lại OTP cho một email.
     * Given: ResendVerificationRequest hợp lệ.
     * When: controller gọi resendVerification.
     * Then: delegate cho service và luôn trả thông báo điều kiện chung “if account exists”.
     * Ý nghĩa bảo mật: response controller không xác nhận email có tồn tại hay đang pending.
     */
    @Test
    void registrationControllerShouldReturnEnumerationSafeResendMessage() {
        AuthRegistrationService service = mock(AuthRegistrationService.class);
        AuthRegistrationController controller = new AuthRegistrationController(service);
        ResendVerificationRequest request = new ResendVerificationRequest("student@example.com");

        ApiResponse<Void> response = controller.resendVerification(request);

        verify(service).resendVerification(request);
        assertThat(response.success()).isTrue();
        assertThat(response.message()).startsWith(
                "If the account exists and is pending verification");
    }

    /**
     * Kịch bản: HTTP quên mật khẩu được service tiếp nhận.
     * Given: ForgotPasswordRequest hợp lệ.
     * When: controller gọi forgotPassword.
     * Then: delegate nguyên request và trả thông báo chung không xác nhận tài khoản tồn tại.
     * Ý nghĩa bảo mật: lớp HTTP giữ đúng contract chống email enumeration của service.
     */
    @Test
    void passwordControllerShouldReturnEnumerationSafeForgotPasswordMessage() {
        AuthPasswordService service = mock(AuthPasswordService.class);
        AuthPasswordController controller = new AuthPasswordController(service);
        ForgotPasswordRequest request = new ForgotPasswordRequest("student@example.com");

        ApiResponse<Void> response = controller.forgotPassword(request);

        verify(service).forgotPassword(request);
        assertThat(response.success()).isTrue();
        assertThat(response.message()).startsWith("If the account exists");
    }

    /**
     * Kịch bản: HTTP đặt lại mật khẩu bằng token.
     * Given: ResetPasswordRequest đã qua validation.
     * When: controller gọi resetPassword.
     * Then: delegate duy nhất một lần và trả success message sau khi service hoàn tất.
     * Ý nghĩa kiến trúc: controller không tự kiểm tra/consume token ngoài transaction service.
     */
    @Test
    void passwordControllerShouldDelegatePasswordReset() {
        AuthPasswordService service = mock(AuthPasswordService.class);
        AuthPasswordController controller = new AuthPasswordController(service);
        ResetPasswordRequest request = new ResetPasswordRequest("token", "NewPass1!", "NewPass1!");

        ApiResponse<Void> response = controller.resetPassword(request);

        verify(service).resetPassword(request);
        assertThat(response.success()).isTrue();
        assertThat(response.message()).isEqualTo("Password has been reset successfully");
    }

    /**
     * Kịch bản: HTTP đổi mật khẩu của principal hiện tại.
     * Given: ChangePasswordRequest hợp lệ.
     * When: controller gọi changePassword.
     * Then: delegate request mà không nhận user ID từ client và trả success message.
     * Ý nghĩa bảo mật: danh tính mục tiêu do service resolve từ authentication context.
     */
    @Test
    void passwordControllerShouldDelegateCurrentUserPasswordChange() {
        AuthPasswordService service = mock(AuthPasswordService.class);
        AuthPasswordController controller = new AuthPasswordController(service);
        ChangePasswordRequest request = new ChangePasswordRequest("Current1!", "NewPass1!", "NewPass1!");

        ApiResponse<Void> response = controller.changePassword(request);

        verify(service).changeCurrentUserPassword(request);
        assertThat(response.success()).isTrue();
        assertThat(response.message()).isEqualTo("Password changed successfully");
    }

    /**
     * Kịch bản: HTTP login email/password thành công.
     * Given: request có User-Agent, HTTP support resolve IP và login service trả IssuedSession.
     * When: controller.login được gọi.
     * Then: service nhận đúng device/IP rồi HTTP support nhận session để đặt cookie với message chuẩn.
     * Ý nghĩa bảo mật: metadata audit lấy từ request server-side, refresh token được đóng gói ở một helper thống nhất.
     */
    @Test
    void sessionControllerShouldPassRequestMetadataThroughLoginFlow() {
        AuthLoginService loginService = mock(AuthLoginService.class);
        AuthSessionService sessionService = mock(AuthSessionService.class);
        AuthSessionHttpSupport httpSupport = mock(AuthSessionHttpSupport.class);
        AuthSessionController controller = new AuthSessionController(loginService, sessionService, httpSupport);
        LoginRequest request = new LoginRequest("student@example.com", "Secure@123");
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.addHeader(HttpHeaders.USER_AGENT, "test-browser");
        AuthSessionService.IssuedSession session = issuedSession();
        ResponseEntity<ApiResponse<AuthSessionResponse>> expected =
                ResponseEntity.ok(ApiResponse.success("Login successful", session.response()));
        when(httpSupport.clientIp(httpRequest)).thenReturn("203.0.113.10");
        when(loginService.login(request, "test-browser", "203.0.113.10")).thenReturn(session);
        when(httpSupport.sessionResponse("Login successful", session)).thenReturn(expected);

        ResponseEntity<ApiResponse<AuthSessionResponse>> actual = controller.login(request, httpRequest);

        assertThat(actual).isSameAs(expected);
        verify(loginService).login(request, "test-browser", "203.0.113.10");
        verify(httpSupport).sessionResponse("Login successful", session);
    }

    /**
     * Kịch bản: HTTP refresh session bằng cookie hiện tại.
     * Given: HTTP support trích token/IP và session service trả cặp token đã rotate.
     * When: controller.refresh được gọi.
     * Then: rotate nhận đúng raw token/User-Agent/IP và response được đóng gói với message Session refreshed.
     * Ý nghĩa bảo mật: controller không nhận refresh token qua body/query dễ bị log, chỉ dùng cookie contract.
     */
    @Test
    void sessionControllerShouldRotateCookieTokenWithRequestMetadata() {
        AuthLoginService loginService = mock(AuthLoginService.class);
        AuthSessionService sessionService = mock(AuthSessionService.class);
        AuthSessionHttpSupport httpSupport = mock(AuthSessionHttpSupport.class);
        AuthSessionController controller = new AuthSessionController(loginService, sessionService, httpSupport);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.USER_AGENT, "test-browser");
        AuthSessionService.IssuedSession session = issuedSession();
        ResponseEntity<ApiResponse<AuthSessionResponse>> expected =
                ResponseEntity.ok(ApiResponse.success("Session refreshed", session.response()));
        when(httpSupport.requireRefreshToken(request)).thenReturn("old-refresh-token");
        when(httpSupport.clientIp(request)).thenReturn("203.0.113.10");
        when(sessionService.rotate("old-refresh-token", "test-browser", "203.0.113.10"))
                .thenReturn(session);
        when(httpSupport.sessionResponse("Session refreshed", session)).thenReturn(expected);

        ResponseEntity<ApiResponse<AuthSessionResponse>> actual = controller.refresh(request);

        assertThat(actual).isSameAs(expected);
        verify(sessionService).rotate("old-refresh-token", "test-browser", "203.0.113.10");
        verify(httpSupport).sessionResponse("Session refreshed", session);
    }

    /**
     * Kịch bản: HTTP logout khi refresh cookie có thể có hoặc không.
     * Given: HTTP support trả token tùy chọn và chuẩn bị response xóa cookie.
     * When: controller.logout được gọi.
     * Then: session service thu hồi token trước, sau đó trả nguyên response xóa cookie.
     * Ý nghĩa bảo mật/UX: logout vừa revoke server-side vừa luôn dọn credential phía browser.
     */
    @Test
    void sessionControllerShouldRevokeOptionalTokenAndReturnCookieDeletionResponse() {
        AuthLoginService loginService = mock(AuthLoginService.class);
        AuthSessionService sessionService = mock(AuthSessionService.class);
        AuthSessionHttpSupport httpSupport = mock(AuthSessionHttpSupport.class);
        AuthSessionController controller = new AuthSessionController(loginService, sessionService, httpSupport);
        MockHttpServletRequest request = new MockHttpServletRequest();
        ResponseEntity<ApiResponse<Void>> expected =
                ResponseEntity.ok(ApiResponse.success("Logout successful"));
        when(httpSupport.optionalRefreshToken(request)).thenReturn("refresh-token");
        when(httpSupport.logoutResponse()).thenReturn(expected);

        ResponseEntity<ApiResponse<Void>> actual = controller.logout(request);

        assertThat(actual).isSameAs(expected);
        verify(sessionService).logout("refresh-token");
        verify(httpSupport).logoutResponse();
    }

    /**
     * Kịch bản: frontend tải Google OAuth public config.
     * Given: effective settings có client ID null.
     * When: getGoogleConfig được gọi.
     * Then: controller trả chuỗi rỗng thay vì null và không bao giờ trả client secret/scope.
     * Ý nghĩa bảo mật/API: chỉ client ID công khai được expose và response ổn định cho frontend.
     */
    @Test
    void googleControllerShouldReturnEmptyClientIdWhenGoogleIsNotConfigured() {
        GoogleAuthService googleAuthService = mock(GoogleAuthService.class);
        SystemSettingsService settingsService = mock(SystemSettingsService.class);
        AuthSessionHttpSupport httpSupport = mock(AuthSessionHttpSupport.class);
        GoogleAuthController controller = new GoogleAuthController(googleAuthService, settingsService, httpSupport);
        when(settingsService.resolveGoogleSettings()).thenReturn(
                new SystemSettingsService.GoogleOAuthSettings(null, "secret", "openid,profile,email"));

        ApiResponse<GoogleAuthController.GoogleConfigResponse> response = controller.getGoogleConfig();

        assertThat(response.success()).isTrue();
        assertThat(response.message()).isEqualTo("Google config loaded");
        assertThat(response.data().clientId()).isEmpty();
    }

    /**
     * Kịch bản: HTTP Google login thành công.
     * Given: verified request, User-Agent/IP server-side và GoogleAuthService trả session.
     * When: loginWithGoogle được gọi.
     * Then: service nhận đúng metadata và HTTP support đặt cookie theo cùng contract login thường.
     * Ý nghĩa bảo mật: Google login không tạo một cơ chế refresh-cookie khác biệt/dễ cấu hình sai.
     */
    @Test
    void googleControllerShouldIssueSessionUsingSharedCookieContract() {
        GoogleAuthService googleAuthService = mock(GoogleAuthService.class);
        SystemSettingsService settingsService = mock(SystemSettingsService.class);
        AuthSessionHttpSupport httpSupport = mock(AuthSessionHttpSupport.class);
        GoogleAuthController controller = new GoogleAuthController(googleAuthService, settingsService, httpSupport);
        GoogleLoginRequest request = new GoogleLoginRequest("google-id-token");
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.addHeader(HttpHeaders.USER_AGENT, "test-browser");
        AuthSessionService.IssuedSession session = issuedSession();
        ResponseEntity<ApiResponse<AuthSessionResponse>> expected =
                ResponseEntity.ok(ApiResponse.success("Google login successful", session.response()));
        when(httpSupport.clientIp(httpRequest)).thenReturn("203.0.113.10");
        when(googleAuthService.login(request, "test-browser", "203.0.113.10")).thenReturn(session);
        when(httpSupport.sessionResponse("Google login successful", session)).thenReturn(expected);

        ResponseEntity<ApiResponse<AuthSessionResponse>> actual =
                controller.loginWithGoogle(request, httpRequest);

        assertThat(actual).isSameAs(expected);
        verify(googleAuthService).login(request, "test-browser", "203.0.113.10");
        verify(httpSupport).sessionResponse("Google login successful", session);
    }

    /**
     * Kịch bản: HTTP GET profile của principal hiện tại.
     * Given: profile service trả DTO an toàn.
     * When: controller.getProfile được gọi.
     * Then: DTO được giữ nguyên trong success envelope với message chuẩn.
     * Ý nghĩa kiến trúc: controller không truy cập repository hay nhận user ID từ route.
     */
    @Test
    void profileControllerShouldReturnCurrentUserProfile() {
        AuthProfileService profileService = mock(AuthProfileService.class);
        CourseThumbnailService thumbnailService = mock(CourseThumbnailService.class);
        AuthProfileController controller = new AuthProfileController(profileService, thumbnailService);
        UserProfileResponse profile = profile();
        when(profileService.getCurrentUserProfile()).thenReturn(profile);

        ApiResponse<UserProfileResponse> response = controller.getProfile();

        assertThat(response.success()).isTrue();
        assertThat(response.message()).isEqualTo("Profile loaded successfully");
        assertThat(response.data()).isSameAs(profile);
    }

    /**
     * Kịch bản: HTTP PATCH profile với các trường tùy chọn.
     * Given: UpdateProfileRequest đã qua validation và service trả DTO sau lưu.
     * When: controller.updateProfile được gọi.
     * Then: delegate nguyên request và trả DTO mới trong success envelope.
     * Ý nghĩa kiến trúc: quy tắc PATCH/normalize/audit được giữ trong service transaction.
     */
    @Test
    void profileControllerShouldDelegatePartialProfileUpdate() {
        AuthProfileService profileService = mock(AuthProfileService.class);
        CourseThumbnailService thumbnailService = mock(CourseThumbnailService.class);
        AuthProfileController controller = new AuthProfileController(profileService, thumbnailService);
        UpdateProfileRequest request = new UpdateProfileRequest("Updated", null, null, null);
        UserProfileResponse profile = profile();
        when(profileService.updateCurrentUserProfile(request)).thenReturn(profile);

        ApiResponse<UserProfileResponse> response = controller.updateProfile(request);

        verify(profileService).updateCurrentUserProfile(request);
        assertThat(response.message()).isEqualTo("Profile updated successfully");
        assertThat(response.data()).isSameAs(profile);
    }

    /**
     * Kịch bản: HTTP upload avatar thành công.
     * Given: thumbnail service lưu file và trả URL công khai.
     * When: controller.uploadAvatar được gọi.
     * Then: URL được chuyển thành PATCH profile chỉ chứa avatarUrl, DTO cập nhật được trả về.
     * Ý nghĩa bảo mật/kiến trúc: profile không tự tin URL do client gửi trong multipart; URL đến từ file service.
     */
    @Test
    void profileControllerShouldStoreAvatarThenUpdateCurrentProfileWithReturnedUrl() {
        AuthProfileService profileService = mock(AuthProfileService.class);
        CourseThumbnailService thumbnailService = mock(CourseThumbnailService.class);
        AuthProfileController controller = new AuthProfileController(profileService, thumbnailService);
        MultipartFile file = mock(MultipartFile.class);
        CourseThumbnailUploadResponse uploaded = new CourseThumbnailUploadResponse(
                "https://cdn.example.com/avatar.png", "avatars/id.png", "avatar.png", "image/png", 1024);
        UserProfileResponse profile = profile();
        when(thumbnailService.upload(file)).thenReturn(uploaded);
        when(profileService.updateCurrentUserProfile(
                new UpdateProfileRequest(null, uploaded.url(), null, null))).thenReturn(profile);

        ApiResponse<UserProfileResponse> response = controller.uploadAvatar(file);

        verify(thumbnailService).upload(file);
        verify(profileService).updateCurrentUserProfile(
                new UpdateProfileRequest(null, "https://cdn.example.com/avatar.png", null, null));
        assertThat(response.message()).isEqualTo("Avatar uploaded successfully");
        assertThat(response.data()).isSameAs(profile);
    }

    private AuthSessionService.IssuedSession issuedSession() {
        return new AuthSessionService.IssuedSession(
                new AuthSessionResponse("access-token", "Bearer", 900, profile()),
                "refresh-token");
    }

    private UserProfileResponse profile() {
        Instant now = Instant.now();
        return new UserProfileResponse(
                UUID.randomUUID(), "student@example.com", "Student", null, null, null,
                "TRAINEE", "active", true, now, now, now);
    }
}
