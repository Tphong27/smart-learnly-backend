package com.smartlearnly.backend.user.dto;

import java.util.UUID;

public record PublicTrainerProfileResponse(
        UUID id,
        String fullName,
        String email,
        String avatarUrl,
        String bio,
        String role,
        String status
) {
}