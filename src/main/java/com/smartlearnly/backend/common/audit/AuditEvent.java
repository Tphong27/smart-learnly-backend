package com.smartlearnly.backend.common.audit;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record AuditEvent(
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
    public AuditEvent {
        if (actorType == null) {
            throw new IllegalArgumentException("Audit actor type is required");
        }
        if (action == null) {
            throw new IllegalArgumentException("Audit action is required");
        }
        if (domain == null) {
            throw new IllegalArgumentException("Audit domain is required");
        }
        if (result == null) {
            throw new IllegalArgumentException("Audit result is required");
        }
        if (summary == null || summary.isBlank()) {
            throw new IllegalArgumentException("Audit summary is required");
        }

        actorEmail = normalize(actorEmail);
        actorRole = normalize(actorRole);
        targetType = normalize(targetType);
        targetId = normalize(targetId);
        summary = summary.trim();
        oldValues = copy(oldValues);
        newValues = copy(newValues);
        metadata = copy(metadata);
        ipAddress = normalize(ipAddress);
        userAgent = normalize(userAgent);
        correlationId = normalize(correlationId);
        errorCode = normalize(errorCode);
    }

    public static AuditEvent userEvent(
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
            Map<String, Object> metadata
    ) {
        if (actorId == null) {
            throw new IllegalArgumentException("User audit actor id is required");
        }
        return new AuditEvent(
                AuditActorType.USER, actorId, actorEmail, actorRole,
                action, domain, result, targetType, targetId, summary,
                oldValues, newValues, metadata,
                null, null, null, null
        );
    }

    public static AuditEvent systemEvent(
            String systemName,
            AuditAction action,
            AuditDomain domain,
            AuditResult result,
            String targetType,
            String targetId,
            String summary,
            Map<String, Object> metadata,
            String correlationId,
            String errorCode
    ) {
        return nonUserEvent(
                AuditActorType.SYSTEM, systemName, action, domain, result,
                targetType, targetId, summary, metadata, correlationId, errorCode
        );
    }

    public static AuditEvent paymentProviderEvent(
            String providerName,
            AuditAction action,
            AuditResult result,
            String targetType,
            String targetId,
            String summary,
            Map<String, Object> metadata,
            String correlationId,
            String errorCode
    ) {
        return nonUserEvent(
                AuditActorType.PAYMENT_PROVIDER, providerName, action, AuditDomain.PAYMENT, result,
                targetType, targetId, summary, metadata, correlationId, errorCode
        );
    }

    private static AuditEvent nonUserEvent(
            AuditActorType actorType,
            String actorName,
            AuditAction action,
            AuditDomain domain,
            AuditResult result,
            String targetType,
            String targetId,
            String summary,
            Map<String, Object> metadata,
            String correlationId,
            String errorCode
    ) {
        if (actorName == null || actorName.isBlank()) {
            throw new IllegalArgumentException("Audit actor name is required");
        }
        return new AuditEvent(
                actorType, null, actorName, null,
                action, domain, result, targetType, targetId, summary,
                null, null, metadata,
                null, null, correlationId, errorCode
        );
    }

    private static Map<String, Object> copy(Map<String, Object> source) {
        return source == null ? null : new LinkedHashMap<>(source);
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
