package com.smartlearnly.backend.learning.dto;

import java.util.List;
import java.util.UUID;

public record LearningContentResponse(
    UUID courseId,
    String courseTitle,
    String courseThumbnail,
    List<LearningSectionResponse> sections,
    LearningStats stats
) {
}
