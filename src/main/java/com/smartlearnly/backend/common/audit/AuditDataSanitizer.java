package com.smartlearnly.backend.common.audit;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class AuditDataSanitizer {
    public static final String REDACTED_VALUE = "***REDACTED***";

    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "password",
            "passwordhash",
            "currentpassword",
            "newpassword",
            "confirmpassword",
            "token",
            "accesstoken",
            "refreshtoken",
            "passwordresettoken",
            "resettoken",
            "idtoken",
            "otp",
            "otpcode",
            "secret",
            "webhooksecret",
            "paymentgatewaysecret",
            "clientsecret",
            "apikey",
            "servicerolekey",
            "authorization",
            "cookie",
            "signature"
    );

    public Map<String, Object> sanitizeMap(Map<String, ?> source) {
        if (source == null) {
            return null;
        }
        Map<String, Object> sanitized = new LinkedHashMap<>();
        source.forEach((key, value) -> sanitized.put(
                key,
                isSensitiveKey(key) ? REDACTED_VALUE : sanitize(value)
        ));
        return sanitized;
    }

    public Object sanitize(Object value) {
        if (value == null || isSafeScalar(value)) {
            return value;
        }
        if (value instanceof Map<?, ?> map) {
            return sanitizeUntypedMap(map);
        }
        if (value instanceof Collection<?> collection) {
            List<Object> sanitized = new ArrayList<>(collection.size());
            collection.forEach(item -> sanitized.add(sanitize(item)));
            return sanitized;
        }
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            List<Object> sanitized = new ArrayList<>(length);
            for (int index = 0; index < length; index++) {
                sanitized.add(sanitize(Array.get(value, index)));
            }
            return sanitized;
        }
        return value.toString();
    }

    private Map<String, Object> sanitizeUntypedMap(Map<?, ?> source) {
        Map<String, Object> sanitized = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            String textKey = String.valueOf(key);
            sanitized.put(
                    textKey,
                    isSensitiveKey(textKey) ? REDACTED_VALUE : sanitize(value)
            );
        });
        return sanitized;
    }

    private boolean isSensitiveKey(String key) {
        return key != null && SENSITIVE_KEYS.contains(normalizeKey(key));
    }

    private String normalizeKey(String key) {
        return key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private boolean isSafeScalar(Object value) {
        return value instanceof String
                || value instanceof Number
                || value instanceof Boolean
                || value instanceof Character
                || value instanceof Enum<?>;
    }
}


