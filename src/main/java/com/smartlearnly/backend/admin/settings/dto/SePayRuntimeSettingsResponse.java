package com.smartlearnly.backend.admin.settings.dto;

/**
 * SePay runtime secret configuration exposed to the admin UI without returning raw secrets.
 */
public record SePayRuntimeSettingsResponse(
        boolean hasApiToken,
        boolean hasWebhookSecret,
        boolean hasApiTokenOverride,
        boolean hasWebhookSecretOverride
) {
}
