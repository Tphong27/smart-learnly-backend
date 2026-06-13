package com.smartlearnly.backend.course.dto;

import java.time.Instant;
import java.util.UUID;

public record LessonResponse(
        UUID id,
        UUID courseId,
        UUID sectionId,
        String title,
        String lessonType,
        String videoUrl,
        String content,
        String attachmentUrl,
        Integer durationSeconds,
        boolean isPreview,
        String status,
        Integer sortOrder,
        Instant createdAt,
        Instant updatedAt
) {
}
