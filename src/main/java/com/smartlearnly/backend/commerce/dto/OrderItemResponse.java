package com.smartlearnly.backend.commerce.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemResponse(
        UUID id,
        UUID courseId,
        UUID classId,
        String itemTitle,
        BigDecimal unitPrice,
        BigDecimal discountAmount,
        BigDecimal finalAmount
) {
}
