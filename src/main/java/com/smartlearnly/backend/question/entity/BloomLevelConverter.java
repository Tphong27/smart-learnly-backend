package com.smartlearnly.backend.question.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class BloomLevelConverter implements AttributeConverter<BloomLevel, String> {
    @Override
    public String convertToDatabaseColumn(BloomLevel attribute) {
        return attribute == null ? null : attribute.name().toLowerCase();
    }

    @Override
    public BloomLevel convertToEntityAttribute(String dbData) {
        return dbData == null ? null : BloomLevel.valueOf(dbData.toUpperCase());
    }
}