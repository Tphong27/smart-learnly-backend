package com.smartlearnly.backend.common.audit;

import com.smartlearnly.backend.user.entity.UserAccount;
import com.smartlearnly.backend.user.repository.UserRepository;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuditLogService {
    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    private final AuditLogRepository auditLogRepository;
    private final AuditDataSanitizer auditDataSanitizer;
    private final UserRepository userRepository;

    public void record(String actor, String action, String targetType, String targetId) {
        log.info("audit actor={} action={} targetType={} targetId={}", actor, action, targetType, targetId);

        try {
            AuditAction auditAction = AuditAction.valueOf(normalizeRequired(action, "action").toUpperCase(Locale.ROOT));
            String normalizedActor = normalize(actor);
            UserAccount actorAccount = normalizedActor == null
                    ? null
                    : userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(normalizedActor).orElse(null);

            AuditEvent event = new AuditEvent(
                    normalizedActor == null ? AuditActorType.SYSTEM : AuditActorType.USER,
                    actorAccount == null ? null : actorAccount.getId(),
                    actorAccount == null ? normalizedActor : actorAccount.getEmail(),
                    actorAccount == null ? null : actorAccount.getRole(),
                    auditAction,
                    deriveDomain(auditAction, targetType),
                    AuditResult.SUCCESS,
                    normalize(targetType),
                    normalize(targetId),
                    buildSummary(auditAction, targetType, targetId),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );

            auditLogRepository.save(AuditLog.from(event, auditDataSanitizer));
        }
        catch (RuntimeException exception) {
            log.warn(
                    "Could not persist audit event actor={} action={} targetType={} targetId={}",
                    actor,
                    action,
                    targetType,
                    targetId,
                    exception
            );
        }
    }

    private AuditDomain deriveDomain(AuditAction action, String targetType) {
        String actionName = action.name();
        if (actionName.startsWith("LOGIN")
                || actionName.startsWith("GOOGLE_LOGIN")
                || actionName.startsWith("LOGOUT")
                || actionName.startsWith("ACCOUNT")
                || actionName.startsWith("PASSWORD")
                || actionName.startsWith("EMAIL")) {
            return AuditDomain.AUTH;
        }
        if (actionName.startsWith("PROFILE")) {
            return AuditDomain.USER;
        }
        if (actionName.startsWith("USER")) {
            return AuditDomain.USER;
        }
        if (actionName.startsWith("CATEGORY")) {
            return AuditDomain.CATEGORY;
        }
        if (actionName.startsWith("COURSE")) {
            return AuditDomain.COURSE;
        }
        if (actionName.startsWith("SECTION") || actionName.startsWith("LESSON")) {
            return AuditDomain.CONTENT;
        }
        if (actionName.startsWith("CLASS")) {
            return AuditDomain.CLASS;
        }
        if (actionName.startsWith("ENROLLMENT")) {
            return AuditDomain.ENROLLMENT;
        }
        if (actionName.startsWith("ORDER")) {
            return AuditDomain.ORDER;
        }
        if (actionName.startsWith("PAYMENT")) {
            return AuditDomain.PAYMENT;
        }

        String normalizedTargetType = normalize(targetType);
        if (normalizedTargetType == null) {
            return AuditDomain.SYSTEM;
        }
        return switch (normalizedTargetType.toUpperCase(Locale.ROOT)) {
            case "USER" -> AuditDomain.USER;
            case "CATEGORY" -> AuditDomain.CATEGORY;
            case "COURSE" -> AuditDomain.COURSE;
            case "SECTION", "LESSON" -> AuditDomain.CONTENT;
            case "CLASS" -> AuditDomain.CLASS;
            case "ENROLLMENT" -> AuditDomain.ENROLLMENT;
            case "ORDER" -> AuditDomain.ORDER;
            case "PAYMENT", "TRANSACTION" -> AuditDomain.PAYMENT;
            default -> AuditDomain.SYSTEM;
        };
    }

    private String buildSummary(AuditAction action, String targetType, String targetId) {
        StringBuilder summary = new StringBuilder(action.name().toLowerCase(Locale.ROOT).replace('_', ' '));
        String normalizedTargetType = normalize(targetType);
        String normalizedTargetId = normalize(targetId);
        if (normalizedTargetType != null) {
            summary.append(" on ").append(normalizedTargetType);
        }
        if (normalizedTargetId != null) {
            summary.append(" #").append(normalizedTargetId);
        }
        return summary.length() <= 500 ? summary.toString() : summary.substring(0, 500);
    }

    private String normalizeRequired(String value, String fieldName) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new IllegalArgumentException("Audit " + fieldName + " is required");
        }
        return normalized;
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
