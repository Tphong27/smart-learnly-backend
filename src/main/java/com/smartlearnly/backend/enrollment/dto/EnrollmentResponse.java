package com.smartlearnly.backend.enrollment.dto;

import java.time.Instant;
import java.util.UUID;

public record EnrollmentResponse(
        UUID enrollmentId,
        UUID courseId,
        String status,
        Instant enrollmentDate,
        boolean alreadyEnrolled,
        boolean reactivated
) {
}
