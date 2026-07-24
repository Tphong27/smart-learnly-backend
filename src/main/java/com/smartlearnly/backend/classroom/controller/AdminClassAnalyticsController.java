package com.smartlearnly.backend.classroom.controller;

import com.smartlearnly.backend.classroom.dto.ClassAnalyticsResponse;
import com.smartlearnly.backend.classroom.service.ClassAnalyticsService;
import com.smartlearnly.backend.classroom.dto.StudentPerformanceQuery;
import com.smartlearnly.backend.common.api.ApiResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/classes")
@PreAuthorize("hasAnyRole('ADMIN', 'TMO')")
public class AdminClassAnalyticsController {

        private final ClassAnalyticsService classAnalyticsService;

        @GetMapping("/{classId}/analytics")
        public ApiResponse<ClassAnalyticsResponse> getAnalytics(
                        @PathVariable UUID classId,
                        @RequestParam(defaultValue = "7") @Min(1) @Max(365) int inactiveDays,
                        @RequestParam(required = false) @Size(max = 100) String keyword,
                        @RequestParam(defaultValue = "all") String progress,
                        @RequestParam(defaultValue = "all") String indicator,
                        @RequestParam(defaultValue = "0") @Min(0) int page,
                        @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
                StudentPerformanceQuery query = new StudentPerformanceQuery(
                                keyword,
                                progress,
                                indicator,
                                page,
                                size);

                return ApiResponse.success(
                                "Class analytics loaded successfully",
                                classAnalyticsService.getForAdminOrTmo(classId, inactiveDays, query));
        }
}