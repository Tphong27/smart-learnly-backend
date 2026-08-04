package com.smartlearnly.backend.auth.session.controller;

import com.smartlearnly.backend.auth.login.service.AuthLoginService;
import com.smartlearnly.backend.auth.login.dto.LoginRequest;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Authentication session APIs.")
public class AuthSessionController {
    private final AuthLoginService loginService;
    private final AuthSessionService sessionService;
    private final AuthSessionHttpSupport sessionHttpSupport;

    @PostMapping("/login")
    @Operation(summary = "Login with email and password")
    // Xác thực email/mật khẩu và đặt refresh cookie cho session mới.
    public ResponseEntity<ApiResponse<AuthSessionResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest
    ) {
        AuthSessionService.IssuedSession session = loginService.login(
                request,
                httpRequest.getHeader(HttpHeaders.USER_AGENT),
                sessionHttpSupport.clientIp(httpRequest)
        );
        return sessionHttpSupport.sessionResponse("Login successful", session);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate refresh token and issue a new access token")
    // Xoay refresh token hiện tại và trả access token mới trong cùng cookie contract.
    public ResponseEntity<ApiResponse<AuthSessionResponse>> refresh(HttpServletRequest httpRequest) {
        AuthSessionService.IssuedSession session = sessionService.rotate(
                sessionHttpSupport.requireRefreshToken(httpRequest),
                httpRequest.getHeader(HttpHeaders.USER_AGENT),
                sessionHttpSupport.clientIp(httpRequest)
        );
        return sessionHttpSupport.sessionResponse("Session refreshed", session);
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoke the current refresh token")
    // Thu hồi refresh token nếu có và luôn xóa cookie phía trình duyệt.
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest request) {
        sessionService.logout(sessionHttpSupport.optionalRefreshToken(request));
        return sessionHttpSupport.logoutResponse();
    }
}
