package com.smartlearnly.backend.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ForgotPasswordRequest(
        @Schema(example = "active.trainee@smartlearnly.dev")
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid email address")
        String email
) {
}
