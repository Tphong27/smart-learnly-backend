package com.smartlearnly.backend.payment.sepay;

import java.math.BigDecimal;

public record SePayTransactionCandidate(
        String id,
        String transactionDate,
        String accountNumber,
        String transferType,
        BigDecimal amountIn,
        String transactionContent,
        String referenceNumber,
        String code
) {
    String gatewayTransactionId() {
        if (referenceNumber != null && !referenceNumber.isBlank()) {
            return referenceNumber.trim();
        }
        if (id != null && !id.isBlank()) {
            return id.trim();
        }
        return null;
    }
}
