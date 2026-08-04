package com.smartlearnly.backend.auth.session.dto;

import com.smartlearnly.backend.auth.profile.dto.UserProfileResponse;

public record AuthSessionResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        UserProfileResponse user
) {
}
