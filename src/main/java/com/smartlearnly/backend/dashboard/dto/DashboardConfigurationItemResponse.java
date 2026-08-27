package com.smartlearnly.backend.dashboard.dto;

public record DashboardConfigurationItemResponse(
        String id,
        String name,
        boolean configured,
        boolean enabled,
        String provider,
        String model
) {
}
