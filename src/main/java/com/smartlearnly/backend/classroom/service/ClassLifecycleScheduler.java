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
@ConditionalOnProperty(prefix = "app.classroom.lifecycle", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.classroom.lifecycle.enabled", havingValue = "true", matchIfMissing = true)
public class ClassLifecycleScheduler {

    private final ClassLifecycleSynchronizationService synchronizationService;

    @EventListener(ApplicationReadyEvent.class)
    public void synchronizeAfterStartup() {
        synchronize("application startup");
    }

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