package com.smartlearnly.backend.auth.session.service;

import com.smartlearnly.backend.auth.config.AuthProperties;
import com.smartlearnly.backend.user.entity.UserAccount;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class JwtTokenService {
    private final JwtEncoder jwtEncoder;
    private final AuthProperties authProperties;

    // Tạo access token chứa định danh và role hiện tại của người dùng.
    public String createAccessToken(UserAccount user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuedAt(now)
                .expiresAt(now.plus(authProperties.getAccessTokenTtl()))
                .subject(user.getId().toString())
                .claim("user_id", user.getId().toString())
                .claim("email", user.getEmail())
                .claim("roles", List.of(user.getRole()))
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }
}
