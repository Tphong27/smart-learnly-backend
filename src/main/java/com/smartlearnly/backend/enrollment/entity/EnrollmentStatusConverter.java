package com.smartlearnly.backend.enrollment.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Locale;

@Converter(autoApply = true)
public class EnrollmentStatusConverter implements AttributeConverter<EnrollmentStatus, String> {
    @Override
    public String convertToDatabaseColumn(EnrollmentStatus status) {
        return status == null ? null : status.name().toLowerCase(Locale.ROOT);
    }

    @Override
    public EnrollmentStatus convertToEntityAttribute(String value) {
        return value == null ? null : EnrollmentStatus.valueOf(value.toUpperCase(Locale.ROOT));
    }
}
