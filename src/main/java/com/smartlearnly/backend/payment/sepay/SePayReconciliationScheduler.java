package com.smartlearnly.backend.payment.sepay;

import com.smartlearnly.backend.admin.settings.service.SystemSettingsService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SePayReconciliationScheduler {
    private static final Logger log = LoggerFactory.getLogger(SePayReconciliationScheduler.class);

    private final SystemSettingsService systemSettingsService;
    private final SePayReconciliationService reconciliationService;

    @Scheduled(
            fixedDelayString = "${app.payment.sepay.reconciliation-interval:PT5M}",
            initialDelayString = "${app.payment.sepay.reconciliation-interval:PT5M}"
    )
    public void run() {
        if (!hasApiToken()) {
            log.info("SePay reconciliation skipped because API token is not configured");
            return;
        }
        reconciliationService.reconcile();
    }

    private boolean hasApiToken() {
        return systemSettingsService.resolveSePayRuntimeSettings().hasApiToken();
    }
}
