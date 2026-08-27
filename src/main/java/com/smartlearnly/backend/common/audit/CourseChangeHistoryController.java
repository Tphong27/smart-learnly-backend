package com.smartlearnly.backend.common.audit;

import com.smartlearnly.backend.common.api.ApiResponse;
import com.smartlearnly.backend.common.api.PageResponse;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.course.access.service.CourseAccessService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/courses/{courseId}/change-history")
@PreAuthorize("hasAnyRole('TMO', 'SME')")
public class CourseChangeHistoryController {

    private static final String COURSE_TARGET_TYPE = "COURSE";

    private final AuditLogQueryService auditLogQueryService;
    private final CourseAccessService courseAccessService;

    @GetMapping
    public ApiResponse<PageResponse<AuditLogSummaryResponse>> list(
            @PathVariable UUID courseId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String actorRole,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        courseAccessService.requireReadableCourse(courseId);

        PageResponse<AuditLogSummaryResponse> result = auditLogQueryService.listCourseChangeHistory(
                courseId,
                keyword,
                action,
                actorRole,
                from,
                to,
                page,
                size);

        return ApiResponse.success(
                "Course change history loaded successfully",
                result);
    }

    @GetMapping("/{auditLogId}")
    public ApiResponse<AuditLogDetailResponse> get(
            @PathVariable UUID courseId,
            @PathVariable UUID auditLogId) {
        courseAccessService.requireReadableCourse(courseId);

        AuditLogDetailResponse detail = auditLogQueryService.getCourseChangeHistoryDetail(
                courseId,
                auditLogId);

        return ApiResponse.success(
                "Course change history detail loaded successfully",
                detail);
    }
}