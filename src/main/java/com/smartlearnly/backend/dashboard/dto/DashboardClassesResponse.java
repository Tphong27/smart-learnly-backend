package com.smartlearnly.backend.dashboard.dto;

public record DashboardClassesResponse(
        long total,
        long upcoming,
        long ongoing,
        long completed,
        long cancelled,
        long newInRange
) {
}
