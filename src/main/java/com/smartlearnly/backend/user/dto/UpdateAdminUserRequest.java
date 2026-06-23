package com.smartlearnly.backend.user.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateAdminUserRequest(
        @Size(max = 150, message = "Full name must not exceed 150 characters")
        String fullName,

        @Email(message = "Email must be a valid email address")
        @Size(max = 255, message = "Email must not exceed 255 characters")
        String email,

        @Size(max = 500, message = "Avatar URL must not exceed 500 characters")
        String avatarUrl,

        @Size(max = 20, message = "Phone number must not exceed 20 characters")
        String phoneNumber,

        String bio,

        String role,

        String status,

        Boolean emailVerified,

        @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z\\d]).{8,100}$",
                message = "Password must contain uppercase, lowercase, number, and special character"
        )
        String password
) {
    @JsonIgnore
    public boolean hasAnyField() {
        return fullName != null
                || email != null
                || avatarUrl != null
                || phoneNumber != null
                || bio != null
                || role != null
                || status != null
                || emailVerified != null
                || password != null;
    }
}
