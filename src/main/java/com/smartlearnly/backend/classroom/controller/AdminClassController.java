package com.smartlearnly.backend.classroom.controller;

import com.smartlearnly.backend.classroom.dto.ClassResponse;
import com.smartlearnly.backend.classroom.dto.CreateClassRequest;
import com.smartlearnly.backend.classroom.dto.UpdateClassRequest;
import com.smartlearnly.backend.classroom.service.ClassAdminService;
import com.smartlearnly.backend.classroom.dto.ClassStatusOptionResponse;
import com.smartlearnly.backend.common.api.ApiResponse;
import com.smartlearnly.backend.common.api.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.UUID;
import java.util.List;
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
// Class-level: chỉ cho staff (ADMIN, TMO, SME, TRAINER) — trainee/khách bị chặn ngay tại đây;
// role write cụ thể (ADMIN/TMO) được siết thêm ở từng method.
@PreAuthorize("hasAnyRole('ADMIN', 'TMO', 'SME', 'TRAINER')")
@RequestMapping("/api/v1/admin/classes")
@Tag(name = "Admin Classes", description = "Admin and TMO class management APIs")
@SecurityRequirement(name = "bearerAuth")
public class AdminClassController {
    private final ClassAdminService classAdminService;

    @GetMapping("/statuses")
    @Operation(summary = "List class status options")
    public ApiResponse<List<ClassStatusOptionResponse>> listStatusOptions() {
        return ApiResponse.success(
                "Class statuses loaded successfully",
                classAdminService.listStatusOptions());
    }

    @GetMapping
    @Operation(summary = "List classes with filters")
    public ApiResponse<PageResponse<ClassResponse>> list(
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

    @GetMapping("/{classId}")
    @Operation(summary = "Get class detail")
    public ApiResponse<ClassResponse> get(@PathVariable UUID classId) {
        return ApiResponse.success("Class loaded successfully", classAdminService.get(classId));
    }

    @PostMapping
    @Operation(summary = "Create a class")
    @PreAuthorize("hasAnyRole('ADMIN', 'TMO')")
    public ResponseEntity<ApiResponse<ClassResponse>> create(
            @Valid @RequestBody CreateClassRequest request) {
        ClassResponse created = classAdminService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/admin/classes/" + created.id()))
                .body(ApiResponse.success("Class created successfully", created));
    }

    @PatchMapping("/{classId}")
    @Operation(summary = "Update selected class fields")
    @PreAuthorize("hasAnyRole('ADMIN', 'TMO')")
    public ApiResponse<ClassResponse> update(
            @PathVariable UUID classId,
            @Valid @RequestBody UpdateClassRequest request) {
        return ApiResponse.success("Class updated successfully", classAdminService.update(classId, request));
    }

    @PostMapping("/{classId}/cancel")
    @Operation(summary = "Cancel a class without deleting history")
    @PreAuthorize("hasAnyRole('ADMIN', 'TMO')")
    public ApiResponse<ClassResponse> cancel(@PathVariable UUID classId) {
        return ApiResponse.success("Class cancelled successfully", classAdminService.cancel(classId));
    }

    @DeleteMapping("/{classId}")
    @Operation(summary = "Soft delete a class")
    @PreAuthorize("hasAnyRole('ADMIN', 'TMO')")
    public ApiResponse<Void> delete(@PathVariable UUID classId) {
        classAdminService.softDelete(classId);
        return ApiResponse.success("Class deleted successfully");
    }
}
