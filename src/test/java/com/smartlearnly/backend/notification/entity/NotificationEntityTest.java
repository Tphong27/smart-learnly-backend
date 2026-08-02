package com.smartlearnly.backend.notification.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NotificationEntityTest {

    @Test
    void prePersistShouldInitializeIdentityTimestampsAndPayload() {
        Notification notification = new Notification();
        notification.setPayload(null);

        notification.prePersist();

        assertThat(notification.getId()).isNotNull();
        assertThat(notification.getCreatedAt()).isNotNull();
        assertThat(notification.getUpdatedAt()).isNotNull();
        assertThat(notification.getDeliveredAt()).isNotNull();
        assertThat(notification.getPayload()).isEmpty();
    }

    @Test
    void prePersistShouldKeepExistingValuesWhenProvided() {
        Notification notification = new Notification();
        Instant createdAt = Instant.parse("2026-07-29T00:00:00Z");
        Instant deliveredAt = Instant.parse("2026-07-29T00:00:01Z");
        notification.setCreatedAt(createdAt);
        notification.setDeliveredAt(deliveredAt);
        notification.setPayload(Map.of("reference", "course"));

        notification.prePersist();

        assertThat(notification.getCreatedAt()).isEqualTo(createdAt);
        assertThat(notification.getDeliveredAt()).isEqualTo(deliveredAt);
        assertThat(notification.getPayload()).containsEntry("reference", "course");
        assertThat(notification.getUpdatedAt()).isNotNull();
    }

    @Test
    void preUpdateShouldRefreshUpdatedAtAndNormalizePayload() {
        Notification notification = new Notification();
        notification.setPayload(null);

        notification.preUpdate();

        assertThat(notification.getPayload()).isEmpty();
        assertThat(notification.getUpdatedAt()).isNotNull();
    }
}
