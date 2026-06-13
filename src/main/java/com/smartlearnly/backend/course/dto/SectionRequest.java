package com.smartlearnly.backend.course.dto;

import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SectionRequest(
        @NotBlank(message = "Section title is required")
        @Size(max = 255, message = "Section title must not exceed 255 characters")
        String title,

        @PositiveOrZero(message = "Sort order must not be negative")
        Integer sortOrder
) {
}
