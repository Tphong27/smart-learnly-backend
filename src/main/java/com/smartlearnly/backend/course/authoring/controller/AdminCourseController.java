package com.smartlearnly.backend.course.authoring.controller;

import com.smartlearnly.backend.common.api.ApiResponse;
import com.smartlearnly.backend.common.api.PageResponse;
import com.smartlearnly.backend.course.authoring.dto.CourseResponse;
import com.smartlearnly.backend.course.authoring.dto.CreateCourseRequest;
import com.smartlearnly.backend.course.authoring.dto.UpdateCourseRequest;
import com.smartlearnly.backend.course.authoring.service.CourseAdminService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
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
@PreAuthorize("hasAnyRole('TMO', 'SME', 'TRAINER')")
@RequestMapping("/api/v1/admin/courses")
public class AdminCourseController {
    private final CourseAdminService courseAdminService;

    // Liệt kê khóa học quản trị theo phân trang, từ khóa và các bộ lọc được phép.
    @GetMapping
    public ApiResponse<PageResponse<CourseResponse>> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) @Size(max = 200) String keyword,
            @RequestParam(required = false) @Size(max = 20) String status,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) @Size(max = 30) String level) {
        return ApiResponse.success(
                "Courses loaded successfully",
                courseAdminService.list(page, size, keyword, status, categoryId, level));
    }

    // Tạo khóa học nháp mới và trả vị trí tài nguyên vừa tạo.
    @PostMapping
    @PreAuthorize("hasRole('TMO')")
    public ResponseEntity<ApiResponse<CourseResponse>> create(@Valid @RequestBody CreateCourseRequest request) {
        CourseResponse course = courseAdminService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/admin/courses/" + course.id()))
                .body(ApiResponse.success("Course created successfully", course));
    }

    // Trả chi tiết khóa học cho người có quyền quản trị hoặc được phân công.
    @GetMapping("/{courseId}")
    public ApiResponse<CourseResponse> get(@PathVariable UUID courseId) {
        return ApiResponse.success("Course loaded successfully", courseAdminService.get(courseId));
    }

    // Cập nhật riêng các trường metadata được gửi trong yêu cầu PATCH.
    @PatchMapping("/{courseId}")
    @PreAuthorize("hasAnyRole('TMO', 'TRAINER')")
    public ApiResponse<CourseResponse> update(
            @PathVariable UUID courseId,
            @Valid @RequestBody UpdateCourseRequest request) {
        CourseResponse updatedCourse = courseAdminService.update(courseId, request);

        return ApiResponse.success(
                "Course updated successfully",
                updatedCourse);
    }

    // Lưu trữ mềm khóa học để dữ liệu lịch sử không bị xóa vật lý.
    @DeleteMapping("/{courseId}")
    @PreAuthorize("hasRole('TMO')")
    public ApiResponse<Void> delete(@PathVariable UUID courseId) {
        courseAdminService.delete(courseId);
        return ApiResponse.success("Course deleted successfully");
    }
}
