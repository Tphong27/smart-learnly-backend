package com.smartlearnly.backend.test.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class AttemptStatusConverter implements AttributeConverter<AttemptStatus, String> {

    @Override
    public String convertToDatabaseColumn(AttemptStatus attribute) {
        if (attribute == null) {
            return null;
        }
        // Chuyển thành chữ thường để khớp với giá trị lưu trong Postgres
        return attribute.name().toLowerCase();
    }

    @Override
    public AttemptStatus convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        // Chuyển ngược từ String dưới DB thành Enum trong Java
        return AttemptStatus.valueOf(dbData.toUpperCase());
    }
}