package com.smartlearnly.backend.dashboard.dto;

/**
 * Snapshot account counts for Information System dashboard.
 * Kept as a thin alias shape used by the query repository.
 */
public record DashboardUsersResponse(
        long total,
        long active,
        long pendingVerify,
        long inactive,
        long locked,
        long banned
) {
}
