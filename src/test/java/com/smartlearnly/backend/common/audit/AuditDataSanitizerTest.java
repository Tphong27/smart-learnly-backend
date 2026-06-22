package com.smartlearnly.backend.common.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuditDataSanitizerTest {
    private final AuditDataSanitizer sanitizer = new AuditDataSanitizer();

    @Test
    void sanitizeMapShouldRedactAllRequiredAliasesRecursively() {
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("password_hash", "hash");
        nested.put("access-token", "access");
        nested.put("safe", "visible");

        Map<String, Object> source = new LinkedHashMap<>();
        source.put("currentPassword", "old");
        source.put("OTP_CODE", "123456");
        source.put("headers", Map.of(
                "Authorization", "Bearer secret",
                "Cookie", "session=secret"
        ));
        source.put("items", List.of(
                nested,
                Map.of("serviceRoleKey", "service-role", "title", "Course")
        ));
        source.put("array", new Object[]{
                Map.of("signature", "sha256=secret"),
                "safe-value"
        });

        Map<String, Object> sanitized = sanitizer.sanitizeMap(source);

        assertThat(sanitized.get("currentPassword")).isEqualTo(AuditDataSanitizer.REDACTED_VALUE);
        assertThat(sanitized.get("OTP_CODE")).isEqualTo(AuditDataSanitizer.REDACTED_VALUE);

        @SuppressWarnings("unchecked")
        Map<String, Object> headers = (Map<String, Object>) sanitized.get("headers");
        assertThat(headers)
                .containsEntry("Authorization", AuditDataSanitizer.REDACTED_VALUE)
                .containsEntry("Cookie", AuditDataSanitizer.REDACTED_VALUE);

        @SuppressWarnings("unchecked")
        List<Object> items = (List<Object>) sanitized.get("items");
        assertThat(items).hasSize(2);
        @SuppressWarnings("unchecked")
        Map<String, Object> firstItem = (Map<String, Object>) items.get(0);
        @SuppressWarnings("unchecked")
        Map<String, Object> secondItem = (Map<String, Object>) items.get(1);
        assertThat(firstItem)
                .containsEntry("password_hash", AuditDataSanitizer.REDACTED_VALUE)
                .containsEntry("access-token", AuditDataSanitizer.REDACTED_VALUE)
                .containsEntry("safe", "visible");
        assertThat(secondItem)
                .containsEntry("serviceRoleKey", AuditDataSanitizer.REDACTED_VALUE)
                .containsEntry("title", "Course");

        @SuppressWarnings("unchecked")
        List<Object> array = (List<Object>) sanitized.get("array");
        @SuppressWarnings("unchecked")
        Map<String, Object> arrayItem = (Map<String, Object>) array.get(0);
        assertThat(arrayItem)
                .containsEntry("signature", AuditDataSanitizer.REDACTED_VALUE);
        assertThat(array.get(1)).isEqualTo("safe-value");
    }

    @Test
    void sanitizeMapShouldNotMutateCallerOwnedData() {
        List<Object> nestedList = new ArrayList<>();
        nestedList.add(new LinkedHashMap<>(Map.of("token", "raw-token")));
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("nested", nestedList);

        Map<String, Object> sanitized = sanitizer.sanitizeMap(source);

        assertThat(((Map<?, ?>) nestedList.get(0)).get("token")).isEqualTo("raw-token");
        @SuppressWarnings("unchecked")
        List<Object> sanitizedList = (List<Object>) sanitized.get("nested");
        assertThat(((Map<?, ?>) sanitizedList.get(0)).get("token"))
                .isEqualTo(AuditDataSanitizer.REDACTED_VALUE);
    }

    @Test
    void sanitizeShouldHandleNullAndSafeScalars() {
        assertThat(sanitizer.sanitizeMap(null)).isNull();
        assertThat(sanitizer.sanitize(null)).isNull();
        assertThat(sanitizer.sanitize("safe")).isEqualTo("safe");
        assertThat(sanitizer.sanitize(42)).isEqualTo(42);
        assertThat(sanitizer.sanitize(true)).isEqualTo(true);
    }
}

