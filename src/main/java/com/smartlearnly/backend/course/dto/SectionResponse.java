package com.smartlearnly.backend.course.dto;

import java.time.Instant;
import java.util.UUID;

public record SectionResponse(
        UUID id,
        UUID courseId,
        String title,
        Integer orderIndex,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
}
