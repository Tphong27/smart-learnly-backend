package com.smartlearnly.backend.curriculum.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Locale;

@Converter(autoApply = false)
public class CurriculumScopeConverter implements AttributeConverter<CurriculumScope, String> {
    @Override
    public String convertToDatabaseColumn(CurriculumScope scope) {
        return scope == null ? null : scope.name();
    }

    @Override
    public CurriculumScope convertToEntityAttribute(String value) {
        return value == null ? null : CurriculumScope.valueOf(value.toUpperCase(Locale.ROOT));
    }
}
