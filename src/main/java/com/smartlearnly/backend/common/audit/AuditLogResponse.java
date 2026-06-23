package com.smartlearnly.backend.common.audit;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record AuditLogResponse(
        UUID id,
        Instant occurredAt,
        String actorType,
        UUID actorId,
        String actorEmail,
        String actorRole,
        String action,
        String domain,
        String result,
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
    public static AuditLogResponse from(AuditLog auditLog) {
        return new AuditLogResponse(
                auditLog.getId(),
                auditLog.getOccurredAt(),
                enumName(auditLog.getActorType()),
                auditLog.getActorId(),
                auditLog.getActorEmail(),
                auditLog.getActorRole(),
                enumName(auditLog.getAction()),
                enumName(auditLog.getDomain()),
                enumName(auditLog.getResult()),
                auditLog.getTargetType(),
                auditLog.getTargetId(),
                auditLog.getSummary(),
                copy(auditLog.getOldValues()),
                copy(auditLog.getNewValues()),
                copy(auditLog.getMetadata()),
                auditLog.getIpAddress(),
                auditLog.getUserAgent(),
                auditLog.getCorrelationId(),
                auditLog.getErrorCode()
        );
    }

    private static String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private static Map<String, Object> copy(Map<String, Object> source) {
        return source == null ? null : new LinkedHashMap<>(source);
    }
}
