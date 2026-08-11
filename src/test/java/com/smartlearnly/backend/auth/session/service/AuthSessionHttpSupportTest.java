package com.smartlearnly.backend.auth.session.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.smartlearnly.backend.auth.config.AuthProperties;
import com.smartlearnly.backend.auth.session.dto.AuthSessionResponse;
import com.smartlearnly.backend.common.api.ApiResponse;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

class AuthSessionHttpSupportTest {
    private AuthSessionHttpSupport httpSupport;

    @BeforeEach
    void setUp() {
        AuthProperties properties = new AuthProperties();
        properties.setRefreshTokenTtl(Duration.ofDays(7));
        properties.setRefreshCookieSecure(true);
        httpSupport = new AuthSessionHttpSupport(properties);
    }

    /**
     * Kịch bản: controller trả session mới sau login/refresh thành công.
     * Given: IssuedSession chứa response công khai và refresh token rõ.
     * When: sessionResponse đóng gói HTTP response.
     * Then: status 200, body giữ đúng message/data và Set-Cookie có HttpOnly, Secure, SameSite=Lax,
     * path giới hạn trong /api/v1/auth cùng Max-Age bảy ngày.
     * Ý nghĩa bảo mật: JavaScript không đọc được refresh token và cookie chỉ được gửi về nhóm endpoint auth.
     */
    @Test
    void sessionResponseShouldSetSecureHttpOnlyRefreshCookieAndReturnSessionBody() {
        AuthSessionResponse sessionBody = mock(AuthSessionResponse.class);
        AuthSessionService.IssuedSession issuedSession =
                new AuthSessionService.IssuedSession(sessionBody, "raw-refresh-token");

        ResponseEntity<ApiResponse<AuthSessionResponse>> response =
                httpSupport.sessionResponse("Login successful", issuedSession);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getBody().message()).isEqualTo("Login successful");
        assertThat(response.getBody().data()).isSameAs(sessionBody);
        assertThat(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE))
                .contains("slp_refresh_token=raw-refresh-token")
                .contains("Path=/api/v1/auth")
                .contains("Max-Age=604800")
                .contains("Secure")
                .contains("HttpOnly")
                .contains("SameSite=Lax");
    }

    /**
     * Kịch bản: logout thành công cần xóa cookie trình duyệt.
     * Given: cookie refresh cũ có thể vẫn tồn tại phía client.
     * When: logoutResponse được tạo.
     * Then: body báo thành công và Set-Cookie ghi cùng tên/path với value rỗng, Max-Age=0,
     * vẫn giữ các thuộc tính HttpOnly/Secure/SameSite nhất quán.
     * Ý nghĩa bảo mật: browser xóa đúng cookie credential thay vì chỉ server thu hồi token.
     */
    @Test
    void logoutResponseShouldExpireTheExactRefreshCookie() {
        ResponseEntity<ApiResponse<Void>> response = httpSupport.logoutResponse();

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getBody().message()).isEqualTo("Logout successful");
        assertThat(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE))
                .contains("slp_refresh_token=")
                .contains("Path=/api/v1/auth")
                .contains("Max-Age=0")
                .contains("Secure")
                .contains("HttpOnly")
                .contains("SameSite=Lax");
    }

    /**
     * Kịch bản: request chứa nhiều cookie và refresh cookie hợp lệ không đứng đầu.
     * Given: một cookie không liên quan đứng trước slp_refresh_token có value không rỗng.
     * When: requireRefreshToken quét danh sách cookie.
     * Then: trả đúng value của refresh cookie, không nhầm với cookie khác.
     * Ý nghĩa bảo mật: chỉ cookie có tên contract chính xác mới được dùng làm credential.
     */
    @Test
    void requireRefreshTokenShouldReturnNamedCookieValue() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("theme", "dark"), new Cookie("slp_refresh_token", "refresh-value"));

        assertThat(httpSupport.requireRefreshToken(request)).isEqualTo("refresh-value");
    }

    /**
     * Kịch bản: endpoint refresh không nhận được refresh cookie sử dụng được.
     * Given: request không có cookie hoặc refresh cookie chỉ chứa whitespace.
     * When: requireRefreshToken được gọi.
     * Then: trả INVALID_OR_EXPIRED_TOKEN với thông báo contract thống nhất.
     * Ý nghĩa bảo mật: không cho phép refresh session nếu thiếu credential server đã phát.
     */
    @Test
    void requireRefreshTokenShouldRejectMissingOrBlankCookie() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("slp_refresh_token", "   "));

        assertThatThrownBy(() -> httpSupport.requireRefreshToken(request))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_OR_EXPIRED_TOKEN))
                .hasMessage("Refresh token cookie is missing");
    }

    /**
     * Kịch bản: logout đọc refresh token theo chế độ tùy chọn.
     * Given: request không có mảng cookie.
     * When: optionalRefreshToken được gọi.
     * Then: trả null thay vì ném lỗi.
     * Ý nghĩa UX/bảo mật: logout giữ tính idempotent khi cookie đã hết hạn hoặc đã bị xóa.
     */
    @Test
    void optionalRefreshTokenShouldReturnNullWhenRequestHasNoCookies() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertThat(httpSupport.optionalRefreshToken(request)).isNull();
    }

    /**
     * Kịch bản: request đi qua reverse proxy có chuỗi X-Forwarded-For nhiều hop.
     * Given: header chứa IP client đầu tiên, sau đó là hai proxy.
     * When: clientIp được tính.
     * Then: chỉ IP đầu tiên đã trim được trả về.
     * Ý nghĩa audit: lịch sử đăng nhập gắn với địa chỉ client gốc thay vì proxy gần backend nhất.
     */
    @Test
    void clientIpShouldUseFirstForwardedAddress() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", " 203.0.113.10, 10.0.0.2, 10.0.0.3 ");
        request.setRemoteAddr("127.0.0.1");

        assertThat(httpSupport.clientIp(request)).isEqualTo("203.0.113.10");
    }

    /**
     * Kịch bản: backend được gọi trực tiếp, không có X-Forwarded-For hữu ích.
     * Given: header chỉ là whitespace và remoteAddr có giá trị.
     * When: clientIp được tính.
     * Then: dùng địa chỉ kết nối trực tiếp làm fallback.
     * Ý nghĩa ổn định: audit vẫn có IP trong môi trường local hoặc deployment không dùng proxy.
     */
    @Test
    void clientIpShouldFallbackToRemoteAddressWhenForwardedHeaderIsBlank() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "   ");
        request.setRemoteAddr("127.0.0.1");

        assertThat(httpSupport.clientIp(request)).isEqualTo("127.0.0.1");
    }
}
