package com.smartlearnly.backend.course.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CourseCreateRequest(
        @NotNull(message = "Category is required")
        UUID categoryId,

        @NotBlank(message = "Course title is required")
        @Size(max = 200, message = "Course title must not exceed 200 characters")
        String title,

        @Size(max = 180, message = "Course slug must not exceed 180 characters")
        @Pattern(
                regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$",
                message = "Course slug can contain lowercase letters, numbers, and hyphens only"
        )
        String slug,

        @Size(max = 10000, message = "Course description must not exceed 10000 characters")
        String description,

        @DecimalMin(value = "0.00", message = "Course price must be greater than or equal to 0")
        BigDecimal price,

        @Pattern(
                regexp = "(?i)draft|published|archived",
                message = "Course status must be draft, published, or archived"
        )
        String status,

        @Size(max = 500, message = "Avatar URL must not exceed 500 characters")
        String avatarUrl,

        @Size(max = 20, message = "A course can have at most 20 tags")
        List<@NotBlank(message = "Tag must not be blank") @Size(max = 50, message = "Tag must not exceed 50 characters") String> tags,

        Boolean featured
) {
}
