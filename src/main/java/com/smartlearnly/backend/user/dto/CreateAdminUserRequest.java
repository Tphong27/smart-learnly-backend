package com.smartlearnly.backend.user.dto;

import com.smartlearnly.backend.common.validation.PhoneNumberRules;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateAdminUserRequest(
        @NotBlank(message = "Full name is required")
        @Size(max = 150, message = "Full name must not exceed 150 characters")
        String fullName,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid email address")
        @Size(max = 255, message = "Email must not exceed 255 characters")
        String email,

        @Size(max = 20, message = "Phone number must not exceed 20 characters")
        @Pattern(
                regexp = PhoneNumberRules.VIETNAMESE_MOBILE_PATTERN,
                message = PhoneNumberRules.VIETNAMESE_MOBILE_MESSAGE
        )
        String phoneNumber,

        @Pattern(
                regexp = "(?i)^(GUEST|TRAINEE|TRAINER|TMO|SME|ADMIN)$",
                message = "Role is invalid"
        )
        String role,

        @Pattern(
                regexp = "(?i)^(pending_verify|active|inactive|banned)$",
                message = "Status is invalid"
        )
        String status,

        Boolean emailVerified
) {
}
