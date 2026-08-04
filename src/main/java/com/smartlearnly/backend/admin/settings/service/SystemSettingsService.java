package com.smartlearnly.backend.admin.settings.service;

import com.smartlearnly.backend.admin.settings.entity.SystemSetting;
import com.smartlearnly.backend.admin.settings.repository.SystemSettingRepository;
import com.smartlearnly.backend.assignment.ai.config.AssignmentAiDraftProperties;
import com.smartlearnly.backend.classroom.schedule.config.GoogleMeetProperties;
import com.smartlearnly.backend.payment.sepay.config.SePayProperties;
import com.smartlearnly.backend.question.image.QuestionImageImportProperties;
import java.time.Duration;
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
    private final GoogleMeetProperties googleMeetProperties;
    private final QuestionImageImportProperties questionImageImportProperties;
    private final AssignmentAiDraftProperties assignmentAiDraftProperties;
    private final SePayProperties sePayProperties;

    public SystemSettingsService(
            SystemSettingRepository repository,
            SettingsCipherService cipher,
            @Value("${app.resend.api-url:https://api.resend.com}") String envResendApiUrl,
            @Value("${app.resend.api-key:}") String envResendApiKey,
            @Value("${app.resend.from-email:Smart Learnly <no-reply@mail.smartlearnly.online>}") String envResendFromEmail,
            @Value("${app.auth.google-client-id:}") String envGoogleClientId,
            @Value("${app.auth.google-client-secret:}") String envGoogleClientSecret,
            GoogleMeetProperties googleMeetProperties,
            QuestionImageImportProperties questionImageImportProperties,
            AssignmentAiDraftProperties assignmentAiDraftProperties,
            SePayProperties sePayProperties) {
        this.repository = repository;
        this.cipher = cipher;
        this.envResendApiUrl = envResendApiUrl;
        this.envResendApiKey = envResendApiKey;
        this.envResendFromEmail = envResendFromEmail;
        this.envGoogleClientId = envGoogleClientId;
        this.envGoogleClientSecret = envGoogleClientSecret;
        this.googleMeetProperties = googleMeetProperties;
        this.questionImageImportProperties = questionImageImportProperties;
        this.assignmentAiDraftProperties = assignmentAiDraftProperties;
        this.sePayProperties = sePayProperties;
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

    @Transactional
    public void delete(String key) {
        repository.deleteById(key);
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

    public GoogleMeetSettings resolveGoogleMeetSettings() {
        return new GoogleMeetSettings(
                getBooleanOrDefault(SettingKeys.GOOGLE_MEET_ENABLED, googleMeetProperties.isEnabled()),
                getOrDefault(SettingKeys.GOOGLE_MEET_REFRESH_TOKEN, googleMeetProperties.getRefreshToken()));
    }

    public QuestionImageImportSettings resolveQuestionImageImportSettings() {
        return new QuestionImageImportSettings(
                getBooleanOrDefault(SettingKeys.QUESTION_IMAGE_IMPORT_ENABLED, questionImageImportProperties.isEnabled()),
                getOrDefault(SettingKeys.QUESTION_IMAGE_IMPORT_PROVIDER, questionImageImportProperties.getProvider()),
                getOrDefault(SettingKeys.QUESTION_IMAGE_IMPORT_API_KEY, questionImageImportProperties.getApiKey()),
                getOrDefault(SettingKeys.QUESTION_IMAGE_IMPORT_MODEL, questionImageImportProperties.getModel()),
                getLongOrDefault(SettingKeys.QUESTION_IMAGE_IMPORT_TIMEOUT_SECONDS, questionImageImportProperties.getTimeout().toSeconds()),
                getIntOrDefault(SettingKeys.QUESTION_IMAGE_IMPORT_MAX_FILE_SIZE_MB, Math.toIntExact(questionImageImportProperties.getMaxFileSize().toMegabytes())),
                getIntOrDefault(SettingKeys.QUESTION_IMAGE_IMPORT_MAX_FILES, questionImageImportProperties.getMaxFiles()));
    }

    public AssignmentAiSettings resolveAssignmentAiSettings() {
        return new AssignmentAiSettings(
                getBooleanOrDefault(SettingKeys.ASSIGNMENT_AI_ENABLED, assignmentAiDraftProperties.isEnabled()),
                getOrDefault(SettingKeys.ASSIGNMENT_AI_PROVIDER, assignmentAiDraftProperties.getProvider()),
                getOrDefault(SettingKeys.ASSIGNMENT_AI_API_KEY, assignmentAiDraftProperties.getApiKey()),
                getOrDefault(SettingKeys.ASSIGNMENT_AI_MODEL, assignmentAiDraftProperties.getModel()),
                getOrDefault(SettingKeys.ASSIGNMENT_AI_FALLBACK_MODEL, assignmentAiDraftProperties.getFallbackModel()),
                getLongOrDefault(SettingKeys.ASSIGNMENT_AI_TIMEOUT_SECONDS, assignmentAiDraftProperties.getTimeout().toSeconds()));
    }

    public SePayBankDisplaySettings resolveSePayBankDisplaySettings() {
        return new SePayBankDisplaySettings(
                getOrDefault(SettingKeys.SEPAY_ACCOUNT_NUMBER, sePayProperties.getAccountNumber()),
                getOrDefault(SettingKeys.SEPAY_BANK_NAME, sePayProperties.getBankName()),
                getOrDefault(SettingKeys.SEPAY_ACCOUNT_NAME, sePayProperties.getAccountName()));
    }

    public SePayRuntimeSettings resolveSePayRuntimeSettings() {
        return new SePayRuntimeSettings(
                getOrDefault(SettingKeys.SEPAY_API_TOKEN, sePayProperties.getApiToken()),
                getOrDefault(SettingKeys.SEPAY_WEBHOOK_SECRET, sePayProperties.getWebhookSecret()));
    }

    private boolean getBooleanOrDefault(String key, boolean fallback) {
        String value = getRawValue(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String normalized = value.trim().toLowerCase();
        return switch (normalized) {
            case "true", "1", "yes", "on" -> true;
            case "false", "0", "no", "off" -> false;
            default -> fallback;
        };
    }

    private int getIntOrDefault(String key, int fallback) {
        String value = getRawValue(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private long getLongOrDefault(String key, long fallback) {
        String value = getRawValue(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
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

    public record GoogleMeetSettings(boolean enabled, String refreshToken) {
    }

    public record QuestionImageImportSettings(
            boolean enabled,
            String provider,
            String apiKey,
            String model,
            long timeoutSeconds,
            int maxFileSizeMb,
            int maxFiles) {
        public Duration timeout() {
            return Duration.ofSeconds(timeoutSeconds);
        }

        public boolean isConfigured() {
            return apiKey != null && !apiKey.isBlank();
        }
    }

    public record AssignmentAiSettings(
            boolean enabled,
            String provider,
            String apiKey,
            String model,
            String fallbackModel,
            long timeoutSeconds) {
        public Duration timeout() {
            return Duration.ofSeconds(timeoutSeconds);
        }

        public boolean isConfigured() {
            return apiKey != null && !apiKey.isBlank();
        }
    }

    public record SePayBankDisplaySettings(
            String accountNumber,
            String bankName,
            String accountName) {
        public boolean isConfigured() {
            return accountNumber != null && !accountNumber.isBlank()
                    && bankName != null && !bankName.isBlank()
                    && accountName != null && !accountName.isBlank();
        }
    }

    public record SePayRuntimeSettings(
            String apiToken,
            String webhookSecret) {
        public boolean hasApiToken() {
            return apiToken != null && !apiToken.isBlank();
        }

        public boolean hasWebhookSecret() {
            return webhookSecret != null && !webhookSecret.isBlank();
        }
    }
}
