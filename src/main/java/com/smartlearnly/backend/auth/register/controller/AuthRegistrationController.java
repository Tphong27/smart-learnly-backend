package com.smartlearnly.backend.auth.register.controller;

import com.smartlearnly.backend.auth.register.dto.RegisterRequest;
import com.smartlearnly.backend.auth.register.dto.ResendVerificationRequest;
import com.smartlearnly.backend.auth.register.dto.VerifyEmailRequest;
import com.smartlearnly.backend.auth.register.service.AuthRegistrationService;
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
public class AuthRegistrationController {
    private final AuthRegistrationService registrationService;

    @PostMapping("/register")
    // Tạo tài khoản trainee ở trạng thái chờ và phát OTP xác thực email.
    public ApiResponse<Void> register(@Valid @RequestBody RegisterRequest request) {
        registrationService.register(request);
        return ApiResponse.success(
                "Registration successful. Check your email to verify the account."
        );
    }

    @PostMapping("/verify-email")
    // Xác thực OTP và kích hoạt tài khoản đang chờ xác thực email.
    public ApiResponse<Void> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        registrationService.verifyEmail(request);
        return ApiResponse.success("Email has been verified successfully");
    }

    @PostMapping("/resend-verification")
    // Gửi lại OTP theo rate limit mà không tiết lộ email có tồn tại hay không.
    public ApiResponse<Void> resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        registrationService.resendVerification(request);
        return ApiResponse.success(
                "If the account exists and is pending verification, a verification instruction has been generated. In development mode, the token is logged on the server."
        );
    }
}
