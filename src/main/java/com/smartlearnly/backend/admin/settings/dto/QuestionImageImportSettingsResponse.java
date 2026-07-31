package com.smartlearnly.backend.admin.settings.dto;

/**
 * Question image import settings exposed to the admin UI. The API key is never
 * returned; only {@code hasApiKey} signals whether one is configured.
 */
public record QuestionImageImportSettingsResponse(
        boolean enabled,
        String provider,
        boolean hasApiKey,
        String model,
        long timeoutSeconds,
        int maxFileSizeMb,
        int maxFiles
) {
}
