package com.smartlearnly.backend.enrollment.dto;

import java.time.Instant;
import java.util.UUID;

public record ClassEnrollmentResponse(
        UUID classEnrollmentId,
        UUID classId,
        UUID courseId,
        String status,
        Instant enrollmentDate,
        boolean alreadyEnrolled,
        boolean reactivated
) {
}