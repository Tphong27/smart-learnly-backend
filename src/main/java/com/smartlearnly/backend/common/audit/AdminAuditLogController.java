package com.smartlearnly.backend.common.audit;

import com.smartlearnly.backend.common.api.ApiResponse;
import com.smartlearnly.backend.common.api.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
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
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/api/v1/admin/audit-logs")
@Tag(name = "Admin Audit Logs", description = "Administrator system activity audit APIs.")
@SecurityRequirement(name = "bearerAuth")
public class AdminAuditLogController {
    private final AuditLogQueryService auditLogQueryService;

    @GetMapping
    @Operation(summary = "List system activity audit logs")
    public ApiResponse<PageResponse<AuditLogResponse>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String domain,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) String actorRole,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String targetId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ApiResponse.success(
                "Audit logs loaded successfully",
                auditLogQueryService.list(keyword, domain, action, result, actorRole, targetType, targetId, from, to, page, size)
        );
    }

    @GetMapping("/{auditLogId}")
    @Operation(summary = "Get system activity audit detail")
    public ApiResponse<AuditLogResponse> get(@PathVariable UUID auditLogId) {
        return ApiResponse.success("Audit log loaded successfully", auditLogQueryService.get(auditLogId));
    }
}
