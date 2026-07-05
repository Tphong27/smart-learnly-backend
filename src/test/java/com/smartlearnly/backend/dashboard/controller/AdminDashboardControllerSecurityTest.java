package com.smartlearnly.backend.dashboard.controller;

import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartlearnly.backend.dashboard.dto.AdminDashboardOverviewResponse;
import com.smartlearnly.backend.dashboard.dto.DashboardClassesResponse;
import com.smartlearnly.backend.dashboard.dto.DashboardContentResponse;
import com.smartlearnly.backend.dashboard.dto.DashboardCoursesResponse;
import com.smartlearnly.backend.dashboard.dto.DashboardDateRangeResponse;
import com.smartlearnly.backend.dashboard.dto.DashboardQuestionBanksResponse;
import com.smartlearnly.backend.dashboard.dto.DashboardUsersResponse;
import com.smartlearnly.backend.dashboard.service.AdminDashboardService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminDashboardControllerSecurityTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminDashboardService adminDashboardService;

    @Test
    void overviewShouldRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/admin/dashboard/overview"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "TMO")
    void overviewShouldRejectNonAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/admin/dashboard/overview"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void overviewShouldAllowAdmin() throws Exception {
        when(adminDashboardService.getOverview(isNull(), isNull()))
                .thenReturn(sampleResponse());

        mockMvc.perform(get("/api/v1/admin/dashboard/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.users.total").value(10))
                .andExpect(jsonPath("$.data.recentActivities").isEmpty());
    }

    private AdminDashboardOverviewResponse sampleResponse() {
        Instant from = Instant.parse("2026-06-04T00:00:00Z");
        Instant to = Instant.parse("2026-07-04T00:00:00Z");
        return new AdminDashboardOverviewResponse(
                new DashboardDateRangeResponse(from, to, 90),
                new DashboardUsersResponse(10, 8, 1, 1, 0, 2),
                new DashboardCoursesResponse(5, 3, 1, 1, 1),
                new DashboardClassesResponse(4, 1, 1, 1, 1, 1),
                new DashboardContentResponse(6, 12, 8, 3, 1),
                new DashboardQuestionBanksResponse(3, 1, 1, 1, 20, 15, 2, 1, 1, 1),
                List.of(),
                to
        );
    }
}
