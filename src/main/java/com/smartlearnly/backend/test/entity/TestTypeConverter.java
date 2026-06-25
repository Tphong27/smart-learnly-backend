package com.smartlearnly.backend.test.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class TestTypeConverter implements AttributeConverter<TestType, String> {

    @Override
    public String convertToDatabaseColumn(TestType attribute) {
        if (attribute == null) {
            return null;
        }
        // Chuyển sang chữ hoa để khớp với kiểu enum thông thường trong Postgres
        return attribute.name().toLowerCase();
    }

    @Override
    public TestType convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        // Chuyển ngược từ DB String sang Java Enum
        return TestType.valueOf(dbData.toLowerCase());
    }
}