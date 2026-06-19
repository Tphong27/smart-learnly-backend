package com.smartlearnly.backend.enrollment.dto;

import java.time.Instant;
import java.util.UUID;

public record EnrollmentHistoryResponse(
        UUID enrollmentId,
        UUID courseId,
        String courseTitle,
        String courseSlug,
        String status,
        Instant enrollmentDate,
        Instant updatedAt
) {
}
