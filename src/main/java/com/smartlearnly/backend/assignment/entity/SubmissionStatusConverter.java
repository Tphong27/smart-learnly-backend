package com.smartlearnly.backend.assignment.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class SubmissionStatusConverter
        implements AttributeConverter<SubmissionStatus, String> {

    @Override
    public String convertToDatabaseColumn(SubmissionStatus attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute.name().toLowerCase();
    }

    @Override
    public SubmissionStatus convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        return switch (dbData.toLowerCase()) {
            case "pending" -> SubmissionStatus.DOING;
            case "late" -> SubmissionStatus.EXPIRED;
            default -> SubmissionStatus.valueOf(dbData.toUpperCase());
        };
    }
}
