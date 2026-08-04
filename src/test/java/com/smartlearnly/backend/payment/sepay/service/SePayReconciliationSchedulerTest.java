package com.smartlearnly.backend.payment.sepay.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.admin.settings.service.SystemSettingsService;
import com.smartlearnly.backend.admin.settings.service.SystemSettingsService.SePayRuntimeSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SePayReconciliationSchedulerTest {
    @Mock
    private SePayReconciliationService reconciliationService;

    @Test
    void runShouldSkipWhenApiTokenIsBlank() {
        SystemSettingsService settingsService = mock(SystemSettingsService.class);
        when(settingsService.resolveSePayRuntimeSettings()).thenReturn(new SePayRuntimeSettings("", ""));
        SePayReconciliationScheduler scheduler = new SePayReconciliationScheduler(
                settingsService,
                reconciliationService
        );

        scheduler.run();

        verifyNoInteractions(reconciliationService);
    }

    @Test
    void runShouldInvokeReconciliationWhenApiTokenIsConfigured() {
        SystemSettingsService settingsService = mock(SystemSettingsService.class);
        when(settingsService.resolveSePayRuntimeSettings()).thenReturn(new SePayRuntimeSettings("fake-api-token", "secret"));
        SePayReconciliationScheduler scheduler = new SePayReconciliationScheduler(
                settingsService,
                reconciliationService
        );

        scheduler.run();

        verify(reconciliationService).reconcile();
    }
}
