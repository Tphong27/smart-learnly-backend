package com.smartlearnly.backend.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartlearnly.backend.notification.entity.Notification;
import jakarta.persistence.Column;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class NotificationMigrationContractTest {
    private static final Path MIGRATION =
            Path.of("src/main/resources/db/migration/V78__notification_foundation.sql");

    @Test
    void migrationShouldDefineNotificationFoundation() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertThat(sql).contains("CREATE TYPE public.notification_type");
        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS public.notifications");
        assertThat(sql).contains("id uuid PRIMARY KEY");
        assertThat(sql).contains("user_id uuid NOT NULL REFERENCES public.users(id)");
        assertThat(sql).contains("idx_notifications_user_created_at");
        assertThat(sql).contains("idx_notifications_user_unread_created_at");
        assertThat(sql).contains("uq_notifications_user_event_key");
        assertThat(sql).contains("ENABLE ROW LEVEL SECURITY");
    }

    @Test
    void entityShouldMapRequiredColumns() throws Exception {
        assertThat(Notification.class.getDeclaredField("userId").getAnnotation(Column.class).name())
                .isEqualTo("user_id");
        assertThat(Notification.class.getDeclaredField("referenceType").getAnnotation(Column.class).name())
                .isEqualTo("reference_type");
        assertThat(Notification.class.getDeclaredField("referenceId").getAnnotation(Column.class).name())
                .isEqualTo("reference_id");
        assertThat(Notification.class.getDeclaredField("actionUrl").getAnnotation(Column.class).name())
                .isEqualTo("action_url");
        assertThat(Notification.class.getDeclaredField("eventKey").getAnnotation(Column.class).name())
                .isEqualTo("event_key");
    }
}
