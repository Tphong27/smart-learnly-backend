package com.smartlearnly.backend.classroom.controller;

import com.smartlearnly.backend.classroom.dto.ClassAnalyticsResponse;
import com.smartlearnly.backend.classroom.service.ClassAnalyticsService;
import com.smartlearnly.backend.common.api.ApiResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
@RequestMapping("/api/v1/trainer/classes")
@PreAuthorize("hasRole('TRAINER')")
public class TrainerClassAnalyticsController {

    private final ClassAnalyticsService
            classAnalyticsService;

    @GetMapping("/{classId}/analytics")
    public ApiResponse<ClassAnalyticsResponse>
            getAnalytics(
                    @PathVariable UUID classId,

                    @RequestParam(defaultValue = "7")
                    @Min(1)
                    @Max(365)
                    int inactiveDays
            ) {

        return ApiResponse.success(
                "Class analytics loaded successfully",

                classAnalyticsService
                        .getForTrainer(
                                classId,
                                inactiveDays
                        )
        );
    }
}