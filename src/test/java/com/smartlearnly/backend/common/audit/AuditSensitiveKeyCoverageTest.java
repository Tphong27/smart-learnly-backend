package com.smartlearnly.backend.common.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuditSensitiveKeyCoverageTest {
    private final AuditDataSanitizer sanitizer = new AuditDataSanitizer();

    @Test
    void sanitizerShouldRedactEveryCommittedSensitiveKey() {
        List<String> sensitiveKeys = List.of(
                "password",
                "passwordHash",
                "password_hash",
                "currentPassword",
                "newPassword",
                "confirmPassword",
                "token",
                "accessToken",
                "refreshToken",
                "passwordResetToken",
                "resetToken",
                "idToken",
                "otp",
                "otpCode",
                "secret",
                "webhookSecret",
                "paymentGatewaySecret",
                "clientSecret",
                "apiKey",
                "serviceRoleKey",
                "authorization",
                "cookie",
                "signature"
        );
        Map<String, Object> source = new LinkedHashMap<>();
        sensitiveKeys.forEach(key -> source.put(key, "sensitive"));

        Map<String, Object> sanitized = sanitizer.sanitizeMap(source);

        sensitiveKeys.forEach(key ->
                assertThat(sanitized.get(key))
                        .as("sensitive key %s", key)
                        .isEqualTo(AuditDataSanitizer.REDACTED_VALUE)
        );
    }
}
