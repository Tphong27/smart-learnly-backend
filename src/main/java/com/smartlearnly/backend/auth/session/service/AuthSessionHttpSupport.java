package com.smartlearnly.backend.auth.session.service;

import com.smartlearnly.backend.auth.config.AuthProperties;
import com.smartlearnly.backend.auth.session.dto.AuthSessionResponse;
import com.smartlearnly.backend.common.api.ApiResponse;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthSessionHttpSupport {
    private static final String REFRESH_COOKIE_NAME = "slp_refresh_token";

    private final AuthProperties authProperties;

    // Trả session thành công và đặt refresh token trong cookie HttpOnly theo cấu hình hiện tại.
    public ResponseEntity<ApiResponse<AuthSessionResponse>> sessionResponse(
            String message,
            AuthSessionService.IssuedSession session
    ) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(session.refreshToken()).toString())
                .body(ApiResponse.success(message, session.response()));
    }

    // Trả logout thành công và xóa refresh cookie trên trình duyệt.
    public ResponseEntity<ApiResponse<Void>> logoutResponse() {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, expiredRefreshCookie().toString())
                .body(ApiResponse.success("Logout successful"));
    }

    // Đọc refresh token bắt buộc từ cookie hoặc báo lỗi token không hợp lệ.
    public String requireRefreshToken(HttpServletRequest request) {
        String refreshToken = optionalRefreshToken(request);
        if (refreshToken != null) {
            return refreshToken;
        }
        throw new BusinessException(
                ErrorCode.INVALID_OR_EXPIRED_TOKEN,
                "Refresh token cookie is missing"
        );
    }

    // Đọc refresh token tùy chọn để logout vẫn idempotent khi cookie đã mất.
    public String optionalRefreshToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (REFRESH_COOKIE_NAME.equals(cookie.getName())
                        && cookie.getValue() != null
                        && !cookie.getValue().isBlank()) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    // Lấy IP gốc đầu tiên qua reverse proxy hoặc dùng địa chỉ kết nối trực tiếp.
    public String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    // Tạo refresh cookie có thời hạn, scope và thuộc tính bảo mật thống nhất cho mọi cách đăng nhập.
    private ResponseCookie refreshCookie(String value) {
        return ResponseCookie.from(REFRESH_COOKIE_NAME, value)
                .httpOnly(true)
                .secure(authProperties.isRefreshCookieSecure())
                .sameSite("Lax")
                .path("/api/v1/auth")
                .maxAge(authProperties.getRefreshTokenTtl())
                .build();
    }

    // Tạo cookie hết hạn ngay để trình duyệt xóa đúng refresh cookie cũ.
    private ResponseCookie expiredRefreshCookie() {
        return ResponseCookie.from(REFRESH_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(authProperties.isRefreshCookieSecure())
                .sameSite("Lax")
                .path("/api/v1/auth")
                .maxAge(0)
                .build();
    }
}
