package com.smartlearnly.backend.commerce.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderSummaryResponse(
        UUID id,
        String orderCode,
        BigDecimal amount,
        String currency,
        String status,
        Instant expiresAt,
        Instant paidAt,
        Instant createdAt
) {
}
