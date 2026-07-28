package com.smartlearnly.backend.admin.settings.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Question image import update payload. {@code apiKey} is optional: when null
 * or the masked placeholder ("********"), the existing key is kept; blank clears
 * the stored override.
 */
public record QuestionImageImportSettingsUpdateRequest(
        @NotNull(message = "Enabled flag is required")
        Boolean enabled,

        @NotBlank(message = "Provider is required")
        @Size(max = 100, message = "Provider must be at most 100 characters")
        String provider,

        @Size(max = 500, message = "API key must be at most 500 characters")
        String apiKey,

        @NotBlank(message = "Model is required")
        @Size(max = 200, message = "Model must be at most 200 characters")
        String model,

        @NotNull(message = "Timeout is required")
        @Min(value = 5, message = "Timeout must be at least 5 seconds")
        @Max(value = 300, message = "Timeout must be at most 300 seconds")
        Long timeoutSeconds,

        @NotNull(message = "Max file size is required")
        @Min(value = 1, message = "Max file size must be at least 1 MB")
        @Max(value = 50, message = "Max file size must be at most 50 MB")
        Integer maxFileSizeMb,

        @NotNull(message = "Max files is required")
        @Min(value = 1, message = "Max files must be at least 1")
        @Max(value = 20, message = "Max files must be at most 20")
        Integer maxFiles
) {
}
