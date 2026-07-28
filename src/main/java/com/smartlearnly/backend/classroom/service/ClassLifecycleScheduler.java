package com.smartlearnly.backend.classroom.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.classroom.lifecycle.enabled", havingValue = "true", matchIfMissing = true)
public class ClassLifecycleScheduler {

    private final ClassLifecycleSynchronizationService synchronizationService;

    /*
     * Khắc phục status bị cũ nếu server ngừng hoạt động đúng thời điểm scheduled job hằng ngày đáng lẽ phải chạy.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void synchronizeAfterStartup() {
        synchronize("application startup");
    }

    /*
     * start_date và end_date chỉ có độ chính xác theo ngày.
     * => chỉ cần chạy một lần lúc 00:00 theo giờ Việt Nam.
     */
    @Scheduled(cron = "${app.classroom.lifecycle.cron:0 0 0 * * *}", zone = "${app.classroom.lifecycle.zone:Asia/Ho_Chi_Minh}")
    public void synchronizeEveryDay() {
        synchronize("daily schedule");
    }

    private void synchronize(String source) {
        int updatedCount = synchronizationService.synchronizeStatuses();
        if (updatedCount > 0) {
            log.info("Class lifecycle synchronization updated {} class(es) from {}", updatedCount, source);
        } else {
            log.debug("Class lifecycle synchronization found no changes from {}", source);
        }
    }
}