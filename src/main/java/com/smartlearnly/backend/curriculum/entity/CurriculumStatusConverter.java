package com.smartlearnly.backend.curriculum.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Locale;

@Converter(autoApply = false)
public class CurriculumStatusConverter implements AttributeConverter<CurriculumStatus, String> {
    @Override
    public String convertToDatabaseColumn(CurriculumStatus status) {
        return status == null ? null : status.name();
    }

    @Override
    public CurriculumStatus convertToEntityAttribute(String value) {
        return value == null ? null : CurriculumStatus.valueOf(value.toUpperCase(Locale.ROOT));
    }
}
