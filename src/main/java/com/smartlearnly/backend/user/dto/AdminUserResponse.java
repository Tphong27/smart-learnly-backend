package com.smartlearnly.backend.user.dto;

import com.smartlearnly.backend.user.entity.UserAccount;
import java.time.Instant;
import java.util.UUID;

public record AdminUserResponse(
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
        Instant lastLoginAt,
        Instant lockedUntil,
        Integer failedLoginAttempts,
        Instant createdAt,
        Instant updatedAt
) {
    public static AdminUserResponse from(UserAccount user) {
        return new AdminUserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getAvatarUrl(),
                user.getPhoneNumber(),
                user.getBio(),
                user.getRole(),
                user.getStatus(),
                user.isEmailVerified(),
                user.getEmailVerifiedAt(),
                user.getLastLoginAt(),
                user.getLockedUntil(),
                user.getFailedLoginAttempts(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
