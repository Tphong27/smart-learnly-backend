package com.smartlearnly.backend.course.controller;

import com.smartlearnly.backend.common.api.ApiResponse;
import com.smartlearnly.backend.course.dto.CourseAccessResponse;
import com.smartlearnly.backend.course.dto.UpdateCourseAccessRequest;
import com.smartlearnly.backend.course.service.CourseAccessAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'TMO')")
@RequestMapping("/api/v1/admin/courses")
@Tag(name = "Admin Course Access", description = "Explicit learner access blocking for courses")
@SecurityRequirement(name = "bearerAuth")
public class AdminCourseAccessController {
    private final CourseAccessAdminService courseAccessAdminService;

    @PatchMapping("/{courseId}/access")
    @Operation(summary = "Block or unblock learner access to a course")
    public ApiResponse<CourseAccessResponse> updateAccess(
            @PathVariable UUID courseId,
            @Valid @RequestBody UpdateCourseAccessRequest request
    ) {
        return ApiResponse.success(
                "Course access updated successfully",
                courseAccessAdminService.update(courseId, request)
        );
    }
}
