package com.smartlearnly.backend.dashboard.dto;

public record DashboardCoursesResponse(
        long total,
        long published,
        long draft,
        long inactive,
        long newInRange
) {
}
