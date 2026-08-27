package com.smartlearnly.backend.admin.settings.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.admin.settings.entity.SystemSetting;
import com.smartlearnly.backend.admin.settings.repository.SystemSettingRepository;
import com.smartlearnly.backend.assignment.ai.config.AssignmentAiDraftProperties;
import com.smartlearnly.backend.classroom.schedule.config.GoogleMeetProperties;
import com.smartlearnly.backend.payment.sepay.config.SePayProperties;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link SystemSettingsService} covering the most complex
 * functions: secret encryption at rest, placeholder/blank write guards, cache
 * eviction and typed-value parsing. Pure Mockito/JUnit tests, no Spring context.
 */
@ExtendWith(MockitoExtension.class)
class SystemSettingsServiceUnitTest {

    @Mock
    private SystemSettingRepository repository;

    @Test
    void putShouldRejectSecretWhenEncryptionIsDisabled() {
        SystemSettingsService service = newService(new SettingsCipherService(""));

        assertThatThrownBy(() -> service.put("sepay.api_token", "token", true, UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("encryption key is not configured");

        verify(repository, never()).save(any());
    }

    @Test
    void putShouldSkipNullBlankAndPlaceholderValues() {
        SystemSettingsService service = newService(new SettingsCipherService(""));
        UUID updatedBy = UUID.randomUUID();

        service.put("key", null, false, updatedBy);
        service.put("key", "   ", false, updatedBy);
        service.put("key", SystemSettingsService.SECRET_PLACEHOLDER, false, updatedBy);

        verify(repository, never()).save(any());
    }

    @Test
    void putShouldEncryptSecretValueAndPersistEncrypted() {
        SettingsCipherService cipher = cipherWithKey();
        SystemSettingsService service = newService(cipher);
        when(repository.findById("sepay.api_token")).thenReturn(Optional.empty());
        when(repository.save(any(SystemSetting.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.put("sepay.api_token", "secret-token", true, UUID.randomUUID());

        ArgumentCaptor<SystemSetting> captor = ArgumentCaptor.forClass(SystemSetting.class);
        verify(repository).save(captor.capture());
        String stored = captor.getValue().getSettingValue();
        assertThat(stored).startsWith("enc:v1:");
        assertThat(cipher.decrypt(stored)).isEqualTo("secret-token");
        assertThat(captor.getValue().isSecret()).isTrue();
    }

    @Test
    void getRawValueShouldReturnNullWhenUnset() {
        SystemSettingsService service = newService(new SettingsCipherService(""));
        when(repository.findAll()).thenReturn(List.of());

        assertThat(service.getRawValue("missing")).isNull();
        assertThat(service.hasValue("missing")).isFalse();
    }

    @Test
    void putShouldEvictCacheSoNextReadSeesNewValue() {
        SystemSettingsService service = newService(new SettingsCipherService(""));
        SystemSetting oldSetting =
                new SystemSetting("email.api_url", "https://old.example.com", false, UUID.randomUUID());
        when(repository.findAll()).thenReturn(List.of(oldSetting));
        // Load the cache with the OLD value first so eviction actually matters.
        assertThat(service.getOrDefault("email.api_url", "fallback")).isEqualTo("https://old.example.com");

        when(repository.findById("email.api_url")).thenReturn(Optional.of(oldSetting));
        when(repository.save(any(SystemSetting.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        service.put("email.api_url", "https://db.example.com", false, UUID.randomUUID());

        // After eviction the next read must reload from the repository, not the stale cache.
        when(repository.findAll()).thenReturn(List.of(
                new SystemSetting("email.api_url", "https://db.example.com", false, UUID.randomUUID())));
        assertThat(service.getOrDefault("email.api_url", "fallback")).isEqualTo("https://db.example.com");
    }

    @Test
    void deleteShouldEvictCacheSoNextReadDoesNotSeeDeletedSetting() {
        SystemSettingsService service = newService(new SettingsCipherService(""));
        when(repository.findAll()).thenReturn(List.of(
                new SystemSetting("email.api_url", "https://db.example.com", false, UUID.randomUUID())));
        assertThat(service.getRawValue("email.api_url")).isEqualTo("https://db.example.com");

        service.delete("email.api_url");

        verify(repository).deleteById("email.api_url");
        when(repository.findAll()).thenReturn(List.of());
        assertThat(service.getRawValue("email.api_url")).isNull();
    }

    @Test
    void resolveGoogleMeetSettingsShouldParseTruthyBooleanStrings() {
        SystemSettingsService service = newService(new SettingsCipherService(""));
        when(repository.findAll()).thenReturn(List.of(
                new SystemSetting(SettingKeys.GOOGLE_MEET_ENABLED, "yes", false, UUID.randomUUID()),
                new SystemSetting(SettingKeys.GOOGLE_MEET_REFRESH_TOKEN, "refresh-1", false, UUID.randomUUID())));

        SystemSettingsService.GoogleMeetSettings settings = service.resolveGoogleMeetSettings();

        assertThat(settings.enabled()).isTrue();
        assertThat(settings.refreshToken()).isEqualTo("refresh-1");
    }

    @Test
    void resolveGoogleMeetSettingsShouldFallbackOnInvalidBooleanString() {
        GoogleMeetProperties properties = new GoogleMeetProperties();
        properties.setEnabled(true);
        SystemSettingsService service = newService(new SettingsCipherService(""), properties);
        when(repository.findAll()).thenReturn(List.of(
                new SystemSetting(SettingKeys.GOOGLE_MEET_ENABLED, "nonsense", false, UUID.randomUUID())));

        SystemSettingsService.GoogleMeetSettings settings = service.resolveGoogleMeetSettings();

        assertThat(settings.enabled()).isTrue();
    }

    @Test
    void emailSettingsFromAddressShouldComposeNameAndEmail() {
        SystemSettingsService.EmailSettings settings = new SystemSettingsService.EmailSettings(
                "https://api.resend.com", "key", "Smart Learnly",
                "no-reply@mail.smartlearnly.online", null, "fallback@example.com");

        assertThat(settings.fromAddress()).isEqualTo("Smart Learnly <no-reply@mail.smartlearnly.online>");
        assertThat(settings.isConfigured()).isTrue();
    }

    @Test
    void emailSettingsFromAddressShouldFallbackToEnvWhenDbEmailMissing() {
        SystemSettingsService.EmailSettings settings = new SystemSettingsService.EmailSettings(
                "https://api.resend.com", "key", null, "   ", null, "fallback@example.com");

        assertThat(settings.fromAddress()).isEqualTo("fallback@example.com");
        assertThat(settings.isConfigured()).isTrue();
    }

    private SystemSettingsService newService(SettingsCipherService cipher) {
        return newService(cipher, new GoogleMeetProperties());
    }

    private SystemSettingsService newService(SettingsCipherService cipher, GoogleMeetProperties googleMeetProperties) {
        return new SystemSettingsService(
                repository,
                cipher,
                "https://api.resend.com",
                "resend-key",
                "Smart Learnly <no-reply@mail.smartlearnly.online>",
                "google-client-id",
                "google-client-secret",
                googleMeetProperties,
                new AssignmentAiDraftProperties(),
                new SePayProperties());
    }

    private SettingsCipherService cipherWithKey() {
        return new SettingsCipherService(Base64.getEncoder().encodeToString(new byte[32]));
    }
}
