package com.smartlearnly.backend.notification.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class NotificationTypeConverterTest {

    private final NotificationTypeConverter converter = new NotificationTypeConverter();

    @Test
    void convertToDatabaseColumnShouldUseLowercaseEnumValue() {
        assertThat(converter.convertToDatabaseColumn(NotificationType.AI_SUGGESTION))
                .isEqualTo("ai_suggestion");
    }

    @Test
    void convertToDatabaseColumnShouldAllowNull() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
    }

    @Test
    void convertToEntityAttributeShouldReadLowercaseDatabaseValue() {
        assertThat(converter.convertToEntityAttribute("payment"))
                .isEqualTo(NotificationType.PAYMENT);
    }

    @Test
    void convertToEntityAttributeShouldAllowNull() {
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    void convertToEntityAttributeShouldRejectUnknownValue() {
        assertThatThrownBy(() -> converter.convertToEntityAttribute("unknown_type"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
