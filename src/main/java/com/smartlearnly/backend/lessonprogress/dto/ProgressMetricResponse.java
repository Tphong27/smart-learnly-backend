package com.smartlearnly.backend.lessonprogress.dto;

public record ProgressMetricResponse(
        String label,
        int completed,
        int total,
        int percent
) {
}