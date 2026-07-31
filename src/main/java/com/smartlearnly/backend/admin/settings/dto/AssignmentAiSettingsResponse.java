package com.smartlearnly.backend.admin.settings.dto;

/**
 * Assignment AI settings exposed to the admin UI. The API key is never
 * returned; only {@code hasApiKey} signals whether one is configured.
 */
public record AssignmentAiSettingsResponse(
        boolean enabled,
        String provider,
        boolean hasApiKey,
        String model,
        String fallbackModel,
        long timeoutSeconds
) {
}
