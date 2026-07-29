package com.smartlearnly.backend.notification.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Locale;

@Converter(autoApply = true)
public class NotificationTypeConverter implements AttributeConverter<NotificationType, String> {
    @Override
    public String convertToDatabaseColumn(NotificationType type) {
        return type == null ? null : type.name().toLowerCase(Locale.ROOT);
    }

    @Override
    public NotificationType convertToEntityAttribute(String value) {
        return value == null ? null : NotificationType.valueOf(value.toUpperCase(Locale.ROOT));
    }
}
