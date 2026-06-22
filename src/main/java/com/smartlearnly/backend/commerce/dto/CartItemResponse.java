package com.smartlearnly.backend.commerce.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CartItemResponse(
        UUID id,
        UUID courseId,
        String courseTitle,
        UUID classId,
        String className,
        BigDecimal price,
        Instant addedAt
) {
}
