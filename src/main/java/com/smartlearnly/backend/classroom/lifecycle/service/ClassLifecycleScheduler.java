package com.smartlearnly.backend.classroom.lifecycle.service;

import com.smartlearnly.backend.classroom.entity.ClassLifecycle;
import com.smartlearnly.backend.classroom.repository.ClassOfferingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.classroom.lifecycle", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ClassLifecycleScheduler {

    private final ClassOfferingRepository classOfferingRepository;

    // Đồng bộ trạng thái lớp một lần sau khi ứng dụng khởi động.
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void synchronizeAfterStartup() {
        synchronize("application startup");
    }

    // Đồng bộ trạng thái lớp theo lịch chạy hằng ngày đã cấu hình.
    @Scheduled(cron = "${app.classroom.lifecycle.cron:0 0 0 * * *}", zone = "${app.classroom.lifecycle.zone:Asia/Ho_Chi_Minh}")
    @Transactional
    public void synchronizeEveryDay() {
        synchronize("daily schedule");
    }

    // Cập nhật trạng thái lifecycle trong database và ghi log kết quả chạy scheduler.
    private void synchronize(String source) {
        int updatedCount = classOfferingRepository.synchronizeLifecycleStatuses(ClassLifecycle.today());

        if (updatedCount > 0) {
            log.info("Class lifecycle synchronization updated {} class(es) from {}", updatedCount, source);
            return;
        }

        log.debug("Class lifecycle synchronization found no changes from {}", source);
    }
}
