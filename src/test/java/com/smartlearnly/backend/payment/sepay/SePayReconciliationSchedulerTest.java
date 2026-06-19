package com.smartlearnly.backend.payment.sepay;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

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
        SePayProperties sePayProperties = new SePayProperties();
        SePayReconciliationScheduler scheduler = new SePayReconciliationScheduler(
                sePayProperties,
                reconciliationService
        );

        scheduler.run();

        verifyNoInteractions(reconciliationService);
    }

    @Test
    void runShouldInvokeReconciliationWhenApiTokenIsConfigured() {
        SePayProperties sePayProperties = new SePayProperties();
        sePayProperties.setApiToken("fake-api-token");
        SePayReconciliationScheduler scheduler = new SePayReconciliationScheduler(
                sePayProperties,
                reconciliationService
        );

        scheduler.run();

        verify(reconciliationService).reconcile();
    }
}
