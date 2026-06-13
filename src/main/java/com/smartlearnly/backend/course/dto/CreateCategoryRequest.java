package com.smartlearnly.backend.course.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateCategoryRequest(
        @NotBlank(message = "Category name is required")
        @Size(max = 150, message = "Category name must not exceed 150 characters")
        String name,

        @Size(max = 180, message = "Category slug must not exceed 180 characters")
        String slug,

        String description,

        UUID parentId,

        @JsonProperty("isActive")
        Boolean isActive,

        @PositiveOrZero(message = "Sort order must not be negative")
        Integer sortOrder
) {
}
