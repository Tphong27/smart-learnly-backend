package com.smartlearnly.backend.admin.settings.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Google OAuth update payload. {@code clientSecret} is optional: when null,
 * blank, or the masked placeholder ("********"), the existing secret is kept.
 */
public record GoogleOAuthSettingsUpdateRequest(
        @NotBlank(message = "Client ID is required")
        @Size(max = 300, message = "Client ID must be at most 300 characters")
        String clientId,

        @Size(max = 300, message = "Client secret must be at most 300 characters")
        String clientSecret,

        @Size(max = 300, message = "Scope must be at most 300 characters")
        String scope
) {
}
