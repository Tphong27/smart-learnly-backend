package com.smartlearnly.backend.payment.sepay;

import java.math.BigDecimal;
import java.time.Instant;

public record SePayPaymentInstruction(
        String paymentCode,
        String bankAccountNumber,
        String bankName,
        String accountName,
        String qrUrl,
        BigDecimal amount,
        Instant expiresAt
) {
}
