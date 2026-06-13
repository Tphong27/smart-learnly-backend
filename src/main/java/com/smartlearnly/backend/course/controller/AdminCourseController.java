package com.smartlearnly.backend.course.controller;

import com.smartlearnly.backend.common.api.ApiResponse;
import com.smartlearnly.backend.common.api.PageResponse;
import com.smartlearnly.backend.course.dto.CourseCreateRequest;
import com.smartlearnly.backend.course.dto.CourseResponse;
import com.smartlearnly.backend.course.dto.CourseUpdateRequest;
import com.smartlearnly.backend.course.service.CourseAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/courses")
@Tag(name = "Admin Courses", description = "Admin course authoring APIs.")
@SecurityRequirements({
        @SecurityRequirement(name = "basicAuth"),
        @SecurityRequirement(name = "bearerAuth")
})
public class AdminCourseController {
    private final CourseAdminService courseAdminService;

    @PostMapping
    @Operation(summary = "Create a course")
    public ApiResponse<CourseResponse> createCourse(@Valid @RequestBody CourseCreateRequest request) {
        return ApiResponse.success("Course created successfully", courseAdminService.createCourse(request));
    }

    @GetMapping
    @Operation(summary = "List courses for admin")
    public ApiResponse<PageResponse<CourseResponse>> listCourses(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ApiResponse.success("Courses loaded successfully", courseAdminService.listCourses(page, size));
    }

    @GetMapping("/{courseId}")
    @Operation(summary = "Get a course for admin")
    public ApiResponse<CourseResponse> getCourse(@PathVariable UUID courseId) {
        return ApiResponse.success("Course loaded successfully", courseAdminService.getCourse(courseId));
    }

    @PatchMapping("/{courseId}")
    @Operation(summary = "Update a course")
    public ApiResponse<CourseResponse> updateCourse(
            @PathVariable UUID courseId,
            @Valid @RequestBody CourseUpdateRequest request
    ) {
        return ApiResponse.success("Course updated successfully", courseAdminService.updateCourse(courseId, request));
    }

    @DeleteMapping("/{courseId}")
    @Operation(summary = "Soft delete a course")
    public ApiResponse<Void> deleteCourse(@PathVariable UUID courseId) {
        courseAdminService.deleteCourse(courseId);
        return ApiResponse.success("Course deleted successfully");
    }
}
