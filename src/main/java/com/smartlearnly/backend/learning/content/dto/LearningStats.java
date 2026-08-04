package com.smartlearnly.backend.learning.content.dto;

public record LearningStats(
    int totalSections,
    int totalLessons,
    int totalVideos,
    int totalDocuments,
    int totalQuizzes,
    int totalDurationSeconds
) {
}
