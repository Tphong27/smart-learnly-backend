package com.smartlearnly.backend.dashboard.dto;

public record DashboardUsersResponse(
        long total,
        long active,
        long pendingVerify,
        long inactive,
        long banned,
        long newInRange
) {
}
