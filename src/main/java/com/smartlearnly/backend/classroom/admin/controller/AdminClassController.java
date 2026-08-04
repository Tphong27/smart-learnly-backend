package com.smartlearnly.backend.classroom.admin.controller;

import com.smartlearnly.backend.classroom.dto.ClassResponse;
import com.smartlearnly.backend.classroom.admin.dto.ClassStatusOptionResponse;
import com.smartlearnly.backend.classroom.admin.dto.CreateClassRequest;
import com.smartlearnly.backend.classroom.admin.dto.RestoreClassRequest;
import com.smartlearnly.backend.classroom.admin.dto.UpdateClassRequest;
import com.smartlearnly.backend.classroom.schedule.dto.MeetingUrlResponse;
import com.smartlearnly.backend.classroom.schedule.service.GoogleMeetService;
import com.smartlearnly.backend.classroom.admin.service.ClassAdminService;
import com.smartlearnly.backend.common.api.ApiResponse;
import com.smartlearnly.backend.common.api.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.List;
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
@RequestMapping("/api/v1")
@SecurityRequirement(name = "bearerAuth")
public class AdminClassController {
    private final ClassAdminService classAdminService;
    private final GoogleMeetService googleMeetService;

    // Trả các trạng thái lớp mà quản trị viên được phép chọn trong màn quản lý.
    @GetMapping("/admin/classes/statuses")
    @PreAuthorize("hasAnyRole('ADMIN', 'TMO')")
    @Operation(summary = "List class status options", tags = { "Admin Classes" })
    public ApiResponse<List<ClassStatusOptionResponse>> listStatusOptions() {
        return ApiResponse.success("Class statuses loaded successfully", classAdminService.listStatusOptions());
    }

    // Tạo link Google Meet mới để quản trị viên gắn vào lớp học.
    @PostMapping("/admin/classes/meeting-links")
    @PreAuthorize("hasAnyRole('ADMIN', 'TMO')")
    @Operation(summary = "Generate a Google Meet link", tags = { "Admin Classes" })
    public ApiResponse<MeetingUrlResponse> generateMeetingUrl() {
        return ApiResponse.success("Google Meet link generated successfully", googleMeetService.createMeetingUrl());
    }

    // Liệt kê lớp học theo các bộ lọc quản trị và phân trang hiện tại.
    @GetMapping("/admin/classes")
    @PreAuthorize("hasAnyRole('ADMIN', 'TMO')")
    @Operation(summary = "List classes with filters", tags = { "Admin Classes" })
    public ApiResponse<PageResponse<ClassResponse>> listAdminClasses(
            @RequestParam(required = false) UUID courseId,
            @RequestParam(required = false) UUID trainerId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(
                "Classes loaded successfully",
                classAdminService.list(courseId, trainerId, status, keyword, page, size));
    }

    // Trả chi tiết một lớp cho quản trị viên hoặc TMO.
    @GetMapping("/admin/classes/{classId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TMO')")
    @Operation(summary = "Get class detail", tags = { "Admin Classes" })
    public ApiResponse<ClassResponse> getAdminClass(@PathVariable UUID classId) {
        return ApiResponse.success("Class loaded successfully", classAdminService.get(classId));
    }

    // Tạo lớp mới và trả URL của tài nguyên lớp vừa được tạo.
    @PostMapping("/admin/classes")
    @PreAuthorize("hasAnyRole('ADMIN', 'TMO')")
    @Operation(summary = "Create a class", tags = { "Admin Classes" })
    public ResponseEntity<ApiResponse<ClassResponse>> createClass(@Valid @RequestBody CreateClassRequest request) {
        ClassResponse created = classAdminService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/admin/classes/" + created.id()))
                .body(ApiResponse.success("Class created successfully", created));
    }

    // Cập nhật các trường lớp học được gửi trong yêu cầu PATCH.
    @PatchMapping("/admin/classes/{classId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TMO')")
    @Operation(summary = "Update selected class fields", tags = { "Admin Classes" })
    public ApiResponse<ClassResponse> updateClass(
            @PathVariable UUID classId,
            @Valid @RequestBody UpdateClassRequest request) {
        return ApiResponse.success("Class updated successfully", classAdminService.update(classId, request));
    }

    // Hủy lớp nhưng vẫn giữ lịch sử dữ liệu để có thể khôi phục.
    @PostMapping("/admin/classes/{classId}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN', 'TMO')")
    @Operation(summary = "Cancel a class without deleting history", tags = { "Admin Classes" })
    public ApiResponse<ClassResponse> cancelClass(@PathVariable UUID classId) {
        return ApiResponse.success("Class cancelled successfully", classAdminService.cancel(classId));
    }

    // Khôi phục lớp đã hủy với thông tin cập nhật được xác nhận.
    @PostMapping("/admin/classes/{classId}/restore")
    @PreAuthorize("hasAnyRole('ADMIN', 'TMO')")
    @Operation(summary = "Restore a cancelled class and recalculate its status", tags = { "Admin Classes" })
    public ApiResponse<ClassResponse> restoreClass(
            @PathVariable UUID classId,
            @Valid @RequestBody RestoreClassRequest request) {
        return ApiResponse.success("Class restored successfully", classAdminService.restore(classId, request));
    }

    // Xóa mềm lớp để giữ các dữ liệu lịch sử liên quan.
    @DeleteMapping("/admin/classes/{classId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TMO')")
    @Operation(summary = "Soft delete a class", tags = { "Admin Classes" })
    public ApiResponse<Void> deleteClass(@PathVariable UUID classId) {
        classAdminService.softDelete(classId);
        return ApiResponse.success("Class deleted successfully");
    }
}
