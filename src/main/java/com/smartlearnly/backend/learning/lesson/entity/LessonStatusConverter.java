package com.smartlearnly.backend.learning.lesson.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Locale;

@Converter(autoApply = true)
public class LessonStatusConverter implements AttributeConverter<LessonStatus, String> {
    @Override
    public String convertToDatabaseColumn(LessonStatus status) {
        return status == null ? null : status.name().toLowerCase(Locale.ROOT);
    }

    @Override
    public LessonStatus convertToEntityAttribute(String value) {
        return value == null ? null : LessonStatus.valueOf(value.toUpperCase(Locale.ROOT));
    }
}
