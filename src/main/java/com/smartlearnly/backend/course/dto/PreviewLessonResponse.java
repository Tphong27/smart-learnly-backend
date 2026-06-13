package com.smartlearnly.backend.course.dto;

import java.util.UUID;

public record PreviewLessonResponse(
        UUID courseId,
        UUID sectionId,
        UUID lessonId,
        String title,
        String content,
        String lessonType,
        Integer sectionOrderIndex,
        Integer lessonOrderIndex
) {
}
