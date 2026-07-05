package com.smartlearnly.backend.dashboard.dto;

public record DashboardQuestionBanksResponse(
        long total,
        long approved,
        long draft,
        long archived,
        long questions,
        long approvedQuestions,
        long pendingReviewQuestions,
        long draftQuestions,
        long rejectedQuestions,
        long archivedQuestions
) {
}
