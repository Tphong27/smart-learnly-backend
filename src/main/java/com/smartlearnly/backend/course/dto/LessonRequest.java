package com.smartlearnly.backend.course.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record LessonRequest(
        @NotBlank(message = "Lesson title is required")
        @Size(max = 200, message = "Lesson title must not exceed 200 characters")
        String title,

        @Size(max = 100000, message = "Lesson content must not exceed 100000 characters")
        String content,

        @Pattern(
                regexp = "(?i)rich_text|video|pdf|quiz|assignment",
                message = "Lesson type must be rich_text, video, pdf, quiz, or assignment"
        )
        String lessonType,

        @Min(value = 0, message = "Lesson order index must be greater than or equal to 0")
        Integer orderIndex,

        Boolean preview,

        @Pattern(regexp = "(?i)active|inactive", message = "Lesson status must be active or inactive")
        String status
) {
}
