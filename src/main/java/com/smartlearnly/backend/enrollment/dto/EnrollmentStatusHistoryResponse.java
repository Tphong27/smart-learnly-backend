package com.smartlearnly.backend.enrollment.dto;

import java.time.Instant;
import java.util.UUID;

public record EnrollmentStatusHistoryResponse(
        UUID id,
        String fromStatus,
        String toStatus,
        String source,
        String reason,
        UUID transactionId,
        UUID changedBy,
        Instant createdAt
) {
}
