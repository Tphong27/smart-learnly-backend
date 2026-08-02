package com.smartlearnly.backend.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record UpdateAdminUserRequest(
        @Size(max = 150, message = "Full name must not exceed 150 characters")
        String fullName,

        @Email(message = "Email must be a valid email address")
        @Size(max = 255, message = "Email must not exceed 255 characters")
        String email,

        @Size(max = 20, message = "Phone number must not exceed 20 characters")
        String phoneNumber,

        String role,

        String status,

        Boolean emailVerified
) {
}
