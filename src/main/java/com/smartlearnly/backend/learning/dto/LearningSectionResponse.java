package com.smartlearnly.backend.learning.dto;

import java.util.List;
import java.util.UUID;

public record LearningSectionResponse(
    UUID sectionId,
    String title,
    int sortOrder,
    List<LearningLessonResponse> lessons
) {
}
