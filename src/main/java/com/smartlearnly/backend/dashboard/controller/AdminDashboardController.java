package com.smartlearnly.backend.dashboard.controller;

import com.smartlearnly.backend.common.api.ApiResponse;
import com.smartlearnly.backend.dashboard.dto.AdminDashboardOverviewResponse;
import com.smartlearnly.backend.dashboard.service.AdminDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/api/v1/admin/dashboard")
@Tag(name = "Admin Dashboard", description = "Admin-only operational dashboard overview APIs")
@SecurityRequirement(name = "bearerAuth")
public class AdminDashboardController {
    private final AdminDashboardService adminDashboardService;

    @GetMapping("/overview")
    @Operation(summary = "Load admin dashboard operational overview")
    public ApiResponse<AdminDashboardOverviewResponse> overview(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {
        return ApiResponse.success(
                "Dashboard overview loaded successfully",
                adminDashboardService.getOverview(from, to)
        );
    }
}
