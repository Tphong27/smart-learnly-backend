package com.smartlearnly.backend.classroom.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Locale;

@Converter(autoApply = true)
public class ClassStatusConverter implements AttributeConverter<ClassStatus, String> {
    @Override
    public String convertToDatabaseColumn(ClassStatus status) {
        return status == null ? null : status.name().toLowerCase(Locale.ROOT);
    }

    @Override
    public ClassStatus convertToEntityAttribute(String value) {
        return value == null ? null : ClassStatus.valueOf(value.toUpperCase(Locale.ROOT));
    }
}
