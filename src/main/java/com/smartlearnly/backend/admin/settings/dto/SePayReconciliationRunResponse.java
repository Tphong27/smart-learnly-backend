package com.smartlearnly.backend.admin.settings.dto;

/**
 * Summary returned after a manual SePay reconciliation run.
 */
public record SePayReconciliationRunResponse(
        int pendingOrders,
        int queriedOrders,
        int matchedCandidates,
        int queryFailures,
        int candidateFailures
) {
    public boolean hasFailures() {
        return queryFailures > 0 || candidateFailures > 0;
    }
}
