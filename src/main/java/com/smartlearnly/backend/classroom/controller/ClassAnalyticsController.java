package com.smartlearnly.backend.classroom.controller;

import com.smartlearnly.backend.classroom.dto.ClassAnalyticsResponse;
import com.smartlearnly.backend.classroom.dto.StudentPerformanceQuery;
import com.smartlearnly.backend.classroom.service.ClassAnalyticsService;
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
@RequestMapping("/api/v1")
public class ClassAnalyticsController {

    private final ClassAnalyticsService classAnalyticsService;

    @GetMapping("/admin/classes/{classId}/analytics")
    @PreAuthorize("hasAnyRole('ADMIN', 'TMO')")
    public ApiResponse<ClassAnalyticsResponse> getAdminOrTmoAnalytics(
            @PathVariable UUID classId,
            @RequestParam(defaultValue = "7") @Min(1) @Max(365) int inactiveDays,
            @RequestParam(required = false) @Size(max = 100) String keyword,
            @RequestParam(defaultValue = "all") String progress,
            @RequestParam(defaultValue = "all") String indicator,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {

        StudentPerformanceQuery query = createQuery(keyword, progress, indicator, page, size);
        ClassAnalyticsResponse analytics = classAnalyticsService.getForAdminOrTmo(classId, inactiveDays, query);

        return ApiResponse.success("Class analytics loaded successfully", analytics);
    }

    @GetMapping("/trainer/classes/{classId}/analytics")
    @PreAuthorize("hasRole('TRAINER')")
    public ApiResponse<ClassAnalyticsResponse> getTrainerAnalytics(
            @PathVariable UUID classId,
            @RequestParam(defaultValue = "7") @Min(1) @Max(365) int inactiveDays,
            @RequestParam(required = false) @Size(max = 100) String keyword,
            @RequestParam(defaultValue = "all") String progress,
            @RequestParam(defaultValue = "all") String indicator,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {

        StudentPerformanceQuery query = createQuery(keyword, progress, indicator, page, size);
        ClassAnalyticsResponse analytics = classAnalyticsService.getForTrainer(classId, inactiveDays, query);

        return ApiResponse.success("Class analytics loaded successfully", analytics);
    }

    private StudentPerformanceQuery createQuery(String keyword, String progress, String indicator, int page, int size) {
        return new StudentPerformanceQuery(keyword, progress, indicator, page, size);
    }
}