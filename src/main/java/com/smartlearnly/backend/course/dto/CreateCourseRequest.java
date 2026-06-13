package com.smartlearnly.backend.course.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record CreateCourseRequest(
        @NotNull(message = "Category is required")
        UUID categoryId,

        @NotBlank(message = "Course title is required")
        @Size(max = 255, message = "Course title must not exceed 255 characters")
        String title,

        @Size(max = 280, message = "Course slug must not exceed 280 characters")
        @Pattern(
                regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$",
                message = "Course slug can contain lowercase letters, numbers, and hyphens only"
        )
        String slug,

        String shortDescription,

        String description,

        String outcomes,

        String requirements,

        @Size(max = 50, message = "Language must not exceed 50 characters")
        String language,

        @Size(max = 30, message = "Level must not exceed 30 characters")
        String level,

        @Size(max = 500, message = "Thumbnail URL must not exceed 500 characters")
        String thumbnailUrl,

        @DecimalMin(value = "0.00", message = "Course price must be greater than or equal to 0")
        BigDecimal price,

        @DecimalMin(value = "0.00", message = "Discounted price must be greater than or equal to 0")
        BigDecimal discountedPrice,

        Boolean isFree,

        @Pattern(regexp = "(?i)draft|published|inactive", message = "Course status must be draft, published, or inactive")
        String status
) {
}
