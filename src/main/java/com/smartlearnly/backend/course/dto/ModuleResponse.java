package com.smartlearnly.backend.course.dto;

import java.time.Instant;
import java.util.UUID;

public record ModuleResponse(
        UUID moduleId,
        UUID id,
        UUID courseId,
        String title,
        Integer sortOrder,
        Instant createdAt,
        Instant updatedAt
) {
}
