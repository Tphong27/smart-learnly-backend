package com.smartlearnly.backend.auth.session.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.auth.config.AuthProperties;
import com.smartlearnly.backend.user.entity.UserAccount;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

class JwtTokenServiceTest {

    /**
     * Kịch bản: tạo access token cho một user đã đăng nhập thành công.
     * Given: user có ID/email/role và access-token TTL cấu hình là 15 phút.
     * When: JwtTokenService yêu cầu JwtEncoder ký claims.
     * Then: subject và user_id cùng trỏ đến user ID, email/roles đúng, issuedAt gần hiện tại,
     * expiresAt cách issuedAt đúng 15 phút và chuỗi token đã ký được trả nguyên vẹn.
     * Ý nghĩa bảo mật: authorization downstream dựa trên claims do backend tự tạo và encoder ký,
     * không dùng role hay user ID do frontend gửi lên.
     */
    @Test
    void createAccessTokenShouldSignExpectedIdentityRoleAndLifetimeClaims() {
        JwtEncoder jwtEncoder = mock(JwtEncoder.class);
        Jwt encodedJwt = mock(Jwt.class);
        when(jwtEncoder.encode(any(JwtEncoderParameters.class))).thenReturn(encodedJwt);
        when(encodedJwt.getTokenValue()).thenReturn("signed-access-token");
        AuthProperties properties = new AuthProperties();
        properties.setAccessTokenTtl(Duration.ofMinutes(15));
        JwtTokenService tokenService = new JwtTokenService(jwtEncoder, properties);
        UserAccount user = new UserAccount();
        user.setId(UUID.randomUUID());
        user.setEmail("student@example.com");
        user.setRole("TRAINEE");

        Instant beforeCreation = Instant.now();
        String token = tokenService.createAccessToken(user);

        assertThat(token).isEqualTo("signed-access-token");
        ArgumentCaptor<JwtEncoderParameters> parametersCaptor =
                ArgumentCaptor.forClass(JwtEncoderParameters.class);
        verify(jwtEncoder).encode(parametersCaptor.capture());
        JwtClaimsSet claims = parametersCaptor.getValue().getClaims();
        assertThat(claims.getSubject()).isEqualTo(user.getId().toString());
        assertThat(claims.getClaimAsString("user_id")).isEqualTo(user.getId().toString());
        assertThat(claims.getClaimAsString("email")).isEqualTo("student@example.com");
        assertThat(claims.getClaimAsStringList("roles")).isEqualTo(List.of("TRAINEE"));
        assertThat(claims.getIssuedAt()).isAfterOrEqualTo(beforeCreation);
        assertThat(claims.getExpiresAt()).isEqualTo(claims.getIssuedAt().plus(Duration.ofMinutes(15)));
    }
}
