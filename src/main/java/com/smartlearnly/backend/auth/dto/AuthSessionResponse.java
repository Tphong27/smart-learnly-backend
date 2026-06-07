package com.smartlearnly.backend.auth.dto;

public record AuthSessionResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        UserProfileResponse user
) {
}
