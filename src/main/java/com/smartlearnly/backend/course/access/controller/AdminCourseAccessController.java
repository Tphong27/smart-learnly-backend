package com.smartlearnly.backend.course.access.controller;

import com.smartlearnly.backend.common.api.ApiResponse;
import com.smartlearnly.backend.course.access.dto.CourseAccessResponse;
import com.smartlearnly.backend.course.access.dto.UpdateCourseAccessRequest;
import com.smartlearnly.backend.course.access.service.CourseAccessAdminService;
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
public class AdminCourseAccessController {
    private final CourseAccessAdminService courseAccessAdminService;

    // Khóa hoặc mở lại quyền học của toàn bộ học viên đối với một khóa học.
    @PatchMapping("/{courseId}/access")
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
