package com.smartlearnly.backend.lessonprogress.dto;

import java.time.Instant;
import java.util.UUID;

public record LessonProgressResponse(
        UUID lessonId,
        UUID courseId,
        boolean completed,
        Instant completedAt,
        Instant lastAccessedAt
) {
}
