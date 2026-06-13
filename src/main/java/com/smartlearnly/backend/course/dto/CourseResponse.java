package com.smartlearnly.backend.course.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CourseResponse(
        UUID id,
        UUID categoryId,
        String categoryName,
        String title,
        String slug,
        String description,
        BigDecimal price,
        String status,
        String avatarUrl,
        UUID creatorId,
        List<String> tags,
        boolean featured,
        Instant createdAt,
        Instant updatedAt
) {
}
