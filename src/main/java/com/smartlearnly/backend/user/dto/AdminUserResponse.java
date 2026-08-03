package com.smartlearnly.backend.user.dto;

import java.time.Instant;
import java.util.UUID;

public record AdminUserResponse(
        UUID id,
        String email,
        String fullName,
        String avatarUrl,
        String role,
        String status,
        String phoneNumber,
        boolean emailVerified,
        Instant lastLoginAt,
        Instant createdAt
) {
}
