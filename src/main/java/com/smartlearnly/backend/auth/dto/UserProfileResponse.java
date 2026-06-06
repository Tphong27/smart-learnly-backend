package com.smartlearnly.backend.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

public record UserProfileResponse(
        @Schema(example = "5fa6a88d-a0e7-4f20-9c8c-c1fd745b74f0")
        UUID id,
        @Schema(example = "active.trainee@smartlearnly.dev")
        String email,
        @Schema(example = "Active Trainee")
        String fullName,
        @Schema(example = "https://api.dicebear.com/9.x/initials/svg?seed=Active%20Trainee")
        String avatarUrl,
        @Schema(example = "+84901234567")
        String phoneNumber,
        @Schema(example = "Seeded active trainee account for local authentication testing.")
        String bio,
        @Schema(example = "TRAINEE")
        String role,
        @Schema(example = "active")
        String status,
        boolean emailVerified,
        Instant emailVerifiedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
