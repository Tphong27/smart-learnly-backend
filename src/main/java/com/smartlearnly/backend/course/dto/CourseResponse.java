package com.smartlearnly.backend.course.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CourseResponse(
        UUID id,
        UUID categoryId,
        String categoryName,
        UUID creatorId,
        String title,
        String slug,
        String shortDescription,
        String description,
        String outcomes,
        String requirements,
        String language,
        String level,
        String thumbnailUrl,
        BigDecimal price,
        BigDecimal discountedPrice,
        boolean isFree,
        String status,
        Instant createdAt,
        Instant updatedAt,
        UUID assignedSmeId
) {
}
