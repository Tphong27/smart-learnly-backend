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
        return attribute.name().toLowerCase();
    }

    @Override
    public AttemptStatus convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        return switch (dbData.toLowerCase()) {
            case "in_progress" -> AttemptStatus.DOING;
            case "timeout" -> AttemptStatus.EXPIRED;
            default -> AttemptStatus.valueOf(dbData.toUpperCase());
        };
    }
}
