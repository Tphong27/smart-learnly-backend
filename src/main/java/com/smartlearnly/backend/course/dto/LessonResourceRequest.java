package com.smartlearnly.backend.course.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record LessonResourceRequest(
        @NotBlank(message = "Resource URL is required")
        @Size(max = 1000, message = "Resource URL must not exceed 1000 characters")
        String url,

        @Size(max = 1000, message = "Resource object path must not exceed 1000 characters")
        String objectPath,

        @Size(max = 255, message = "Resource name must not exceed 255 characters")
        String name,

        @Size(max = 255, message = "Resource file name must not exceed 255 characters")
        String fileName,

        @PositiveOrZero(message = "Resource file size must not be negative")
        Long fileSize,

        @Size(max = 255, message = "Resource content type must not exceed 255 characters")
        String contentType,

        @PositiveOrZero(message = "Resource sort order must not be negative")
        Integer sortOrder
) {
}
