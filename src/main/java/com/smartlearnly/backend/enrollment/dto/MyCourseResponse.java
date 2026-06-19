package com.smartlearnly.backend.enrollment.dto;

import com.smartlearnly.backend.course.dto.CategorySummaryResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record MyCourseResponse(
        UUID id,
        String title,
        String slug,
        String description,
        BigDecimal price,
        String avatarUrl,
        boolean featured,
        CategorySummaryResponse category,
        UUID enrollmentId,
        String enrollmentStatus,
        Instant enrollmentDate,
        String courseStatus,
        boolean accessAllowed,
        String accessBlockedReason
) {
}
