package com.smartlearnly.backend.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationRetentionSchedulerTest {
    @Mock
    private NotificationService notificationService;

    @Test
    void cleanupShouldUseAtLeastOneDayRetention() throws Exception {
        NotificationRetentionScheduler scheduler = new NotificationRetentionScheduler(notificationService);
        setRetentionDays(scheduler, 0);
        when(notificationService.cleanupReadOrArchivedCreatedBefore(any(Instant.class))).thenReturn(2);

        scheduler.cleanup();

        ArgumentCaptor<Instant> cutoffCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(notificationService).cleanupReadOrArchivedCreatedBefore(cutoffCaptor.capture());
        assertThat(cutoffCaptor.getValue()).isBefore(Instant.now());
    }

    private void setRetentionDays(NotificationRetentionScheduler scheduler, int days) throws Exception {
        Field field = NotificationRetentionScheduler.class.getDeclaredField("retentionDays");
        field.setAccessible(true);
        field.setInt(scheduler, days);
    }
}
