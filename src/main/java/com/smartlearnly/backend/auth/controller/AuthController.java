package com.smartlearnly.backend.auth.controller;

import com.smartlearnly.backend.auth.dto.ChangePasswordRequest;
import com.smartlearnly.backend.auth.dto.ForgotPasswordRequest;
import com.smartlearnly.backend.auth.dto.ResetPasswordRequest;
import com.smartlearnly.backend.auth.dto.ResendVerificationRequest;
import com.smartlearnly.backend.auth.dto.UpdateProfileRequest;
import com.smartlearnly.backend.auth.dto.UserProfileResponse;
import com.smartlearnly.backend.auth.dto.VerifyEmailRequest;
import com.smartlearnly.backend.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Authentication, email verification, password recovery, and current-user profile APIs.")
public class AuthController {
    private final AuthService authService;

    @PostMapping("/forgot-password")
    @Operation(
            summary = "Request password reset",
            description = "Always returns a generic success message to avoid revealing whether the email exists."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Password reset request accepted"),
            @ApiResponse(responseCode = "400", description = "Validation failed")
    })
    public com.smartlearnly.backend.common.api.ApiResponse<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return com.smartlearnly.backend.common.api.ApiResponse.success(
                "If the account exists, password reset instructions have been generated. In development mode, the token is logged on the server."
        );
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Reset password using reset token")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Password reset successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input or token"),
            @ApiResponse(responseCode = "422", description = "Business rule violation")
    })
    public com.smartlearnly.backend.common.api.ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return com.smartlearnly.backend.common.api.ApiResponse.success("Password has been reset successfully");
    }

    @PostMapping("/verify-email")
    @Operation(summary = "Verify email using verification token")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Email verified successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input or token")
    })
    public com.smartlearnly.backend.common.api.ApiResponse<Void> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        authService.verifyEmail(request);
        return com.smartlearnly.backend.common.api.ApiResponse.success("Email has been verified successfully");
    }

    @PostMapping("/resend-verification")
    @Operation(
            summary = "Resend verification token",
            description = "Always returns a generic success message to avoid revealing whether the email exists."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Verification resend request accepted"),
            @ApiResponse(responseCode = "400", description = "Validation failed")
    })
    public com.smartlearnly.backend.common.api.ApiResponse<Void> resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        authService.resendVerification(request);
        return com.smartlearnly.backend.common.api.ApiResponse.success(
                "If the account exists and is pending verification, a verification instruction has been generated. In development mode, the token is logged on the server."
        );
    }

    @GetMapping("/profile")
    @Operation(summary = "Get current user profile")
    @SecurityRequirement(name = "basicAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profile loaded successfully"),
            @ApiResponse(responseCode = "401", description = "Authentication required")
    })
    public com.smartlearnly.backend.common.api.ApiResponse<UserProfileResponse> getProfile() {
        return com.smartlearnly.backend.common.api.ApiResponse.success("Profile loaded successfully", authService.getCurrentUserProfile());
    }

    @PatchMapping("/profile")
    @Operation(summary = "Update current user profile")
    @SecurityRequirement(name = "basicAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profile updated successfully"),
            @ApiResponse(responseCode = "400", description = "Validation failed or empty update payload"),
            @ApiResponse(responseCode = "401", description = "Authentication required")
    })
    public com.smartlearnly.backend.common.api.ApiResponse<UserProfileResponse> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        return com.smartlearnly.backend.common.api.ApiResponse.success("Profile updated successfully", authService.updateCurrentUserProfile(request));
    }

    @PostMapping("/change-password")
    @Operation(summary = "Change current user password")
    @SecurityRequirement(name = "basicAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Password changed successfully"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "401", description = "Authentication required or invalid current password"),
            @ApiResponse(responseCode = "422", description = "Business rule violation")
    })
    public com.smartlearnly.backend.common.api.ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changeCurrentUserPassword(request);
        return com.smartlearnly.backend.common.api.ApiResponse.success("Password changed successfully");
    }
}
