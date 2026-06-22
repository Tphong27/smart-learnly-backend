package com.smartlearnly.backend.common.audit;

import com.smartlearnly.backend.user.entity.UserAccount;
import com.smartlearnly.backend.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
@RequiredArgsConstructor
public class AuditLogService {
    private final AuditLogRepository auditLogRepository;
    private final AuditDataSanitizer sanitizer;
    private final UserRepository userRepository;

    @Transactional
    public AuditLog record(AuditEvent event) {
        return auditLogRepository.save(AuditLog.from(withRequestContext(event), sanitizer));
    }

    @Transactional
    public AuditLog recordUser(
            UserAccount actor,
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
        return record(AuditEvent.userEvent(
                actor.getId(), actor.getEmail(), actor.getRole(), action, domain, result,
                targetType, targetId, summary, oldValues, newValues, metadata
        ));
    }

    /** Backward-compatible bridge for existing business services. */
    @Transactional
    public void record(String actorEmail, String actionValue, String targetType, String targetId) {
        AuditAction action = AuditAction.valueOf(actionValue);
        AuditDomain domain = domainFor(action);
        UserAccount actor = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(actorEmail).orElse(null);
        record(new AuditEvent(
                AuditActorType.USER,
                actor == null ? null : actor.getId(),
                actorEmail,
                actor == null ? null : actor.getRole(),
                action,
                domain,
                AuditResult.SUCCESS,
                targetType,
                targetId,
                summaryFor(action),
                null,
                null,
                null,
                null,
                null,
                null,
                null
        ));
    }

    @Transactional
    public AuditLog recordAuthentication(
            UserAccount actor,
            String attemptedEmail,
            AuditAction action,
            AuditResult result,
            String summary,
            String ipAddress,
            String userAgent,
            String errorCode
    ) {
        return record(new AuditEvent(
                AuditActorType.USER,
                actor == null ? null : actor.getId(),
                actor == null ? attemptedEmail : actor.getEmail(),
                actor == null ? null : actor.getRole(),
                action,
                AuditDomain.AUTH,
                result,
                "USER",
                actor == null ? null : actor.getId().toString(),
                summary,
                null,
                null,
                null,
                ipAddress,
                userAgent,
                null,
                errorCode
        ));
    }

    @Transactional
    public AuditLog recordSystem(
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
        return record(AuditEvent.systemEvent(
                systemName, action, domain, result, targetType, targetId,
                summary, metadata, correlationId, errorCode
        ));
    }

    @Transactional
    public AuditLog recordPaymentProvider(
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
        return record(AuditEvent.paymentProviderEvent(
                providerName, action, result, targetType, targetId,
                summary, metadata, correlationId, errorCode
        ));
    }

    private AuditEvent withRequestContext(AuditEvent event) {
        HttpServletRequest request = currentRequest();
        if (request == null || (event.ipAddress() != null && event.userAgent() != null)) {
            return event;
        }
        return new AuditEvent(
                event.actorType(), event.actorId(), event.actorEmail(), event.actorRole(),
                event.action(), event.domain(), event.result(), event.targetType(), event.targetId(),
                event.summary(), event.oldValues(), event.newValues(), event.metadata(),
                event.ipAddress() == null ? clientIp(request) : event.ipAddress(),
                event.userAgent() == null ? request.getHeader("User-Agent") : event.userAgent(),
                event.correlationId(), event.errorCode()
        );
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",", 2)[0].trim();
        }
        return request.getRemoteAddr();
    }

    static AuditDomain domainFor(AuditAction action) {
        String value = action.name();
        if (value.startsWith("CATEGORY_")) return AuditDomain.CATEGORY;
        if (value.startsWith("COURSE_")) return AuditDomain.COURSE;
        if (value.startsWith("SECTION_") || value.startsWith("LESSON_")) return AuditDomain.CONTENT;
        if (value.startsWith("CLASS_")) return AuditDomain.CLASS;
        if (value.startsWith("ENROLLMENT_")) return AuditDomain.ENROLLMENT;
        if (value.startsWith("ORDER_")) return AuditDomain.ORDER;
        if (value.startsWith("PAYMENT_")) return AuditDomain.PAYMENT;
        if (value.equals("PROFILE_UPDATED")) return AuditDomain.USER;
        return AuditDomain.AUTH;
    }

    static String summaryFor(AuditAction action) {
        String normalized = action.name().toLowerCase().replace('_', ' ');
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }
}
