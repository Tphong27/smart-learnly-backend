package com.smartlearnly.backend.admin.settings.dto;

/**
 * Google OAuth settings exposed to the admin UI. The client ID and secret are
 * never returned; only {@code hasClientId} / {@code hasClientSecret} signal
 * whether each is configured.
 */
public record GoogleOAuthSettingsResponse(
        boolean hasClientId,
        boolean hasClientSecret,
        String scope,
        String redirectUriHint
) {
}
