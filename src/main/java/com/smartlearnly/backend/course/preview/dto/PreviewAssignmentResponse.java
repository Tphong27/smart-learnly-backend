package com.smartlearnly.backend.course.preview.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** Nội dung assignment an toàn để khách chỉ xem trong course preview. */
public record PreviewAssignmentResponse(
        String title,
        String description,
        String rubric,
        String instructionFileUrl,
        String instructionFileName,
        Instant dueDate,
        Boolean allowLateSubmission,
        Instant lockoutDate,
        BigDecimal maxScore
) {
}
