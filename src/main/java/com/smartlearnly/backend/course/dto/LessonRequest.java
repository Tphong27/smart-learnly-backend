package com.smartlearnly.backend.course.dto;

import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import java.util.List;

public record LessonRequest(
        @NotBlank(message = "Lesson title is required")
        @Size(max = 255, message = "Lesson title must not exceed 255 characters")
        String title,

        @Pattern(
                regexp = "(?i)video|pdf|document|rich_text|quiz",
                message = "Lesson type must be video, pdf, document, rich_text, or quiz"
        )
        String lessonType,

        @Pattern(
                regexp = "(?i)video|pdf|document|rich_text|quiz",
                message = "Lesson type must be video, pdf, document, rich_text, or quiz"
        )
        String type,

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

        @Size(max = 10, message = "Lesson resources must not exceed 10 files")
        List<@NotNull(message = "Lesson resource must not be null") @Valid LessonResourceRequest> resources,

        @PositiveOrZero(message = "Sort order must not be negative")
        Integer sortOrder
) {
}
