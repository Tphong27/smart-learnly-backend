package com.smartlearnly.backend.dashboard.dto;

import java.time.Instant;
import java.util.List;

public record AdminDashboardOverviewResponse(
        DashboardDateRangeResponse range,
        DashboardUsersResponse users,
        DashboardCoursesResponse courses,
        DashboardClassesResponse classes,
        DashboardContentResponse content,
        DashboardQuestionBanksResponse questionBanks,
        List<DashboardRecentActivityResponse> recentActivities,
        Instant generatedAt
) {
}
