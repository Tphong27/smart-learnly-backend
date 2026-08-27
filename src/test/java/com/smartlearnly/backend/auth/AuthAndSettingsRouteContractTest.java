package com.smartlearnly.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartlearnly.backend.admin.settings.controller.AiSettingsController;
import com.smartlearnly.backend.admin.settings.controller.EmailSettingsController;
import com.smartlearnly.backend.admin.settings.controller.GoogleSettingsController;
import com.smartlearnly.backend.admin.settings.controller.SePaySettingsController;
import com.smartlearnly.backend.auth.google.controller.GoogleAuthController;
import com.smartlearnly.backend.auth.password.controller.AuthPasswordController;
import com.smartlearnly.backend.auth.profile.controller.AuthProfileController;
import com.smartlearnly.backend.auth.register.controller.AuthRegistrationController;
import com.smartlearnly.backend.auth.session.controller.AuthSessionController;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

class AuthAndSettingsRouteContractTest {

    /**
     * Kịch bản: kiểm tra hồi quy toàn bộ public route của nhóm auth sau khi tổ chức lại package/controller.
     * Given: các controller registration, session, password, profile và Google hiện tại.
     * When: test đọc annotation mapping bằng reflection.
     * Then: base path và từng HTTP method/path phải giữ nguyên contract mà frontend đang sử dụng.
     * Ý nghĩa tương thích: refactor Java/package không được âm thầm làm hỏng API đăng ký, đăng nhập hay tài khoản.
     */
    @Test
    void authRoutesShouldKeepTheirPublicContract() {
        assertBasePath("/api/v1/auth",
                AuthSessionController.class,
                GoogleAuthController.class,
                AuthRegistrationController.class,
                AuthPasswordController.class,
                AuthProfileController.class);

        assertPost(AuthSessionController.class, "login", "/login");
        assertPost(AuthSessionController.class, "refresh", "/refresh");
        assertPost(AuthSessionController.class, "logout", "/logout");
        assertGet(GoogleAuthController.class, "getGoogleConfig", "/google/config");
        assertPost(GoogleAuthController.class, "loginWithGoogle", "/google");
        assertPost(AuthRegistrationController.class, "register", "/register");
        assertPost(AuthRegistrationController.class, "verifyEmail", "/verify-email");
        assertPost(AuthRegistrationController.class, "resendVerification", "/resend-verification");
        assertPost(AuthPasswordController.class, "forgotPassword", "/forgot-password");
        assertPost(AuthPasswordController.class, "resetPassword", "/reset-password");
        assertPost(AuthPasswordController.class, "changePassword", "/change-password");
        assertGet(AuthProfileController.class, "getProfile", "/profile");
        assertPatch(AuthProfileController.class, "updateProfile", "/profile");
        assertPost(AuthProfileController.class, "uploadAvatar", "/profile/avatar");
    }

    /**
     * Kịch bản: kiểm tra hồi quy các route quản trị cấu hình có liên quan đến auth/email/Google.
     * Given: các settings controller hiện tại.
     * When: test đọc mapping annotation bằng reflection.
     * Then: base path, HTTP verb và endpoint con phải đúng contract đã công bố.
     * Ý nghĩa tương thích: thay đổi cấu trúc code không được làm mất màn hình cấu hình dịch vụ đăng nhập/gửi email.
     */
    @Test
    void settingsRoutesShouldKeepTheirPublicContract() {
        assertBasePath("/api/v1/admin/settings",
                EmailSettingsController.class,
                GoogleSettingsController.class,
                AiSettingsController.class,
                SePaySettingsController.class);

        assertGet(EmailSettingsController.class, "getEmailSettings", "/email");
        assertPut(EmailSettingsController.class, "updateEmailSettings", "/email");
        assertPost(EmailSettingsController.class, "testEmail", "/email/test");
        assertGet(GoogleSettingsController.class, "getGoogleOAuth", "/oauth/google");
        assertPut(GoogleSettingsController.class, "updateGoogleOAuth", "/oauth/google");
        assertGet(GoogleSettingsController.class, "getGoogleMeetSettings", "/integrations/google-meet");
        assertPut(GoogleSettingsController.class, "updateGoogleMeetSettings", "/integrations/google-meet");
        assertGet(AiSettingsController.class, "getAssignmentAiSettings", "/ai/assignment-draft");
        assertPut(AiSettingsController.class, "updateAssignmentAiSettings", "/ai/assignment-draft");
        assertGet(SePaySettingsController.class, "getSePayBankDisplaySettings", "/integrations/sepay/bank-display");
        assertPut(SePaySettingsController.class, "updateSePayBankDisplaySettings", "/integrations/sepay/bank-display");
        assertGet(SePaySettingsController.class, "getSePayRuntimeSettings", "/integrations/sepay/runtime");
        assertPut(SePaySettingsController.class, "updateSePayRuntimeSettings", "/integrations/sepay/runtime");
        assertPost(
                SePaySettingsController.class,
                "runSePayReconciliationNow",
                "/integrations/sepay/reconciliation/run");
    }

    @SafeVarargs
    private void assertBasePath(String path, Class<?>... controllerTypes) {
        Arrays.stream(controllerTypes).forEach(controllerType -> {
            RequestMapping mapping = controllerType.getAnnotation(RequestMapping.class);
            assertThat(mapping).as(controllerType.getSimpleName()).isNotNull();
            assertThat(mapping.value()).containsExactly(path);
        });
    }

    private void assertGet(Class<?> controllerType, String methodName, String path) {
        GetMapping mapping = findMethod(controllerType, methodName).getAnnotation(GetMapping.class);
        assertThat(mapping).as(controllerType.getSimpleName() + "." + methodName).isNotNull();
        assertThat(mapping.value()).containsExactly(path);
    }

    private void assertPost(Class<?> controllerType, String methodName, String path) {
        PostMapping mapping = findMethod(controllerType, methodName).getAnnotation(PostMapping.class);
        assertThat(mapping).as(controllerType.getSimpleName() + "." + methodName).isNotNull();
        assertThat(mapping.value()).containsExactly(path);
    }

    private void assertPut(Class<?> controllerType, String methodName, String path) {
        PutMapping mapping = findMethod(controllerType, methodName).getAnnotation(PutMapping.class);
        assertThat(mapping).as(controllerType.getSimpleName() + "." + methodName).isNotNull();
        assertThat(mapping.value()).containsExactly(path);
    }

    private void assertPatch(Class<?> controllerType, String methodName, String path) {
        PatchMapping mapping = findMethod(controllerType, methodName).getAnnotation(PatchMapping.class);
        assertThat(mapping).as(controllerType.getSimpleName() + "." + methodName).isNotNull();
        assertThat(mapping.value()).containsExactly(path);
    }

    private Method findMethod(Class<?> controllerType, String methodName) {
        return Arrays.stream(controllerType.getDeclaredMethods())
                .filter(method -> method.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
    }
}
