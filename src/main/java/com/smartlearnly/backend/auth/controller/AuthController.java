package com.smartlearnly.backend.auth.controller;

import com.smartlearnly.backend.auth.dto.ChangePasswordRequest;
import com.smartlearnly.backend.auth.dto.ForgotPasswordRequest;
import com.smartlearnly.backend.auth.dto.AuthSessionResponse;
import com.smartlearnly.backend.auth.dto.LoginRequest;
import com.smartlearnly.backend.auth.dto.GoogleLoginRequest;
import com.smartlearnly.backend.auth.dto.RegisterRequest;
import com.smartlearnly.backend.auth.dto.ResetPasswordRequest;
import com.smartlearnly.backend.auth.dto.ResendVerificationRequest;
import com.smartlearnly.backend.auth.dto.UpdateProfileRequest;
import com.smartlearnly.backend.auth.dto.UserProfileResponse;
import com.smartlearnly.backend.auth.dto.VerifyEmailRequest;
import com.smartlearnly.backend.auth.service.AuthService;
import com.smartlearnly.backend.auth.service.AuthSessionService;
import com.smartlearnly.backend.auth.config.AuthProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Authentication, email verification, password recovery, and current-user profile APIs.")
public class AuthController {
    private final AuthService authService;
    private final AuthProperties authProperties;

    @PostMapping("/register")
    @Operation(summary = "Register a trainee account")
    public com.smartlearnly.backend.common.api.ApiResponse<Void> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return com.smartlearnly.backend.common.api.ApiResponse.success(
                "Registration successful. Check your email to verify the account."
        );
    }

    @PostMapping("/login")
    @Operation(summary = "Login with email and password")
    public ResponseEntity<com.smartlearnly.backend.common.api.ApiResponse<AuthSessionResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest
    ) {
        AuthSessionService.IssuedSession session = authService.login(
                request,
                httpRequest.getHeader(HttpHeaders.USER_AGENT),
                clientIp(httpRequest)
        );
        return sessionResponse("Login successful", session);
    }

    @PostMapping("/google")
    @Operation(summary = "Login with a Google Identity Services ID token")
    public ResponseEntity<com.smartlearnly.backend.common.api.ApiResponse<AuthSessionResponse>> loginWithGoogle(
            @Valid @RequestBody GoogleLoginRequest request,
            HttpServletRequest httpRequest
    ) {
        AuthSessionService.IssuedSession session = authService.loginWithGoogle(
                request,
                httpRequest.getHeader(HttpHeaders.USER_AGENT),
                clientIp(httpRequest)
        );
        return sessionResponse("Google login successful", session);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate refresh token and issue a new access token")
    public ResponseEntity<com.smartlearnly.backend.common.api.ApiResponse<AuthSessionResponse>> refresh(
            HttpServletRequest httpRequest
    ) {
        AuthSessionService.IssuedSession session = authService.refresh(
                refreshToken(httpRequest),
                httpRequest.getHeader(HttpHeaders.USER_AGENT),
                clientIp(httpRequest)
        );
        return sessionResponse("Session refreshed", session);
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoke the current refresh token")
    public ResponseEntity<com.smartlearnly.backend.common.api.ApiResponse<Void>> logout(HttpServletRequest request) {
        authService.logout(refreshTokenOrNull(request));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, expiredRefreshCookie().toString())
                .body(com.smartlearnly.backend.common.api.ApiResponse.success("Logout successful"));
    }

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
    @Operation(summary = "Verify email using a six-digit OTP")
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
            summary = "Resend verification OTP",
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
    @SecurityRequirements({
            @SecurityRequirement(name = "basicAuth"),
            @SecurityRequirement(name = "bearerAuth")
    })
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profile loaded successfully"),
            @ApiResponse(responseCode = "401", description = "Authentication required")
    })
    public com.smartlearnly.backend.common.api.ApiResponse<UserProfileResponse> getProfile() {
        return com.smartlearnly.backend.common.api.ApiResponse.success("Profile loaded successfully", authService.getCurrentUserProfile());
    }

    @PatchMapping("/profile")
    @Operation(summary = "Update current user profile")
    @SecurityRequirements({
            @SecurityRequirement(name = "basicAuth"),
            @SecurityRequirement(name = "bearerAuth")
    })
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
    @SecurityRequirements({
            @SecurityRequirement(name = "basicAuth"),
            @SecurityRequirement(name = "bearerAuth")
    })
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

    private ResponseEntity<com.smartlearnly.backend.common.api.ApiResponse<AuthSessionResponse>> sessionResponse(
            String message,
            AuthSessionService.IssuedSession session
    ) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(session.refreshToken()).toString())
                .body(com.smartlearnly.backend.common.api.ApiResponse.success(message, session.response()));
    }

    private ResponseCookie refreshCookie(String value) {
        return ResponseCookie.from("slp_refresh_token", value)
                .httpOnly(true)
                .secure(authProperties.isRefreshCookieSecure())
                .sameSite("Lax")
                .path("/api/v1/auth")
                .maxAge(authProperties.getRefreshTokenTtl())
                .build();
    }

    private ResponseCookie expiredRefreshCookie() {
        return ResponseCookie.from("slp_refresh_token", "")
                .httpOnly(true)
                .secure(authProperties.isRefreshCookieSecure())
                .sameSite("Lax")
                .path("/api/v1/auth")
                .maxAge(0)
                .build();
    }

    private String refreshToken(HttpServletRequest request) {
        String refreshToken = refreshTokenOrNull(request);
        if (refreshToken != null) {
            return refreshToken;
        }
        throw new com.smartlearnly.backend.common.exception.BusinessException(
                com.smartlearnly.backend.common.exception.ErrorCode.INVALID_OR_EXPIRED_TOKEN,
                "Refresh token cookie is missing"
        );
    }

    private String refreshTokenOrNull(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("slp_refresh_token".equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
