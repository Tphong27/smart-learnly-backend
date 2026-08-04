package com.smartlearnly.backend.auth.registration.controller;

import com.smartlearnly.backend.auth.registration.dto.RegisterRequest;
import com.smartlearnly.backend.auth.registration.dto.ResendVerificationRequest;
import com.smartlearnly.backend.auth.registration.dto.VerifyEmailRequest;
import com.smartlearnly.backend.auth.registration.service.AuthRegistrationService;
import com.smartlearnly.backend.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Authentication", description = "Registration and email verification APIs.")
public class AuthRegistrationController {
    private final AuthRegistrationService registrationService;

    @PostMapping("/register")
    @Operation(summary = "Register a trainee account")
    // Tạo tài khoản trainee ở trạng thái chờ và phát OTP xác thực email.
    public ApiResponse<Void> register(@Valid @RequestBody RegisterRequest request) {
        registrationService.register(request);
        return ApiResponse.success(
                "Registration successful. Check your email to verify the account."
        );
    }

    @PostMapping("/verify-email")
    @Operation(summary = "Verify email using a six-digit OTP")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Email verified successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid input or token")
    })
    // Xác thực OTP và kích hoạt tài khoản đang chờ xác thực email.
    public ApiResponse<Void> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        registrationService.verifyEmail(request);
        return ApiResponse.success("Email has been verified successfully");
    }

    @PostMapping("/resend-verification")
    @Operation(
            summary = "Resend verification OTP",
            description = "Always returns a generic success message to avoid revealing whether the email exists."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Verification resend request accepted"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed")
    })
    // Gửi lại OTP theo rate limit mà không tiết lộ email có tồn tại hay không.
    public ApiResponse<Void> resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        registrationService.resendVerification(request);
        return ApiResponse.success(
                "If the account exists and is pending verification, a verification instruction has been generated. In development mode, the token is logged on the server."
        );
    }
}
