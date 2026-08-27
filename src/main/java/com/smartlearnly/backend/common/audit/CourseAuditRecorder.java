package com.smartlearnly.backend.common.audit;

import com.smartlearnly.backend.user.entity.UserAccount;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Ghi audit gắn course: luôn inject {@code metadata.courseId} (+ classId/scope khi có)
 * để timeline change-history lọc theo course. Chỉ scalar old/new — không full HTML.
 */
@Component
@RequiredArgsConstructor
public class CourseAuditRecorder {
    public static final String SCOPE_MASTER = "MASTER";
    public static final String SCOPE_CLASS = "CLASS";

    private final AuditLogService auditLogService;

    public void record(
            UserAccount actor,
            AuditAction action,
            String targetType,
            UUID targetId,
            UUID courseId,
            String summary,
            Map<String, Object> oldValues,
            Map<String, Object> newValues,
            Map<String, Object> extraMetadata) {
        record(
                actor,
                action,
                targetType,
                targetId == null ? null : targetId.toString(),
                courseId,
                null,
                SCOPE_MASTER,
                summary,
                oldValues,
                newValues,
                extraMetadata);
    }

    public void recordMaster(
            UserAccount actor,
            AuditAction action,
            String targetType,
            UUID targetId,
            UUID courseId,
            String summary) {
        record(actor, action, targetType, targetId, courseId, summary, null, null, null);
    }

    public void recordClassScoped(
            UserAccount actor,
            AuditAction action,
            String targetType,
            UUID targetId,
            UUID courseId,
            UUID classId,
            String summary,
            Map<String, Object> oldValues,
            Map<String, Object> newValues,
            Map<String, Object> extraMetadata) {
        record(
                actor,
                action,
                targetType,
                targetId == null ? null : targetId.toString(),
                courseId,
                classId,
                SCOPE_CLASS,
                summary,
                oldValues,
                newValues,
                extraMetadata);
    }

    public void record(
            UserAccount actor,
            AuditAction action,
            String targetType,
            String targetId,
            UUID courseId,
            UUID classId,
            String curriculumScope,
            String summary,
            Map<String, Object> oldValues,
            Map<String, Object> newValues,
            Map<String, Object> extraMetadata) {
        if (actor == null) {
            throw new IllegalArgumentException("Audit actor is required");
        }
        if (action == null) {
            throw new IllegalArgumentException("Audit action is required");
        }
        if (courseId == null) {
            throw new IllegalArgumentException("courseId is required for course-scoped audit");
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("courseId", courseId.toString());
        if (classId != null) {
            metadata.put("classId", classId.toString());
        }
        if (curriculumScope != null && !curriculumScope.isBlank()) {
            metadata.put("curriculumScope", curriculumScope.trim());
        }
        if (extraMetadata != null) {
            extraMetadata.forEach((key, value) -> {
                if (key != null && !key.isBlank() && value != null) {
                    metadata.putIfAbsent(key, value);
                }
            });
        }

        String resolvedSummary = (summary == null || summary.isBlank())
                ? AuditLogService.summaryFor(action)
                : summary.trim();

        auditLogService.recordUser(
                actor,
                action,
                AuditLogService.domainFor(action),
                AuditResult.SUCCESS,
                targetType,
                targetId,
                resolvedSummary,
                oldValues,
                newValues,
                metadata);
    }
}
