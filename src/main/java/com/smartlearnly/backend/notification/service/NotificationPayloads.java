package com.smartlearnly.backend.notification.service;

import java.util.LinkedHashMap;
import java.util.Map;

public final class NotificationPayloads {
    private NotificationPayloads() {
    }

    public static Map<String, Object> of(Object... keyValues) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (keyValues == null) {
            return payload;
        }
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("Notification payload requires key/value pairs");
        }
        for (int index = 0; index < keyValues.length; index += 2) {
            Object key = keyValues[index];
            Object value = keyValues[index + 1];
            if (key != null && value != null) {
                payload.put(String.valueOf(key), value);
            }
        }
        return payload;
    }
}
