package com.smartlearnly.backend.classroom.controller;

import com.smartlearnly.backend.classroom.dto.ClassResponse;
import com.smartlearnly.backend.classroom.dto.ClassStatusOptionResponse;
import com.smartlearnly.backend.classroom.dto.CreateClassRequest;
import com.smartlearnly.backend.classroom.dto.UpdateClassRequest;
import com.smartlearnly.backend.classroom.service.ClassAdminService;
import com.smartlearnly.backend.classroom.service.ClassTrainerService;
import com.smartlearnly.backend.classroom.service.GoogleMeetService;
import com.smartlearnly.backend.common.api.ApiResponse;
import com.smartlearnly.backend.common.api.PageResponse;
import com.smartlearnly.backend.classroom.dto.MeetingUrlResponse;
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
public class ClassController {

    private final ClassAdminService classAdminService;
    private final ClassTrainerService classTrainerService;
    private final GoogleMeetService googleMeetService;

    /*
     * Admin/TMO endpoints
     */

    @GetMapping("/admin/classes/statuses")
    @PreAuthorize("hasAnyRole('ADMIN', 'TMO')")
    @Operation(summary = "List class status options", tags = { "Admin Classes" })
    public ApiResponse<List<ClassStatusOptionResponse>> listStatusOptions() {
        return ApiResponse.success("Class statuses loaded successfully", classAdminService.listStatusOptions());
    }

    @PostMapping("/admin/classes/meeting-links")
    @PreAuthorize("hasAnyRole('ADMIN', 'TMO')")
    @Operation(summary = "Generate a Google Meet link", tags = { "Admin Classes" })
    public ApiResponse<MeetingUrlResponse> generateMeetingUrl() {
        return ApiResponse.success("Google Meet link generated successfully", googleMeetService.createMeetingUrl());
    }

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
        return ApiResponse.success("Classes loaded successfully",
                classAdminService.list(courseId, trainerId, status, keyword, page, size));
    }

    @GetMapping("/admin/classes/{classId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TMO')")
    @Operation(summary = "Get class detail", tags = { "Admin Classes" })
    public ApiResponse<ClassResponse> getAdminClass(@PathVariable UUID classId) {
        return ApiResponse.success("Class loaded successfully", classAdminService.get(classId));
    }

    @PostMapping("/admin/classes")
    @PreAuthorize("hasAnyRole('ADMIN', 'TMO')")
    @Operation(summary = "Create a class", tags = { "Admin Classes" })
    public ResponseEntity<ApiResponse<ClassResponse>> createClass(@Valid @RequestBody CreateClassRequest request) {
        ClassResponse created = classAdminService.create(request);
        return ResponseEntity
                .created(URI.create("/api/v1/admin/classes/" + created.id()))
                .body(ApiResponse.success("Class created successfully", created));
    }

    @PatchMapping("/admin/classes/{classId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TMO')")
    @Operation(summary = "Update selected class fields", tags = { "Admin Classes" })
    public ApiResponse<ClassResponse> updateClass(@PathVariable UUID classId,
            @Valid @RequestBody UpdateClassRequest request) {
        return ApiResponse.success("Class updated successfully", classAdminService.update(classId, request));
    }

    @PostMapping("/admin/classes/{classId}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN', 'TMO')")
    @Operation(summary = "Cancel a class without deleting history", tags = { "Admin Classes" })
    public ApiResponse<ClassResponse> cancelClass(@PathVariable UUID classId) {
        return ApiResponse.success("Class cancelled successfully", classAdminService.cancel(classId));
    }

    @DeleteMapping("/admin/classes/{classId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TMO')")
    @Operation(summary = "Soft delete a class", tags = { "Admin Classes" })
    public ApiResponse<Void> deleteClass(@PathVariable UUID classId) {
        classAdminService.softDelete(classId);
        return ApiResponse.success("Class deleted successfully");
    }

    /*
     * Trainer endpoints
     */

    @GetMapping("/trainer/classes")
    @PreAuthorize("hasRole('TRAINER')")
    @Operation(summary = "List classes assigned to current trainer", tags = { "Trainer Classes" })
    public ApiResponse<PageResponse<ClassResponse>> listMyAssignedClasses(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success("Assigned classes loaded successfully",
                classTrainerService.listMyAssignedClasses(status, keyword, page, size));
    }

    @GetMapping("/trainer/classes/{classId}")
    @PreAuthorize("hasRole('TRAINER')")
    @Operation(summary = "Get assigned class detail", tags = { "Trainer Classes" })
    public ApiResponse<ClassResponse> getMyAssignedClassDetail(@PathVariable UUID classId) {
        return ApiResponse.success("Assigned class loaded successfully",
                classTrainerService.getMyAssignedClassDetail(classId));
    }
}