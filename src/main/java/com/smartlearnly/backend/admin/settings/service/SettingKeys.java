package com.smartlearnly.backend.admin.settings.service;

/**
 * Canonical keys for entries stored in the {@code system_settings} table.
 */
public final class SettingKeys {
    private SettingKeys() {
    }

    // Email (Resend) transport + sender identity.
    public static final String EMAIL_API_KEY = "email.api_key";
    public static final String EMAIL_API_URL = "email.api_url";
    public static final String EMAIL_FROM_NAME = "email.from_name";
    public static final String EMAIL_FROM_EMAIL = "email.from_email";
    public static final String EMAIL_REPLY_TO = "email.reply_to";

    // Google OAuth.
    public static final String GOOGLE_CLIENT_ID = "oauth.google.client_id";
    public static final String GOOGLE_CLIENT_SECRET = "oauth.google.client_secret";
    public static final String GOOGLE_SCOPE = "oauth.google.scope";

    // Google Meet integration.
    public static final String GOOGLE_MEET_ENABLED = "google_meet.enabled";
    public static final String GOOGLE_MEET_REFRESH_TOKEN = "google_meet.refresh_token";

    // Question image import.
    public static final String QUESTION_IMAGE_IMPORT_ENABLED = "question_image_import.enabled";
    public static final String QUESTION_IMAGE_IMPORT_PROVIDER = "question_image_import.provider";
    public static final String QUESTION_IMAGE_IMPORT_API_KEY = "question_image_import.api_key";
    public static final String QUESTION_IMAGE_IMPORT_MODEL = "question_image_import.model";
    public static final String QUESTION_IMAGE_IMPORT_TIMEOUT_SECONDS = "question_image_import.timeout_seconds";
    public static final String QUESTION_IMAGE_IMPORT_MAX_FILE_SIZE_MB = "question_image_import.max_file_size_mb";
    public static final String QUESTION_IMAGE_IMPORT_MAX_FILES = "question_image_import.max_files";

    // SePay checkout bank display settings.
    public static final String SEPAY_ACCOUNT_NUMBER = "payment.sepay.account_number";
    public static final String SEPAY_BANK_NAME = "payment.sepay.bank_name";
    public static final String SEPAY_ACCOUNT_NAME = "payment.sepay.account_name";
    public static final String SEPAY_API_TOKEN = "payment.sepay.api_token";
    public static final String SEPAY_WEBHOOK_SECRET = "payment.sepay.webhook_secret";

    // Assignment AI draft.
    public static final String ASSIGNMENT_AI_ENABLED = "assignment_ai.enabled";
    public static final String ASSIGNMENT_AI_PROVIDER = "assignment_ai.provider";
    public static final String ASSIGNMENT_AI_API_KEY = "assignment_ai.api_key";
    public static final String ASSIGNMENT_AI_MODEL = "assignment_ai.model";
    public static final String ASSIGNMENT_AI_FALLBACK_MODEL = "assignment_ai.fallback_model";
    public static final String ASSIGNMENT_AI_TIMEOUT_SECONDS = "assignment_ai.timeout_seconds";
}
