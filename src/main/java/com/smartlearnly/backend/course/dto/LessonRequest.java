package com.smartlearnly.backend.course.dto;

import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record LessonRequest(
        @NotBlank(message = "Lesson title is required")
        @Size(max = 255, message = "Lesson title must not exceed 255 characters")
        String title,

        @Pattern(regexp = "(?i)video|pdf|rich_text", message = "Lesson type must be video, pdf, or rich_text")
        String lessonType,

        @Size(max = 500, message = "Video URL must not exceed 500 characters")
        String videoUrl,

        String content,

        @Size(max = 500, message = "Attachment URL must not exceed 500 characters")
        String attachmentUrl,

        @PositiveOrZero(message = "Duration must not be negative")
        Integer durationSeconds,

        Boolean isPreview,

        @Pattern(regexp = "(?i)draft|published|inactive", message = "Lesson status must be draft, published, or inactive")
        String status,

        @PositiveOrZero(message = "Sort order must not be negative")
        Integer sortOrder
) {
}
