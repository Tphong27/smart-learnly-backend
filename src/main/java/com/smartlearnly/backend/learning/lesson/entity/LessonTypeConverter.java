package com.smartlearnly.backend.learning.lesson.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Locale;

@Converter(autoApply = true)
public class LessonTypeConverter implements AttributeConverter<LessonType, String> {
    @Override
    public String convertToDatabaseColumn(LessonType type) {
        return type == null ? null : type.name().toLowerCase(Locale.ROOT);
    }

    @Override
    public LessonType convertToEntityAttribute(String value) {
        return value == null ? null : LessonType.valueOf(value.toUpperCase(Locale.ROOT));
    }
}
