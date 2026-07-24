package com.smartlearnly.backend.admin.settings.service;

import com.smartlearnly.backend.admin.settings.entity.SystemSetting;
import com.smartlearnly.backend.admin.settings.repository.SystemSettingRepository;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Central read/write access to {@code system_settings}.
 *
 * <p>
 * Values are cached in-memory and the cache is evicted on every write so that
 * configuration changes take effect immediately without an application restart.
 * Secret values are encrypted at rest via {@link SettingsCipherService}.
 */
@Service
public class SystemSettingsService {
    /** Placeholder sent by the frontend to indicate "keep the existing secret". */
    public static final String SECRET_PLACEHOLDER = "********";

    private final SystemSettingRepository repository;
    private final SettingsCipherService cipher;
    private final Map<String, String> cache = new ConcurrentHashMap<>();
    private volatile boolean cacheLoaded = false;

    // Env fallbacks (used when DB has no override yet).
    private final String envResendApiUrl;
    private final String envResendApiKey;
    private final String envResendFromEmail;
    private final String envGoogleClientId;
    private final String envGoogleClientSecret;

    public SystemSettingsService(
            SystemSettingRepository repository,
            SettingsCipherService cipher,
            @Value("${app.resend.api-url:https://api.resend.com}") String envResendApiUrl,
            @Value("${app.resend.api-key:}") String envResendApiKey,
            @Value("${app.resend.from-email:Smart Learnly <no-reply@mail.smartlearnly.online>}") String envResendFromEmail,
            @Value("${app.auth.google-client-id:}") String envGoogleClientId,
            @Value("${app.auth.google-client-secret:}") String envGoogleClientSecret) {
        this.repository = repository;
        this.cipher = cipher;
        this.envResendApiUrl = envResendApiUrl;
        this.envResendApiKey = envResendApiKey;
        this.envResendFromEmail = envResendFromEmail;
        this.envGoogleClientId = envGoogleClientId;
        this.envGoogleClientSecret = envGoogleClientSecret;
    }

    public boolean secretStorageEnabled() {
        return cipher.isEnabled();
    }

    /** Raw decrypted value for a key, or {@code null} when unset. */
    public String getRawValue(String key) {
        ensureCacheLoaded();
        String stored = cache.get(key);
        if (stored == null) {
            return null;
        }
        return cipher.decrypt(stored);
    }

    public boolean hasValue(String key) {
        String value = getRawValue(key);
        return value != null && !value.isBlank();
    }

    /**
     * Returns the stored value if present and non-blank, otherwise
     * {@code fallback}.
     */
    public String getOrDefault(String key, String fallback) {
        String value = getRawValue(key);
        return (value == null || value.isBlank()) ? fallback : value;
    }

    /**
     * Upsert a single setting. When {@code value} is null/blank or equals the
     * secret placeholder, the existing value is kept untouched.
     */
    @Transactional
    public void put(String key, String value, boolean secret, UUID updatedBy) {
        if (value == null || value.isBlank() || SECRET_PLACEHOLDER.equals(value)) {
            return;
        }
        if (secret && !cipher.isEnabled()) {
            throw new IllegalStateException(
                    "Cannot store secret setting because the encryption key is not configured");
        }
        String stored = secret ? cipher.encrypt(value) : value;
        SystemSetting setting = repository.findById(key)
                .orElseGet(() -> new SystemSetting(key, null, secret, updatedBy));
        setting.setSettingValue(stored);
        setting.setSecret(secret);
        setting.setUpdatedBy(updatedBy);
        repository.save(setting);
        evictCache();
    }

    /** Resolve effective email settings (DB first, env fallback). */
    public EmailSettings resolveEmailSettings() {
        return new EmailSettings(
                getOrDefault(SettingKeys.EMAIL_API_URL, envResendApiUrl),
                getOrDefault(SettingKeys.EMAIL_API_KEY, envResendApiKey),
                getOrDefault(SettingKeys.EMAIL_FROM_NAME, null),
                getOrDefault(SettingKeys.EMAIL_FROM_EMAIL, null),
                getOrDefault(SettingKeys.EMAIL_REPLY_TO, null),
                envResendFromEmail);
    }

    /** Resolve effective Google OAuth settings (DB first, env fallback). */
    public GoogleOAuthSettings resolveGoogleSettings() {
        return new GoogleOAuthSettings(
                getOrDefault(SettingKeys.GOOGLE_CLIENT_ID, envGoogleClientId),
                getOrDefault(SettingKeys.GOOGLE_CLIENT_SECRET, envGoogleClientSecret),
                getOrDefault(SettingKeys.GOOGLE_SCOPE, "openid,profile,email"));
    }

    private synchronized void ensureCacheLoaded() {
        if (cacheLoaded) {
            return;
        }
        for (SystemSetting setting : repository.findAll()) {
            if (setting.getSettingValue() != null) {
                cache.put(setting.getSettingKey(), setting.getSettingValue());
            }
        }
        cacheLoaded = true;
    }

    private synchronized void evictCache() {
        cache.clear();
        cacheLoaded = false;
    }

    /**
     * Effective email configuration. {@code fromAddress()} resolves the final
     * Resend "from" header, preferring the configured name/email and falling back
     * to the env default.
     */
    public record EmailSettings(
            String apiUrl,
            String apiKey,
            String fromName,
            String fromEmail,
            String replyTo,
            String envFromAddress) {
        public String fromAddress() {
            if (fromEmail != null && !fromEmail.isBlank()) {
                if (fromName != null && !fromName.isBlank()) {
                    return fromName + " <" + fromEmail + ">";
                }
                return fromEmail;
            }
            return envFromAddress;
        }

        public boolean isConfigured() {
            return apiKey != null && !apiKey.isBlank();
        }
    }

    public record GoogleOAuthSettings(String clientId, String clientSecret, String scope) {
    }
}
