package com.smartlearnly.backend.admin.settings.dto;

/**
 * Email settings exposed to the admin UI. The API key is never returned;
 * only {@code hasApiKey} signals whether one is configured.
 */
public record EmailSettingsResponse(
        boolean hasApiKey,
        String fromName,
        String fromEmail,
        String replyTo
) {
}
