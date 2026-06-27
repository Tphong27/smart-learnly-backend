package com.smartlearnly.backend.lessonprogress.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateLessonProgressRequest(
        @NotNull Boolean completed
) {
}