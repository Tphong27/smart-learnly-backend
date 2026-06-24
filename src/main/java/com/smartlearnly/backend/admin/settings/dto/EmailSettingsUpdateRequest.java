package com.smartlearnly.backend.admin.settings.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Email settings update payload. {@code apiKey} is optional: when null, blank,
 * or the masked placeholder ("********"), the existing key is kept untouched.
 */
public record EmailSettingsUpdateRequest(
        @Size(max = 500, message = "API key must be at most 500 characters")
        String apiKey,

        @NotBlank(message = "From name is required")
        @Size(max = 150, message = "From name must be at most 150 characters")
        String fromName,

        @NotBlank(message = "From email is required")
        @Email(message = "From email must be a valid email address")
        String fromEmail,

        @Email(message = "Reply-to must be a valid email address")
        String replyTo
) {
}
