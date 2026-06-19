package com.smartlearnly.backend.payment.sepay;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SePayPaymentInstructionRequest(
        UUID orderId,
        String orderCode,
        UUID transactionId,
        BigDecimal amount,
        String currency,
        Instant expiresAt
) {
}
