package com.smartlearnly.backend.auth.service;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

@Service
public class GoogleIdTokenService {
    private final JwtDecoder decoder = googleDecoder();
    private final String clientId;

    public GoogleIdTokenService(@Value("${app.auth.google-client-id:}") String clientId) {
        this.clientId = clientId;
    }

    public GoogleIdentity verify(String idToken) {
        if (clientId == null || clientId.isBlank()) {
            throw new BusinessException(
                    ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE,
                    "Google login is not configured"
            );
        }

        try {
            Jwt jwt = decoder.decode(idToken);
            List<String> audience = jwt.getAudience();
            String email = jwt.getClaimAsString("email");
            Boolean emailVerified = jwt.getClaim("email_verified");
            if (!audience.contains(clientId) || email == null || !Boolean.TRUE.equals(emailVerified)) {
                throw invalidToken();
            }
            return new GoogleIdentity(
                    jwt.getSubject(),
                    email,
                    jwt.getClaimAsString("name"),
                    jwt.getClaimAsString("picture")
            );
        } catch (JwtException exception) {
            throw invalidToken();
        } catch (RestClientException exception) {
            throw new BusinessException(
                    ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE,
                    "Google token verification is temporarily unavailable"
            );
        }
    }

    private BusinessException invalidToken() {
        return new BusinessException(ErrorCode.INVALID_CREDENTIALS, "Google ID token is invalid");
    }

    private JwtDecoder googleDecoder() {
        NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withJwkSetUri(
                "https://www.googleapis.com/oauth2/v3/certs"
        ).build();
        OAuth2TokenValidator<Jwt> issuerValidator = jwt -> {
            String issuer = jwt.getIssuer() == null ? null : jwt.getIssuer().toString();
            if ("https://accounts.google.com".equals(issuer) || "accounts.google.com".equals(issuer)) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid Google issuer", null));
        };
        jwtDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(new JwtTimestampValidator(), issuerValidator));
        return jwtDecoder;
    }

    public record GoogleIdentity(String subject, String email, String fullName, String avatarUrl) {
    }
}
