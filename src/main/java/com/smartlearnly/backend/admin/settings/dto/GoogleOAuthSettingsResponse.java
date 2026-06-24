package com.smartlearnly.backend.admin.settings.dto;

/**
 * Google OAuth settings exposed to the admin UI. The client secret is never
 * returned; only {@code hasClientSecret} signals whether one is configured.
 */
public record GoogleOAuthSettingsResponse(
        String clientId,
        boolean hasClientSecret,
        String scope,
        String redirectUriHint
) {
}
