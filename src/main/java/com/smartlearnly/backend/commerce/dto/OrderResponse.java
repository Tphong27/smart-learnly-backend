package com.smartlearnly.backend.commerce.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        String orderCode,
        BigDecimal totalAmount,
        String currency,
        String status,
        Instant expiresAt,
        Instant paidAt,
        Instant cancelledAt,
        Instant createdAt,
        Instant updatedAt,
        List<OrderItemResponse> items,
        TransactionResponse transaction,
        SePayOrderSummaryResponse sepayOrder
) {
}
