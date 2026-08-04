package com.smartlearnly.backend.payment.sepay.dto;

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
    // Ưu tiên mã tham chiếu ngân hàng làm khóa giao dịch, sau đó mới dùng id SePay.
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
