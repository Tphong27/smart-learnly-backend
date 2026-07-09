package com.smartlearnly.backend.curriculum.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CurriculumLessonResponse(
        UUID id,
        UUID lessonIdentityId,
        UUID sourceLessonId,
        UUID sourceCurriculumLessonId,
        String title,
        String lessonType,
        String videoUrl,
        String content,
        String attachmentUrl,
        Integer durationSeconds,
        boolean isPreview,
        String status,
        UUID testId,
        List<CurriculumResourceResponse> resources,
        Integer sortOrder,
        Instant createdAt,
        Instant updatedAt
) {
}
