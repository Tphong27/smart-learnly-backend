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
}
