package com.smartlearnly.backend.admin.settings.dto;

/**
 * Google Meet settings exposed to the admin UI. The refresh token is never
 * returned; only {@code hasRefreshToken} signals whether one is configured.
 */
public record GoogleMeetSettingsResponse(
        boolean enabled,
        boolean hasRefreshToken
) {
}
