package com.smartlearnly.backend.lessonprogress.trainee.dto;

public record ProgressMetricResponse(
        String label,
        int completed,
        int total,
        int percent
) {
}
