package com.smartlearnly.backend.commerce.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class PaymentGatewayConverter implements AttributeConverter<PaymentGateway, String> {
    @Override
    public String convertToDatabaseColumn(PaymentGateway gateway) {
        return gateway == null ? null : gateway.name();
    }

    @Override
    public PaymentGateway convertToEntityAttribute(String value) {
        return value == null ? null : PaymentGateway.valueOf(value.toUpperCase());
    }
}
