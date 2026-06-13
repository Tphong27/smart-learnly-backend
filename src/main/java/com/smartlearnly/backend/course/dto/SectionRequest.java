package com.smartlearnly.backend.course.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SectionRequest(
        @NotBlank(message = "Section title is required")
        @Size(max = 200, message = "Section title must not exceed 200 characters")
        String title,

        @Min(value = 0, message = "Section order index must be greater than or equal to 0")
        Integer orderIndex,

        @Pattern(regexp = "(?i)active|inactive", message = "Section status must be active or inactive")
        String status
) {
}
