package com.smartlearnly.backend.course.dto;

import java.time.Instant;
import java.util.UUID;

public record LessonResponse(
        UUID id,
        UUID sectionId,
        UUID courseId,
        String title,
        String content,
        String lessonType,
        Integer orderIndex,
        boolean preview,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
}
