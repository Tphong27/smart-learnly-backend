package com.smartlearnly.backend.commerce.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CheckoutResponse(
        UUID orderId,
        String orderCode,
        UUID transactionId,
        String paymentGateway,
        String paymentCode,
        String transferContent,
        BigDecimal amount,
        String currency,
        String bankAccountNumber,
        String bankName,
        String accountName,
        String qrUrl,
        String status,
        Instant expiresAt
) {
}
