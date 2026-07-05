package com.smartlearnly.backend.classroom.controller;

import com.smartlearnly.backend.classroom.dto.ClassResponse;
import com.smartlearnly.backend.classroom.service.ClassTrainerService;
import com.smartlearnly.backend.common.api.ApiResponse;
import com.smartlearnly.backend.common.api.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@PreAuthorize("hasRole('TRAINER')")
@RequestMapping("/api/v1/trainer/classes")
@Tag(name = "Trainer Classes", description = "Trainer assigned class APIs")
@SecurityRequirement(name = "bearerAuth")
public class ClassTrainerController {
    private final ClassTrainerService classTrainerService;

    @GetMapping
    @Operation(summary = "List classes assigned to current trainer")
    public ApiResponse<PageResponse<ClassResponse>> listMyAssignedClasses(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ApiResponse.success(
                "Assigned classes loaded successfully",
                classTrainerService.listMyAssignedClasses(status, keyword, page, size)
        );
    }

    @GetMapping("/{classId}")
    @Operation(summary = "Get assigned class detail")
    public ApiResponse<ClassResponse> getMyAssignedClassDetail(@PathVariable UUID classId) {
        return ApiResponse.success(
                "Assigned class loaded successfully",
                classTrainerService.getMyAssignedClassDetail(classId)
        );
    }
}