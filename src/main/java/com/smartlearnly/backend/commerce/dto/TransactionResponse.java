package com.smartlearnly.backend.commerce.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionResponse(
        UUID id,
        UUID orderId,
        BigDecimal amount,
        String currency,
        String status,
        String paymentGateway,
        String invoiceNumber,
        Instant paidAt,
        Instant expiresAt,
        Instant createdAt
) {
}
