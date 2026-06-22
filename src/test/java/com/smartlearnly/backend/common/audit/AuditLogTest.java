package com.smartlearnly.backend.common.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuditLogTest {

    @Test
    void fromShouldMapAndSanitizeEventBeforePersistence() {
        AuditEvent event = AuditEvent.userEvent(
                UUID.randomUUID(),
                "admin@slp.vn",
                "ADMIN",
                AuditAction.PASSWORD_CHANGED,
                AuditDomain.AUTH,
                AuditResult.SUCCESS,
                "USER",
                "user-id",
                "Password was changed",
                Map.of("passwordHash", "old-hash"),
                Map.of("passwordHash", "new-hash"),
                Map.of("safe", true)
        );

        AuditLog auditLog = AuditLog.from(event, new AuditDataSanitizer());
        auditLog.prePersist();

        assertThat(auditLog.getOccurredAt()).isNotNull();
        assertThat(auditLog.getAction()).isEqualTo(AuditAction.PASSWORD_CHANGED);
        assertThat(auditLog.getOldValues())
                .containsEntry("passwordHash", AuditDataSanitizer.REDACTED_VALUE);
        assertThat(auditLog.getNewValues())
                .containsEntry("passwordHash", AuditDataSanitizer.REDACTED_VALUE);
        assertThat(auditLog.getMetadata()).containsEntry("safe", true);
    }
}
