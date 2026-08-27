package com.smartlearnly.backend.course.authoring.controller;

import com.smartlearnly.backend.common.api.ApiResponse;
import com.smartlearnly.backend.common.api.PageResponse;
import com.smartlearnly.backend.common.audit.AuditLogDetailResponse;
import com.smartlearnly.backend.common.audit.AuditLogQueryService;
import com.smartlearnly.backend.common.audit.AuditLogSummaryResponse;
import com.smartlearnly.backend.course.access.service.CourseAccessService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lịch sử thay đổi theo từng course — TMO/SME đọc; Trainer không.
 * Không mở lại global system activity log ({@code /admin/audit-logs} vẫn denyAll).
 */
@Validated
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('TMO', 'SME')")
@RequestMapping("/api/v1/admin/courses/{courseId}/change-history")
public class AdminCourseChangeHistoryController {
    private final AuditLogQueryService auditLogQueryService;
    private final CourseAccessService courseAccessService;

    // Timeline append-only các thay đổi gắn course (metadata.courseId hoặc target COURSE).
    @GetMapping
    public ApiResponse<PageResponse<AuditLogSummaryResponse>> list(
            @PathVariable UUID courseId,
            @RequestParam(required = false) @Size(max = 200) String keyword,
            @RequestParam(required = false) @Size(max = 100) String action,
            @RequestParam(required = false) @Size(max = 30) String actorRole,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        courseAccessService.requireReadableCourse(courseId);
        return ApiResponse.success(
                "Course change history loaded successfully",
                auditLogQueryService.listForCourse(courseId, keyword, action, actorRole, from, to, page, size));
    }

    // Chi tiết một bản ghi; chỉ trả nếu log thuộc đúng course (chống IDOR).
    @GetMapping("/{auditLogId}")
    public ApiResponse<AuditLogDetailResponse> get(
            @PathVariable UUID courseId, @PathVariable UUID auditLogId) {
        courseAccessService.requireReadableCourse(courseId);
        return ApiResponse.success(
                "Course change history entry loaded successfully",
                auditLogQueryService.getForCourse(courseId, auditLogId));
    }
}
