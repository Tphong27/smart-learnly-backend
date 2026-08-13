package com.smartlearnly.backend.notification.service;

import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.notification.retention", name = "enabled", havingValue = "true", matchIfMissing = true)
public class NotificationRetentionScheduler {
    private final NotificationService notificationService;

    @Value("${app.notification.retention.days:90}")
    private int retentionDays;

    /** Xóa định kỳ các notification đã đọc vượt quá thời hạn lưu giữ. */
    @Scheduled(
            cron = "${app.notification.retention.cron:0 30 2 * * *}",
            zone = "${app.notification.retention.zone:Asia/Ho_Chi_Minh}")
    public void cleanup() {
        int days = Math.max(1, retentionDays);
        Instant cutoff = Instant.now().minus(Duration.ofDays(days));
        int deleted = notificationService.cleanupReadCreatedBefore(cutoff);
        if (deleted > 0) {
            log.info("Notification retention cleanup deleted {} read notification(s) older than {} day(s)",
                    deleted, days);
        }
    }
}
