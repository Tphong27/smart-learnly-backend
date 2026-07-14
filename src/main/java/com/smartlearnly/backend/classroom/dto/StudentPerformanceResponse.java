package com.smartlearnly.backend.classroom.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record StudentPerformanceResponse(
        UUID studentId,
        String studentName,
        String email,

        int progressPercent,

        BigDecimal averageTestScore,
        BigDecimal averageAssignmentScore,

        Instant lastActivityAt,

        boolean inactive,
        boolean hasLateSubmission
) {
}