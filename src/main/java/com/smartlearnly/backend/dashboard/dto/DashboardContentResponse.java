package com.smartlearnly.backend.dashboard.dto;

public record DashboardContentResponse(
        long modules,
        long lessons,
        long publishedLessons,
        long draftLessons,
        long inactiveLessons,
        long newModulesInRange,
        long newLessonsInRange
) {
}
