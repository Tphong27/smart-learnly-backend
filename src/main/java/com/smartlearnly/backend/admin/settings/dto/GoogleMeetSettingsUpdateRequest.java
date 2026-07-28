package com.smartlearnly.backend.admin.settings.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Google Meet update payload. {@code refreshToken} is optional: when null or
 * the masked placeholder ("********"), the existing token is kept; blank clears
 * the stored override.
 */
public record GoogleMeetSettingsUpdateRequest(
        @NotNull(message = "Enabled flag is required")
        Boolean enabled,

        @Size(max = 1000, message = "Refresh token must be at most 1000 characters")
        String refreshToken
) {
}
