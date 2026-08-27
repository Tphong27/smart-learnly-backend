package com.smartlearnly.backend.dashboard.dto;

import java.util.List;

public record DashboardConfigurationStatusResponse(
        List<DashboardConfigurationItemResponse> items
) {
}
