package com.smartlearnly.backend.auth.google.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.admin.settings.service.SystemSettingsService;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;

@ExtendWith(MockitoExtension.class)
class GoogleIdTokenServiceTest {
    @Mock
    private SystemSettingsService settingsService;
    @Mock
    private JwtDecoder jwtDecoder;

    private GoogleIdTokenService tokenService;

    @BeforeEach
    void setUp() {
        tokenService = new GoogleIdTokenService(settingsService);
        ReflectionTestUtils.setField(tokenService, "decoder", jwtDecoder);
    }

    /**
     * Kịch bản: quản trị chưa cấu hình Google OAuth client ID.
     * Given: effective Google settings trả clientId chỉ có whitespace.
     * When: verify nhận một ID token.
     * Then: trả EXTERNAL_SERVICE_UNAVAILABLE trước khi gọi decoder/JWK endpoint.
     * Ý nghĩa bảo mật/vận hành: lỗi cấu hình server được phân biệt với credential người dùng sai
     * và không tạo network call vô ích.
     */
    @Test
    void verifyShouldRejectWhenGoogleLoginIsNotConfigured() {
        when(settingsService.resolveGoogleSettings()).thenReturn(googleSettings("   "));

        assertThatThrownBy(() -> tokenService.verify("google-id-token"))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE))
                .hasMessage("Google login is not configured");

        verifyNoInteractions(jwtDecoder);
    }

    /**
     * Kịch bản: Google ID token hợp lệ cho đúng OAuth client của hệ thống.
     * Given: decoder đã xác minh chữ ký/issuer/time và JWT có audience đúng, email không null,
     * email_verified=true cùng subject/name/picture.
     * When: verify hoàn tất kiểm tra business claims.
     * Then: trả GoogleIdentity chứa chính xác bốn claim cần cho bước link/create user.
     * Ý nghĩa bảo mật: chỉ identity đã qua cả cryptographic validation và audience/email validation được tin cậy.
     */
    @Test
    void verifyShouldReturnIdentityForValidGoogleToken() {
        when(settingsService.resolveGoogleSettings()).thenReturn(googleSettings("smart-learnly-client"));
        Jwt jwt = googleJwt(List.of("smart-learnly-client"), "student@example.com", true);
        when(jwtDecoder.decode("google-id-token")).thenReturn(jwt);

        GoogleIdTokenService.GoogleIdentity identity = tokenService.verify("google-id-token");

        assertThat(identity.subject()).isEqualTo("google-subject");
        assertThat(identity.email()).isEqualTo("student@example.com");
        assertThat(identity.fullName()).isEqualTo("Student Name");
        assertThat(identity.avatarUrl()).isEqualTo("https://example.com/avatar.png");
    }

    /**
     * Kịch bản: token hợp lệ về chữ ký nhưng được cấp cho ứng dụng OAuth khác.
     * Given: audience không chứa client ID Smart Learnly.
     * When: verify kiểm tra audience.
     * Then: chuẩn hóa thành INVALID_CREDENTIALS với message chung về Google ID token.
     * Ý nghĩa bảo mật: token lấy từ một Google client khác không thể dùng để đăng nhập vào hệ thống này.
     */
    @Test
    void verifyShouldRejectTokenIssuedForDifferentAudience() {
        when(settingsService.resolveGoogleSettings()).thenReturn(googleSettings("smart-learnly-client"));
        Jwt jwt = googleJwt(List.of("another-client"), "student@example.com", true);
        when(jwtDecoder.decode("google-id-token")).thenReturn(jwt);

        assertInvalidGoogleToken(() -> tokenService.verify("google-id-token"));
    }

    /**
     * Kịch bản: token có email nhưng Google chưa xác minh quyền sở hữu email đó.
     * Given: audience đúng nhưng email_verified=false.
     * When: verify kiểm tra identity claims.
     * Then: trả INVALID_CREDENTIALS và không tạo GoogleIdentity.
     * Ý nghĩa bảo mật: không liên kết/kích hoạt tài khoản nội bộ bằng email chưa được nhà cung cấp xác minh.
     */
    @Test
    void verifyShouldRejectGoogleEmailThatIsNotVerified() {
        when(settingsService.resolveGoogleSettings()).thenReturn(googleSettings("smart-learnly-client"));
        Jwt jwt = googleJwt(List.of("smart-learnly-client"), "student@example.com", false);
        when(jwtDecoder.decode("google-id-token")).thenReturn(jwt);

        assertInvalidGoogleToken(() -> tokenService.verify("google-id-token"));
    }

    /**
     * Kịch bản: token thiếu claim email bắt buộc.
     * Given: audience đúng và email_verified=true nhưng email=null.
     * When: verify kiểm tra identity claims.
     * Then: trả cùng INVALID_CREDENTIALS như các token không hợp lệ khác.
     * Ý nghĩa bảo mật: hệ thống không tạo user không có định danh email và không lộ claim nào bị sai.
     */
    @Test
    void verifyShouldRejectTokenWithoutEmailClaim() {
        when(settingsService.resolveGoogleSettings()).thenReturn(googleSettings("smart-learnly-client"));
        Jwt jwt = googleJwt(List.of("smart-learnly-client"), null, true);
        when(jwtDecoder.decode("google-id-token")).thenReturn(jwt);

        assertInvalidGoogleToken(() -> tokenService.verify("google-id-token"));
    }

    /**
     * Kịch bản: decoder từ chối chữ ký, issuer hoặc thời hạn JWT.
     * Given: JwtDecoder ném JwtException.
     * When: verify xử lý lỗi thư viện OAuth/JWT.
     * Then: không chuyển chi tiết decoder ra ngoài mà trả INVALID_CREDENTIALS/message chung.
     * Ý nghĩa bảo mật: client không nhận thông tin nội bộ có thể hỗ trợ dò cấu hình xác minh token.
     */
    @Test
    void verifyShouldNormalizeJwtValidationFailureToInvalidCredentials() {
        when(settingsService.resolveGoogleSettings()).thenReturn(googleSettings("smart-learnly-client"));
        when(jwtDecoder.decode("bad-token")).thenThrow(new JwtException("invalid signature"));

        assertInvalidGoogleToken(() -> tokenService.verify("bad-token"));
    }

    /**
     * Kịch bản: dịch vụ/JWK Google tạm thời không truy cập được.
     * Given: decoder ném RestClientException thay vì JwtException.
     * When: verify phân loại lỗi hạ tầng bên ngoài.
     * Then: trả EXTERNAL_SERVICE_UNAVAILABLE với thông báo có thể retry, không gắn nhãn sai credential.
     * Ý nghĩa vận hành: frontend và giám sát có thể phân biệt outage với đăng nhập sai của người dùng.
     */
    @Test
    void verifyShouldReportTemporaryGoogleVerificationOutage() {
        when(settingsService.resolveGoogleSettings()).thenReturn(googleSettings("smart-learnly-client"));
        when(jwtDecoder.decode("google-id-token")).thenThrow(new RestClientException("JWK endpoint unavailable"));

        assertThatThrownBy(() -> tokenService.verify("google-id-token"))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE))
                .hasMessage("Google token verification is temporarily unavailable");
    }

    private SystemSettingsService.GoogleOAuthSettings googleSettings(String clientId) {
        return new SystemSettingsService.GoogleOAuthSettings(clientId, "client-secret", "openid,profile,email");
    }

    private Jwt googleJwt(List<String> audience, String email, boolean emailVerified) {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getAudience()).thenReturn(audience);
        lenient().when(jwt.getSubject()).thenReturn("google-subject");
        lenient().when(jwt.getClaimAsString("email")).thenReturn(email);
        lenient().when(jwt.getClaim("email_verified")).thenReturn(emailVerified);
        lenient().when(jwt.getClaimAsString("name")).thenReturn("Student Name");
        lenient().when(jwt.getClaimAsString("picture")).thenReturn("https://example.com/avatar.png");
        return jwt;
    }

    private void assertInvalidGoogleToken(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        assertThatThrownBy(callable)
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS))
                .hasMessage("Google ID token is invalid");
    }
}
