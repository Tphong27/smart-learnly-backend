package com.smartlearnly.backend.commerce.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class TransactionStatusConverter implements AttributeConverter<TransactionStatus, String> {
    @Override
    public String convertToDatabaseColumn(TransactionStatus status) {
        return status == null ? null : status.name();
    }

    @Override
    public TransactionStatus convertToEntityAttribute(String value) {
        return value == null ? null : TransactionStatus.valueOf(value.toUpperCase());
    }
}
