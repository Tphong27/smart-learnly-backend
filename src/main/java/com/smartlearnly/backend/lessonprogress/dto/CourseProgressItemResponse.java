package com.smartlearnly.backend.lessonprogress.dto;

import java.util.UUID;

public record CourseProgressItemResponse(
        UUID id,
        UUID courseId,
        UUID enrollmentId,
        String title,
        String categoryName,
        String enrollmentStatus,
        String courseStatus,
        boolean accessAllowed,
        String accessBlockedReason,
        String thumbnailUrl,
        int overallPercent,
        ProgressMetricResponse lesson,
        ProgressMetricResponse quiz,
        ProgressMetricResponse flashcard
) {
}
