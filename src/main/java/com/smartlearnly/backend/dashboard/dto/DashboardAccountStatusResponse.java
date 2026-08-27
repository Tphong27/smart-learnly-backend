package com.smartlearnly.backend.dashboard.dto;

public record DashboardAccountStatusResponse(
        long active,
        long pendingVerify,
        long inactive,
        long locked,
        long banned,
        long total
) {
}
