package com.smartlearnly.backend.common.audit;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditLogDetailResponse(
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
        Map<String, Object> oldValues,
        Map<String, Object> newValues,
        Map<String, Object> metadata,
        String ipAddress,
        String userAgent,
        String correlationId,
        String errorCode
) {
    static AuditLogDetailResponse from(AuditLog log) {
        return new AuditLogDetailResponse(
                log.getId(), log.getOccurredAt(), log.getActorType(), log.getActorId(),
                log.getActorEmail(), log.getActorRole(), log.getAction(), log.getDomain(), log.getResult(),
                log.getTargetType(), log.getTargetId(), log.getSummary(), log.getOldValues(), log.getNewValues(),
                log.getMetadata(), log.getIpAddress(), log.getUserAgent(), log.getCorrelationId(), log.getErrorCode()
        );
    }
}
