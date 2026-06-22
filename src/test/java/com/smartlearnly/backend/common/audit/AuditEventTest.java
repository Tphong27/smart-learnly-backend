package com.smartlearnly.backend.common.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuditEventTest {

    @Test
    void userEventShouldNormalizeFieldsAndDefensivelyCopyMaps() {
        UUID actorId = UUID.randomUUID();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("courseTitle", "Java");

        AuditEvent event = AuditEvent.userEvent(
                actorId,
                " admin@slp.vn ",
                " ADMIN ",
                AuditAction.COURSE_UPDATED,
                AuditDomain.COURSE,
                AuditResult.SUCCESS,
                " COURSE ",
                " course-id ",
                " Course information was updated ",
                Map.of("status", "draft"),
                Map.of("status", "published"),
                metadata
        );
        metadata.put("courseTitle", "Changed outside");

        assertThat(event.actorType()).isEqualTo(AuditActorType.USER);
        assertThat(event.actorId()).isEqualTo(actorId);
        assertThat(event.actorEmail()).isEqualTo("admin@slp.vn");
        assertThat(event.actorRole()).isEqualTo("ADMIN");
        assertThat(event.targetType()).isEqualTo("COURSE");
        assertThat(event.targetId()).isEqualTo("course-id");
        assertThat(event.summary()).isEqualTo("Course information was updated");
        assertThat(event.metadata()).containsEntry("courseTitle", "Java");
    }

    @Test
    void systemAndProviderFactoriesShouldNotRequireUserId() {
        AuditEvent system = AuditEvent.systemEvent(
                "sepay-reconciliation",
                AuditAction.PAYMENT_RECONCILED,
                AuditDomain.PAYMENT,
                AuditResult.SUCCESS,
                "TRANSACTION",
                "tx-1",
                "Payment was reconciled",
                Map.of("count", 1),
                "transaction:tx-1",
                null
        );
        AuditEvent provider = AuditEvent.paymentProviderEvent(
                "sepay",
                AuditAction.PAYMENT_CALLBACK_RECEIVED,
                AuditResult.SUCCESS,
                "TRANSACTION",
                "tx-1",
                "Payment callback was received",
                null,
                "sepay:100",
                null
        );

        assertThat(system.actorType()).isEqualTo(AuditActorType.SYSTEM);
        assertThat(system.actorId()).isNull();
        assertThat(provider.actorType()).isEqualTo(AuditActorType.PAYMENT_PROVIDER);
        assertThat(provider.domain()).isEqualTo(AuditDomain.PAYMENT);
    }

    @Test
    void eventShouldRejectMissingRequiredValues() {
        assertThatThrownBy(() -> AuditEvent.userEvent(
                null,
                "admin@slp.vn",
                "ADMIN",
                AuditAction.COURSE_UPDATED,
                AuditDomain.COURSE,
                AuditResult.SUCCESS,
                "COURSE",
                "id",
                "Updated",
                null,
                null,
                null
        )).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> AuditEvent.systemEvent(
                "system",
                AuditAction.PAYMENT_RECONCILED,
                AuditDomain.PAYMENT,
                AuditResult.SUCCESS,
                null,
                null,
                " ",
                null,
                null,
                null
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
