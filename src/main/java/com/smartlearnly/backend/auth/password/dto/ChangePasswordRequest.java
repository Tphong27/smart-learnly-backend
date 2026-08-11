package com.smartlearnly.backend.auth.password.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "Current password is required")
        @Size(max = 100, message = "Current password must not exceed 100 characters")
        String currentPassword,

        @NotBlank(message = "New password is required")
        @Size(min = 8, max = 100, message = "New password must be between 8 and 100 characters")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z\\d]).{8,100}$",
                message = "New password must contain uppercase, lowercase, number, and special character"
        )
        String newPassword,

        @NotBlank(message = "Password confirmation is required")
        @Size(min = 8, max = 100, message = "Password confirmation must be between 8 and 100 characters")
        String confirmPassword
) {
}
