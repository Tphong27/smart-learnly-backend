package com.smartlearnly.backend.common.audit;

import java.time.Instant;
import java.util.UUID;

public record AuditLogSummaryResponse(
        UUID id,
        Instant occurredAt,
        AuditActorType actorType,
        UUID actorId,
        String actorEmail,
        String actorRole,
        AuditAction action,
        AuditDomain domain,
        AuditResult result,
        String targetType,
        String targetId,
        String summary,
        String correlationId,
        String errorCode
) {
    static AuditLogSummaryResponse from(AuditLog log) {
        return new AuditLogSummaryResponse(
                log.getId(), log.getOccurredAt(), log.getActorType(), log.getActorId(),
                log.getActorEmail(), log.getActorRole(), log.getAction(), log.getDomain(), log.getResult(),
                log.getTargetType(), log.getTargetId(), log.getSummary(), log.getCorrelationId(), log.getErrorCode()
        );
    }
}
