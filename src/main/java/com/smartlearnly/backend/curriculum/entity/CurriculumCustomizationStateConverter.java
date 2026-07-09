package com.smartlearnly.backend.curriculum.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Locale;

@Converter(autoApply = false)
public class CurriculumCustomizationStateConverter implements AttributeConverter<CurriculumCustomizationState, String> {
    @Override
    public String convertToDatabaseColumn(CurriculumCustomizationState state) {
        return state == null ? null : state.name();
    }

    @Override
    public CurriculumCustomizationState convertToEntityAttribute(String value) {
        return value == null ? null : CurriculumCustomizationState.valueOf(value.toUpperCase(Locale.ROOT));
    }
}
