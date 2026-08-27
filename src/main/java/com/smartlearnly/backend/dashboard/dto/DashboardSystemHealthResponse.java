package com.smartlearnly.backend.dashboard.dto;

import java.util.List;

public record DashboardSystemHealthResponse(
        DashboardHealthComponentResponse backend,
        DashboardHealthComponentResponse database,
        List<DashboardServiceHealthResponse> services
) {
}
