package com.smartlearnly.backend.dashboard.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartlearnly.backend.dashboard.dto.AdminDashboardOverviewResponse;
import com.smartlearnly.backend.dashboard.dto.DashboardAccountStatusResponse;
import com.smartlearnly.backend.dashboard.dto.DashboardConfigurationStatusResponse;
import com.smartlearnly.backend.dashboard.dto.DashboardHealthComponentResponse;
import com.smartlearnly.backend.dashboard.dto.DashboardSystemHealthResponse;
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
        when(adminDashboardService.getOverview())
                .thenReturn(sampleResponse());

        mockMvc.perform(get("/api/v1/admin/dashboard/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accountStatus.total").value(10))
                .andExpect(jsonPath("$.data.systemHealth.backend.status").value("UP"));
    }

    private AdminDashboardOverviewResponse sampleResponse() {
        Instant generatedAt = Instant.parse("2026-07-04T00:00:00Z");
        return new AdminDashboardOverviewResponse(
                generatedAt,
                new DashboardSystemHealthResponse(
                        new DashboardHealthComponentResponse("UP"),
                        new DashboardHealthComponentResponse("UP"),
                        List.of()),
                new DashboardConfigurationStatusResponse(List.of()),
                new DashboardAccountStatusResponse(8, 1, 1, 0, 0, 10)
        );
    }
}
