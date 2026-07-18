package com.smartlearnly.backend.enrollment.controller;

import com.smartlearnly.backend.common.api.ApiResponse;
import com.smartlearnly.backend.common.api.PageResponse;
import com.smartlearnly.backend.enrollment.dto.ClassEnrollmentResponse;
import com.smartlearnly.backend.enrollment.dto.EnrollmentHistoryResponse;
import com.smartlearnly.backend.enrollment.dto.EnrollmentResponse;
import com.smartlearnly.backend.enrollment.dto.EnrollmentStatusHistoryResponse;
import com.smartlearnly.backend.enrollment.dto.FreeClassEnrollmentRequest;
import com.smartlearnly.backend.enrollment.dto.FreeCourseEnrollmentRequest;
import com.smartlearnly.backend.enrollment.dto.MyCourseResponse;
import com.smartlearnly.backend.enrollment.service.ClassEnrollmentService;
import com.smartlearnly.backend.enrollment.service.CourseEnrollmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/enrollments")
@RequiredArgsConstructor
@Tag(name = "Enrollments", description = "Enrollment access and history endpoints")
public class EnrollmentController {
    private final CourseEnrollmentService courseEnrollmentService;
    private final ClassEnrollmentService classEnrollmentService;

    @PostMapping("/free-course")
    @PreAuthorize("hasRole('TRAINEE')")
    @Operation(summary = "Enroll in a published free online course")
    public ApiResponse<EnrollmentResponse> enrollFreeCourse(
            @Valid @RequestBody FreeCourseEnrollmentRequest request) {
        return ApiResponse.success(
                "Free course enrollment completed",
                courseEnrollmentService.enrollFreeCourse(request.courseId()));
    }

    @GetMapping("/my-courses")
    @PreAuthorize("hasRole('TRAINEE')")
    @Operation(summary = "List active courses for the authenticated trainee")
    public ApiResponse<List<MyCourseResponse>> getMyCourses() {
        return ApiResponse.success(courseEnrollmentService.getMyCourses());
    }

    @GetMapping
    @PreAuthorize("hasRole('TRAINEE')")
    @Operation(summary = "List course enrollment history for the authenticated trainee")
    public ApiResponse<PageResponse<EnrollmentHistoryResponse>> getHistory(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(courseEnrollmentService.getHistory(page, size));
    }

    @GetMapping("/{enrollmentId}/status-history")
    @PreAuthorize("hasAnyRole('TRAINEE', 'ADMIN', 'TMO')")
    @Operation(summary = "List audited status transitions for a course enrollment")
    public ApiResponse<List<EnrollmentStatusHistoryResponse>> getStatusHistory(
            @PathVariable UUID enrollmentId) {
        return ApiResponse.success(courseEnrollmentService.getStatusHistory(enrollmentId));
    }

    @PostMapping("/free-class")
    @PreAuthorize("hasRole('TRAINEE')")
    @Operation(summary = "Enroll in an upcoming free offline class")
    public ApiResponse<ClassEnrollmentResponse> enrollFreeClass(
            @Valid @RequestBody FreeClassEnrollmentRequest request) {

        return ApiResponse.success(
                "Free class enrollment completed",
                classEnrollmentService.enrollFreeClass(request.classId()));
    }
}
