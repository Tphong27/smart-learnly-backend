package com.smartlearnly.backend.dashboard.dto;

public record DashboardQuestionsResponse(
        long total,
        long approved,
        long pendingReview,
        long draft,
        long rejected,
        long archived,
        long newInRange,
        long reviewedInRange,
        long aiGenerated,
        long manual
) {
}
