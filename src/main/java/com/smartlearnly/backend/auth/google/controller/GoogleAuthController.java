package com.smartlearnly.backend.auth.google.controller;

import com.smartlearnly.backend.admin.settings.service.SystemSettingsService;
import com.smartlearnly.backend.auth.google.dto.GoogleLoginRequest;
import com.smartlearnly.backend.auth.google.service.GoogleAuthService;
import com.smartlearnly.backend.auth.session.dto.AuthSessionResponse;
import com.smartlearnly.backend.auth.session.service.AuthSessionHttpSupport;
import com.smartlearnly.backend.auth.session.service.AuthSessionService;
import com.smartlearnly.backend.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Google authentication APIs.")
public class GoogleAuthController {
    private final GoogleAuthService googleAuthService;
    private final SystemSettingsService systemSettingsService;
    private final AuthSessionHttpSupport sessionHttpSupport;

    @GetMapping("/google/config")
    @Operation(summary = "Get the public Google OAuth client ID for the sign-in button")
    // Trả client ID công khai để frontend khởi tạo nút đăng nhập Google.
    public ApiResponse<GoogleConfigResponse> getGoogleConfig() {
        String clientId = systemSettingsService.resolveGoogleSettings().clientId();
        return ApiResponse.success(
                "Google config loaded",
                new GoogleConfigResponse(clientId == null ? "" : clientId)
        );
    }

    @PostMapping("/google")
    @Operation(summary = "Login with a Google Identity Services ID token")
    // Xác thực Google ID token và đặt refresh cookie giống luồng đăng nhập thường.
    public ResponseEntity<ApiResponse<AuthSessionResponse>> loginWithGoogle(
            @Valid @RequestBody GoogleLoginRequest request,
            HttpServletRequest httpRequest
    ) {
        AuthSessionService.IssuedSession session = googleAuthService.login(
                request,
                httpRequest.getHeader(HttpHeaders.USER_AGENT),
                sessionHttpSupport.clientIp(httpRequest)
        );
        return sessionHttpSupport.sessionResponse("Google login successful", session);
    }

    public record GoogleConfigResponse(String clientId) {
    }
}
