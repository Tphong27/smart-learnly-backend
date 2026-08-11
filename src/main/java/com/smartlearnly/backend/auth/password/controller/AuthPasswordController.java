package com.smartlearnly.backend.auth.password.controller;

import com.smartlearnly.backend.auth.password.dto.ChangePasswordRequest;
import com.smartlearnly.backend.auth.password.dto.ForgotPasswordRequest;
import com.smartlearnly.backend.auth.password.dto.ResetPasswordRequest;
import com.smartlearnly.backend.auth.password.service.AuthPasswordService;
import com.smartlearnly.backend.common.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthPasswordController {
    private final AuthPasswordService passwordService;

    @PostMapping("/forgot-password")
    // Tạo yêu cầu đặt lại mật khẩu nhưng luôn trả thông báo chung để chống dò email.
    public ApiResponse<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordService.forgotPassword(request);
        return ApiResponse.success(
                "If the account exists, password reset instructions have been generated. In development mode, the token is logged on the server."
        );
    }

    @PostMapping("/reset-password")
    // Đổi mật khẩu bằng reset token hợp lệ và thu hồi các session cũ.
    public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordService.resetPassword(request);
        return ApiResponse.success("Password has been reset successfully");
    }

    @PostMapping("/change-password")
    // Đổi mật khẩu của người dùng đã xác thực sau khi kiểm tra mật khẩu hiện tại.
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        passwordService.changeCurrentUserPassword(request);
        return ApiResponse.success("Password changed successfully");
    }
}
