package com.smartlearnly.backend.admin.settings.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.admin.settings.entity.SystemSetting;
import com.smartlearnly.backend.admin.settings.repository.SystemSettingRepository;
import com.smartlearnly.backend.assignment.ai.config.AssignmentAiDraftProperties;
import com.smartlearnly.backend.classroom.schedule.config.GoogleMeetProperties;
import com.smartlearnly.backend.payment.sepay.config.SePayProperties;
import com.smartlearnly.backend.question.image.QuestionImageImportProperties;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SystemSettingsServiceTest {
    @Mock
    private SystemSettingRepository repository;

    @Test
    void resolveSePayBankDisplaySettingsShouldUsePropertyFallbackWhenNoDbOverrideExists() {
        when(repository.findAll()).thenReturn(List.of());

        SystemSettingsService service = newService(sePayProperties("env-account", "Env Bank", "Env Account"));

        SystemSettingsService.SePayBankDisplaySettings settings = service.resolveSePayBankDisplaySettings();

        assertThat(settings.accountNumber()).isEqualTo("env-account");
        assertThat(settings.bankName()).isEqualTo("Env Bank");
        assertThat(settings.accountName()).isEqualTo("Env Account");
        assertThat(settings.isConfigured()).isTrue();
    }

    @Test
    void resolveSePayBankDisplaySettingsShouldPreferDbOverrides() {
        when(repository.findAll()).thenReturn(List.of(
                setting(SettingKeys.SEPAY_ACCOUNT_NUMBER, "db-account"),
                setting(SettingKeys.SEPAY_BANK_NAME, "DB Bank"),
                setting(SettingKeys.SEPAY_ACCOUNT_NAME, "DB Account")));

        SystemSettingsService service = newService(sePayProperties("env-account", "Env Bank", "Env Account"));

        SystemSettingsService.SePayBankDisplaySettings settings = service.resolveSePayBankDisplaySettings();

        assertThat(settings.accountNumber()).isEqualTo("db-account");
        assertThat(settings.bankName()).isEqualTo("DB Bank");
        assertThat(settings.accountName()).isEqualTo("DB Account");
        assertThat(settings.isConfigured()).isTrue();
    }

    @Test
    void resolveSePayBankDisplaySettingsShouldFallbackWhenDbOverrideIsBlank() {
        when(repository.findAll()).thenReturn(List.of(
                setting(SettingKeys.SEPAY_ACCOUNT_NUMBER, " "),
                setting(SettingKeys.SEPAY_BANK_NAME, "DB Bank")));

        SystemSettingsService service = newService(sePayProperties("env-account", "Env Bank", "Env Account"));

        SystemSettingsService.SePayBankDisplaySettings settings = service.resolveSePayBankDisplaySettings();

        assertThat(settings.accountNumber()).isEqualTo("env-account");
        assertThat(settings.bankName()).isEqualTo("DB Bank");
        assertThat(settings.accountName()).isEqualTo("Env Account");
        assertThat(settings.isConfigured()).isTrue();
    }

    @Test
    void resolveSePayRuntimeSettingsShouldUsePropertyFallbackWhenNoDbOverrideExists() {
        when(repository.findAll()).thenReturn(List.of());
        SePayProperties properties = sePayProperties("env-account", "Env Bank", "Env Account");
        properties.setApiToken("env-token");
        properties.setWebhookSecret("env-secret");

        SystemSettingsService service = newService(properties);
        SystemSettingsService.SePayRuntimeSettings settings = service.resolveSePayRuntimeSettings();

        assertThat(settings.apiToken()).isEqualTo("env-token");
        assertThat(settings.webhookSecret()).isEqualTo("env-secret");
        assertThat(settings.hasApiToken()).isTrue();
        assertThat(settings.hasWebhookSecret()).isTrue();
    }

    @Test
    void resolveSePayRuntimeSettingsShouldPreferDbOverrides() {
        when(repository.findAll()).thenReturn(List.of(
                setting(SettingKeys.SEPAY_API_TOKEN, "db-token"),
                setting(SettingKeys.SEPAY_WEBHOOK_SECRET, "db-secret")));
        SePayProperties properties = sePayProperties("env-account", "Env Bank", "Env Account");
        properties.setApiToken("env-token");
        properties.setWebhookSecret("env-secret");

        SystemSettingsService service = newService(properties);
        SystemSettingsService.SePayRuntimeSettings settings = service.resolveSePayRuntimeSettings();

        assertThat(settings.apiToken()).isEqualTo("db-token");
        assertThat(settings.webhookSecret()).isEqualTo("db-secret");
    }

    @Test
    void sePayBankDisplaySettingsShouldReportIncompleteConfig() {
        SystemSettingsService.SePayBankDisplaySettings settings =
                new SystemSettingsService.SePayBankDisplaySettings("123", "", "Smart Learnly");

        assertThat(settings.isConfigured()).isFalse();
    }

    private SystemSettingsService newService(SePayProperties sePayProperties) {
        return new SystemSettingsService(
                repository,
                new SettingsCipherService(""),
                "https://api.resend.com",
                "resend-key",
                "Smart Learnly <no-reply@mail.smartlearnly.online>",
                "google-client-id",
                "google-client-secret",
                new GoogleMeetProperties(),
                new QuestionImageImportProperties(),
                new AssignmentAiDraftProperties(),
                sePayProperties);
    }

    private SePayProperties sePayProperties(String accountNumber, String bankName, String accountName) {
        SePayProperties properties = new SePayProperties();
        properties.setAccountNumber(accountNumber);
        properties.setBankName(bankName);
        properties.setAccountName(accountName);
        return properties;
    }

    private SystemSetting setting(String key, String value) {
        return new SystemSetting(key, value, false, UUID.randomUUID());
    }
}
