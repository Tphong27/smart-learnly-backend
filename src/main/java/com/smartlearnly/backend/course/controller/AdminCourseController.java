package com.smartlearnly.backend.course.controller;

import com.smartlearnly.backend.common.api.ApiResponse;
import com.smartlearnly.backend.common.api.PageResponse;
import com.smartlearnly.backend.course.dto.CourseResponse;
import com.smartlearnly.backend.course.dto.CreateCourseRequest;
import com.smartlearnly.backend.course.dto.UpdateCourseRequest;
import com.smartlearnly.backend.course.service.CourseAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/api/v1/admin/courses")
@Tag(name = "Admin Courses", description = "Administrator course management APIs.")
@SecurityRequirement(name = "bearerAuth")
public class AdminCourseController {
    private final CourseAdminService courseAdminService;

    @GetMapping
    @Operation(summary = "List courses for admin")
    public ApiResponse<PageResponse<CourseResponse>> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ApiResponse.success("Courses loaded successfully", courseAdminService.list(page, size));
    }

    @PostMapping
    @Operation(summary = "Create a course")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Course created"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Slug conflict")
    })
    public ResponseEntity<ApiResponse<CourseResponse>> create(@Valid @RequestBody CreateCourseRequest request) {
        CourseResponse course = courseAdminService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/admin/courses/" + course.id()))
                .body(ApiResponse.success("Course created successfully", course));
    }

    @GetMapping("/{courseId}")
    @Operation(summary = "Get course details for admin")
    public ApiResponse<CourseResponse> get(@PathVariable UUID courseId) {
        return ApiResponse.success("Course loaded successfully", courseAdminService.get(courseId));
    }

    @PatchMapping("/{courseId}")
    @Operation(summary = "Update selected course fields")
    public ApiResponse<CourseResponse> update(
            @PathVariable UUID courseId,
            @Valid @RequestBody UpdateCourseRequest request
    ) {
        return ApiResponse.success("Course updated successfully", courseAdminService.update(courseId, request));
    }

    @DeleteMapping("/{courseId}")
    @Operation(summary = "Soft delete a course")
    public ApiResponse<Void> delete(@PathVariable UUID courseId) {
        courseAdminService.delete(courseId);
        return ApiResponse.success("Course deleted successfully");
    }
}
