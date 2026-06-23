package com.smartlearnly.backend.learning.dto;

public record LearningStats(
    int totalSections,
    int totalLessons,
    int totalVideos,
    int totalDocuments,
    int totalQuizzes,
    int totalDurationSeconds
) {
}
