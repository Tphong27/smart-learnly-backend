package com.smartlearnly.backend.learning.dto;

import java.util.List;
import java.util.UUID;

public record LearningLessonResponse(
    UUID lessonId,
    String title,
    String lessonType,
    String videoUrl,
    String content,
    String attachmentUrl,
    Integer durationSeconds,
    boolean isPreview,
    int sortOrder,
    boolean completed,
    List<LearningResourceResponse> resources
) {
}
