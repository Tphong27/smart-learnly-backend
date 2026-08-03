package com.smartlearnly.backend.admin.settings.dto;

import jakarta.validation.constraints.Size;

/**
 * Update payload for SePay runtime secrets. Null or ******** keeps the current value.
 * Blank clears the stored override and falls back to env/application config.
 */
public record SePayRuntimeSettingsUpdateRequest(
        @Size(max = 1000, message = "SePay API token must be at most 1000 characters")
        String apiToken,

        @Size(max = 1000, message = "SePay webhook secret must be at most 1000 characters")
        String webhookSecret
) {
}
