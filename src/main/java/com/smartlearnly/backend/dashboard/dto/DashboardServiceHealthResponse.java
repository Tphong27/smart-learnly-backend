package com.smartlearnly.backend.dashboard.dto;

public record DashboardServiceHealthResponse(
        String id,
        String name,
        String status,
        String detail
) {
}
