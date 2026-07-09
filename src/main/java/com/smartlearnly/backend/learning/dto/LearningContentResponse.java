package com.smartlearnly.backend.learning.dto;

import com.smartlearnly.backend.curriculum.dto.CurriculumMetadataResponse;
import java.util.List;
import java.util.UUID;

public record LearningContentResponse(
    UUID courseId,
    String courseTitle,
    String courseThumbnail,
    List<LearningSectionResponse> sections,
    LearningStats stats,
    CurriculumMetadataResponse curriculum
) {
    public LearningContentResponse(
            UUID courseId,
            String courseTitle,
            String courseThumbnail,
            List<LearningSectionResponse> sections,
            LearningStats stats) {
        this(courseId, courseTitle, courseThumbnail, sections, stats, null);
    }
}
