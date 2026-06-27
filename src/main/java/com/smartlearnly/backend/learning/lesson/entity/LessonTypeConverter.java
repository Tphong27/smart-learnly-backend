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
        if (value == null) {
            return null;
        }
        try {
            return LessonType.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            // Giá trị lesson_type trong DB chưa được map sang enum (vd type mới do migration khác thêm).
            // Trả về RICH_TEXT thay vì ném lỗi để không làm hỏng cả truy vấn danh sách lesson.
            return LessonType.RICH_TEXT;
        }
    }
}
