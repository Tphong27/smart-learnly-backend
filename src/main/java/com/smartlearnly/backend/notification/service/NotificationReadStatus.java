package com.smartlearnly.backend.notification.service;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import java.util.Locale;

public enum NotificationReadStatus {
    ALL,
    UNREAD,
    READ;

    public static NotificationReadStatus from(String value) {
        if (value == null || value.isBlank()) {
            return ALL;
        }
        try {
            return NotificationReadStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Notification status must be all, unread, or read");
        }
    }
}
