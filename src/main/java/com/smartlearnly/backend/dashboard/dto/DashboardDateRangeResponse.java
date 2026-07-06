package com.smartlearnly.backend.dashboard.dto;

import java.time.Instant;

public record DashboardDateRangeResponse(
        Instant from,
        Instant to,
        int maxDays
) {
}
