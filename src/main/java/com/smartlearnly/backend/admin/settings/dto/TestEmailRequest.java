package com.smartlearnly.backend.admin.settings.dto;

import jakarta.validation.constraints.Email;

/** Optional recipient for the SMTP/email connectivity test. */
public record TestEmailRequest(
        @Email(message = "Recipient must be a valid email address")
        String to
) {
}
