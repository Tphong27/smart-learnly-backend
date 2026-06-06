package com.smartlearnly.backend.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VerifyEmailRequest(
        @Schema(example = "paste-token-from-server-log")
        @NotBlank(message = "Verification token is required")
        @Size(max = 512, message = "Verification token is too long")
        String token
) {
}
