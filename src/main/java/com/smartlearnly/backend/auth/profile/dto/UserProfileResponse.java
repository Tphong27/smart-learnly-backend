package com.smartlearnly.backend.auth.profile.dto;

import java.time.Instant;
import java.util.UUID;

public record UserProfileResponse(
        UUID id,
        String email,
        String fullName,
        String avatarUrl,
        String phoneNumber,
        String bio,
        String role,
        String status,
        boolean emailVerified,
        Instant emailVerifiedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
