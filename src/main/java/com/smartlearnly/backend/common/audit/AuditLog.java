package com.smartlearnly.backend.common.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "audit_logs", schema = "public")
public class AuditLog {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, length = 30, updatable = false)
    private AuditActorType actorType;

    @Column(name = "actor_id", updatable = false)
    private UUID actorId;

    @Column(name = "actor_email", length = 255, updatable = false)
    private String actorEmail;

    @Column(name = "actor_role", length = 30, updatable = false)
    private String actorRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 100, updatable = false)
    private AuditAction action;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50, updatable = false)
    private AuditDomain domain;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private AuditResult result;

    @Column(name = "target_type", length = 50, updatable = false)
    private String targetType;

    @Column(name = "target_id", length = 100, updatable = false)
    private String targetId;

    @Column(nullable = false, length = 500, updatable = false)
    private String summary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "old_values", columnDefinition = "jsonb", updatable = false)
    private Map<String, Object> oldValues;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "new_values", columnDefinition = "jsonb", updatable = false)
    private Map<String, Object> newValues;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", updatable = false)
    private Map<String, Object> metadata;

    @Column(name = "ip_address", length = 45, updatable = false)
    private String ipAddress;

    @Column(name = "user_agent", length = 500, updatable = false)
    private String userAgent;

    @Column(name = "correlation_id", length = 100, updatable = false)
    private String correlationId;

    @Column(name = "error_code", length = 100, updatable = false)
    private String errorCode;

    public static AuditLog from(AuditEvent event, AuditDataSanitizer sanitizer) {
        AuditLog auditLog = new AuditLog();
        auditLog.setActorType(event.actorType());
        auditLog.setActorId(event.actorId());
        auditLog.setActorEmail(event.actorEmail());
        auditLog.setActorRole(event.actorRole());
        auditLog.setAction(event.action());
        auditLog.setDomain(event.domain());
        auditLog.setResult(event.result());
        auditLog.setTargetType(event.targetType());
        auditLog.setTargetId(event.targetId());
        auditLog.setSummary(event.summary());
        auditLog.setOldValues(sanitizer.sanitizeMap(event.oldValues()));
        auditLog.setNewValues(sanitizer.sanitizeMap(event.newValues()));
        auditLog.setMetadata(sanitizer.sanitizeMap(event.metadata()));
        auditLog.setIpAddress(event.ipAddress());
        auditLog.setUserAgent(event.userAgent());
        auditLog.setCorrelationId(event.correlationId());
        auditLog.setErrorCode(event.errorCode());
        return auditLog;
    }

    @PrePersist
    void prePersist() {
        if (occurredAt == null) {
            occurredAt = Instant.now();
        }
        oldValues = copy(oldValues);
        newValues = copy(newValues);
        metadata = copy(metadata);
    }

    private Map<String, Object> copy(Map<String, Object> source) {
        return source == null ? null : new LinkedHashMap<>(source);
    }
}
