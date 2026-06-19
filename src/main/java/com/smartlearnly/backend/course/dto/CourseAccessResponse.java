package com.smartlearnly.backend.course.dto;

import java.time.Instant;
import java.util.UUID;

public record CourseAccessResponse(
        UUID courseId,
        boolean accessBlocked,
        Instant accessBlockedAt,
        String accessBlockReason,
        UUID accessBlockedBy
) {
}
