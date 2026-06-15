package com.smartlearnly.backend.course.dto;

import java.util.UUID;

public record PreviewLessonResponse(
        UUID courseId,
        UUID sectionId,
        UUID lessonId,
        String title,
        String lessonType,
        String videoUrl,
        String content,
        String attachmentUrl,
        Integer durationSeconds,
        Integer sectionSortOrder,
        Integer lessonSortOrder
) {
}
