package com.smartlearnly.backend.admin.settings.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.admin.settings.dto.SePayBankDisplaySettingsResponse;
import com.smartlearnly.backend.admin.settings.dto.SePayBankDisplaySettingsUpdateRequest;
import com.smartlearnly.backend.admin.settings.dto.SePayRuntimeSettingsResponse;
import com.smartlearnly.backend.admin.settings.dto.SePayRuntimeSettingsUpdateRequest;
import com.smartlearnly.backend.admin.settings.service.SettingKeys;
import com.smartlearnly.backend.admin.settings.service.SystemSettingsService;
import com.smartlearnly.backend.admin.settings.service.SystemSettingsService.SePayBankDisplaySettings;
import com.smartlearnly.backend.admin.settings.service.SystemSettingsService.SePayRuntimeSettings;
import com.smartlearnly.backend.auth.service.EmailService;
import com.smartlearnly.backend.common.api.ApiResponse;
import com.smartlearnly.backend.common.audit.AuditLogService;
import com.smartlearnly.backend.payment.sepay.SePayReconciliationService;
import com.smartlearnly.backend.common.security.AuthenticatedUserResolver;
import com.smartlearnly.backend.common.security.CurrentUser;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminSettingsControllerTest {
    @Mock
    private SystemSettingsService settingsService;

    @Mock
    private EmailService emailService;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private AuthenticatedUserResolver authenticatedUserResolver;

    @Mock
    private SePayReconciliationService sePayReconciliationService;

    private AdminSettingsController controller;
    private UUID adminId;

    @BeforeEach
    void setUp() {
        controller = new AdminSettingsController(
                settingsService,
                emailService,
                auditLogService,
                authenticatedUserResolver,
                sePayReconciliationService);
        adminId = UUID.randomUUID();
    }

    @Test
    void getSePayBankDisplaySettingsShouldReturnEffectiveValues() {
        when(settingsService.resolveSePayBankDisplaySettings())
                .thenReturn(new SePayBankDisplaySettings("123456789", "MBBank", "SMART LEARNLY"));

        ApiResponse<SePayBankDisplaySettingsResponse> response = controller.getSePayBankDisplaySettings();

        assertThat(response.success()).isTrue();
        assertThat(response.data().accountNumber()).isEqualTo("123456789");
        assertThat(response.data().bankName()).isEqualTo("MBBank");
        assertThat(response.data().accountName()).isEqualTo("SMART LEARNLY");
        assertThat(response.data().configured()).isTrue();
    }

    @Test
    void getSePayRuntimeSettingsShouldReturnSecretFlagsOnly() {
        when(settingsService.resolveSePayRuntimeSettings())
                .thenReturn(new SePayRuntimeSettings("token", "secret"));
        when(settingsService.hasValue(SettingKeys.SEPAY_API_TOKEN)).thenReturn(true);
        when(settingsService.hasValue(SettingKeys.SEPAY_WEBHOOK_SECRET)).thenReturn(false);

        ApiResponse<SePayRuntimeSettingsResponse> response = controller.getSePayRuntimeSettings();

        assertThat(response.success()).isTrue();
        assertThat(response.data().hasApiToken()).isTrue();
        assertThat(response.data().hasWebhookSecret()).isTrue();
        assertThat(response.data().hasApiTokenOverride()).isTrue();
        assertThat(response.data().hasWebhookSecretOverride()).isFalse();
    }

    @Test
    void updateSePayBankDisplaySettingsShouldStoreNonSecretOverridesAndAudit() {
        when(authenticatedUserResolver.resolve())
                .thenReturn(Optional.of(new CurrentUser(adminId, null, "admin@smartlearnly.test", Set.of("ADMIN"))));
        when(settingsService.resolveSePayBankDisplaySettings())
                .thenReturn(new SePayBankDisplaySettings("123456789", "MBBank", "SMART LEARNLY"));

        ApiResponse<SePayBankDisplaySettingsResponse> response = controller.updateSePayBankDisplaySettings(
                new SePayBankDisplaySettingsUpdateRequest(" 123456789 ", " MBBank ", " SMART LEARNLY "));

        assertThat(response.success()).isTrue();
        verify(settingsService).put(SettingKeys.SEPAY_ACCOUNT_NUMBER, "123456789", false, adminId);
        verify(settingsService).put(SettingKeys.SEPAY_BANK_NAME, "MBBank", false, adminId);
        verify(settingsService).put(SettingKeys.SEPAY_ACCOUNT_NAME, "SMART LEARNLY", false, adminId);
        verify(auditLogService).record(
                "admin@smartlearnly.test",
                "SETTINGS_UPDATE_SEPAY_BANK_DISPLAY",
                "system_settings",
                "payment.sepay.bank_display");
    }

    @Test
    void updateSePayRuntimeSettingsShouldStoreSecretsAndAudit() {
        when(authenticatedUserResolver.resolve())
                .thenReturn(Optional.of(new CurrentUser(adminId, null, "admin@smartlearnly.test", Set.of("ADMIN"))));
        when(settingsService.resolveSePayRuntimeSettings())
                .thenReturn(new SePayRuntimeSettings("token", "secret"));
        when(settingsService.hasValue(SettingKeys.SEPAY_API_TOKEN)).thenReturn(true);
        when(settingsService.hasValue(SettingKeys.SEPAY_WEBHOOK_SECRET)).thenReturn(true);

        ApiResponse<SePayRuntimeSettingsResponse> response = controller.updateSePayRuntimeSettings(
                new SePayRuntimeSettingsUpdateRequest("new-token", "new-secret"));

        assertThat(response.success()).isTrue();
        verify(settingsService).put(SettingKeys.SEPAY_API_TOKEN, "new-token", true, adminId);
        verify(settingsService).put(SettingKeys.SEPAY_WEBHOOK_SECRET, "new-secret", true, adminId);
        verify(auditLogService).record(
                "admin@smartlearnly.test",
                "SETTINGS_UPDATE_SEPAY_RUNTIME",
                "system_settings",
                "payment.sepay.runtime");
    }

    @Test
    void runSePayReconciliationNowShouldDelegateAndAudit() {
        when(authenticatedUserResolver.resolve())
                .thenReturn(Optional.of(new CurrentUser(adminId, null, "admin@smartlearnly.test", Set.of("ADMIN"))));
        when(sePayReconciliationService.reconcileNow())
                .thenReturn(new SePayReconciliationService.ReconciliationSummary(3, 3, 1, 0, 0));

        ApiResponse<?> response = controller.runSePayReconciliationNow();

        assertThat(response.success()).isTrue();
        assertThat(response.message()).isEqualTo("SePay reconciliation completed");
        verify(sePayReconciliationService).reconcileNow();
        verify(auditLogService).record(
                "admin@smartlearnly.test",
                "PAYMENT_RECONCILED",
                "sepay_reconciliation",
                "manual");
    }
}
