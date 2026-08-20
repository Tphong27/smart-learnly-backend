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
public class AdminClassController {
    private final ClassAdminService classAdminService;
    private final GoogleMeetService googleMeetService;

    // Tr? các tr?ng thái l?p mà qu?n tr? viên đư?c phép ch?n trong màn qu?n l?.
    @GetMapping("/admin/classes/statuses")
    @PreAuthorize("hasRole('TMO')")
    public ApiResponse<List<ClassStatusOptionResponse>> listStatusOptions() {
        return ApiResponse.success("Class statuses loaded successfully", classAdminService.listStatusOptions());
    }

    // T?o link Google Meet m?i đ? qu?n tr? viên g?n vào l?p h?c.
    @PostMapping("/admin/classes/meeting-links")
    @PreAuthorize("hasRole('TMO')")
    public ApiResponse<MeetingUrlResponse> generateMeetingUrl() {
        return ApiResponse.success("Google Meet link generated successfully", googleMeetService.createMeetingUrl());
    }

    // Li?t kê l?p h?c theo các b? l?c qu?n tr? và phân trang hi?n t?i.
    @GetMapping("/admin/classes")
    @PreAuthorize("hasRole('TMO')")
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

    // Tr? chi ti?t m?t l?p cho qu?n tr? viên ho?c TMO.
    @GetMapping("/admin/classes/{classId}")
    @PreAuthorize("hasRole('TMO')")
    public ApiResponse<ClassResponse> getAdminClass(@PathVariable UUID classId) {
        return ApiResponse.success("Class loaded successfully", classAdminService.get(classId));
    }

    // T?o l?p m?i và tr? URL c?a tài nguyên l?p v?a đư?c t?o.
    @PostMapping("/admin/classes")
    @PreAuthorize("hasRole('TMO')")
    public ResponseEntity<ApiResponse<ClassResponse>> createClass(@Valid @RequestBody CreateClassRequest request) {
        ClassResponse created = classAdminService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/admin/classes/" + created.id()))
                .body(ApiResponse.success("Class created successfully", created));
    }

    // C?p nh?t các trư?ng l?p h?c đư?c g?i trong yêu c?u PATCH.
    @PatchMapping("/admin/classes/{classId}")
    @PreAuthorize("hasRole('TMO')")
    public ApiResponse<ClassResponse> updateClass(
            @PathVariable UUID classId,
            @Valid @RequestBody UpdateClassRequest request) {
        return ApiResponse.success("Class updated successfully", classAdminService.update(classId, request));
    }

    // H?y l?p nhưng v?n gi? l?ch s? d? li?u đ? có th? khôi ph?c.
    @PostMapping("/admin/classes/{classId}/cancel")
    @PreAuthorize("hasRole('TMO')")
    public ApiResponse<ClassResponse> cancelClass(@PathVariable UUID classId) {
        return ApiResponse.success("Class cancelled successfully", classAdminService.cancel(classId));
    }

    // Khôi ph?c l?p đ? h?y v?i thông tin c?p nh?t đư?c xác nh?n.
    @PostMapping("/admin/classes/{classId}/restore")
    @PreAuthorize("hasRole('TMO')")
    public ApiResponse<ClassResponse> restoreClass(
            @PathVariable UUID classId,
            @Valid @RequestBody RestoreClassRequest request) {
        return ApiResponse.success("Class restored successfully", classAdminService.restore(classId, request));
    }

    // Xóa m?m l?p đ? gi? các d? li?u l?ch s? liên quan.
    @DeleteMapping("/admin/classes/{classId}")
    @PreAuthorize("hasRole('TMO')")
    public ApiResponse<Void> deleteClass(@PathVariable UUID classId) {
        classAdminService.softDelete(classId);
        return ApiResponse.success("Class deleted successfully");
    }
}
