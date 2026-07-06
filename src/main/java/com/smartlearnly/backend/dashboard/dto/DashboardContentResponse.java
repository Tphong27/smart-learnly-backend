package com.smartlearnly.backend.dashboard.dto;

public record DashboardContentResponse(
        long sections,
        long lessons,
        long publishedLessons,
        long draftLessons,
        long inactiveLessons,
        long newSectionsInRange,
        long newLessonsInRange
) {
}
