package com.smartlearnly.backend.commerce.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record InvoiceResponse(
        UUID transactionId,
        UUID orderId,
        String invoiceNumber,
        BigDecimal amount,
        String currency,
        String status,
        Instant paidAt
) {
}
