package com.smartlearnly.backend.dashboard.dto;

import com.smartlearnly.backend.common.audit.AuditLogSummaryResponse;
import java.time.Instant;
import java.util.UUID;

public record DashboardRecentActivityResponse(
        UUID id,
        Instant occurredAt,
        String actorEmail,
        String actorRole,
        String action,
        String domain,
        String result,
        String targetType,
        String targetId,
        String summary
) {
    public static DashboardRecentActivityResponse from(AuditLogSummaryResponse activity) {
        return new DashboardRecentActivityResponse(
                activity.id(),
                activity.occurredAt(),
                activity.actorEmail(),
                activity.actorRole(),
                activity.action(),
                activity.domain(),
                activity.result(),
                activity.targetType(),
                activity.targetId(),
                activity.summary()
        );
    }
}
