package com.smartlearnly.backend.assignment.entity; // Đổi lại package tương ứng nếu bạn để chỗ khác

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class SubmissionStatusConverter implements AttributeConverter<SubmissionStatus, String> {

    @Override
    public String convertToDatabaseColumn(SubmissionStatus attribute) {
        if (attribute == null) {
            return null;
        }
        // Trả về tên của enum dưới dạng chữ thường (lowercase) hoặc tùy theo db của bạn
        return attribute.name().toLowerCase(); 
    }

    @Override
    public SubmissionStatus convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        // Convert ngược từ database string (ví dụ: 'pending') sang Java Enum (PENDING)
        return SubmissionStatus.valueOf(dbData.toUpperCase());
    }
}