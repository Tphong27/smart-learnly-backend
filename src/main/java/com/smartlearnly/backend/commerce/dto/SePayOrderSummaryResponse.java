package com.smartlearnly.backend.commerce.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SePayOrderSummaryResponse(
        UUID id,
        String paymentCode,
        String bankAccountNumber,
        String bankName,
        String accountName,
        BigDecimal amount,
        String qrUrl,
        String status,
        Instant expiresAt,
        Instant matchedAt
) {
}
