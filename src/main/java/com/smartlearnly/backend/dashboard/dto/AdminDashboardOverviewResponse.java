package com.smartlearnly.backend.dashboard.dto;

import java.time.Instant;

public record AdminDashboardOverviewResponse(
        Instant generatedAt,
        DashboardSystemHealthResponse systemHealth,
        DashboardConfigurationStatusResponse configurationStatus,
        DashboardAccountStatusResponse accountStatus
) {
}
