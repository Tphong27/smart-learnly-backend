package com.smartlearnly.backend.course.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Locale;

@Converter(autoApply = true)
public class CourseStatusConverter implements AttributeConverter<CourseStatus, String> {
    @Override
    public String convertToDatabaseColumn(CourseStatus status) {
        return status == null ? null : status.name().toLowerCase(Locale.ROOT);
    }

    @Override
    public CourseStatus convertToEntityAttribute(String value) {
        return value == null ? null : CourseStatus.valueOf(value.toUpperCase(Locale.ROOT));
    }
}
