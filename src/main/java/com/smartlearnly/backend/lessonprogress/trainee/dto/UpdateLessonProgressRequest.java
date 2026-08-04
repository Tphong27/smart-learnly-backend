package com.smartlearnly.backend.lessonprogress.trainee.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateLessonProgressRequest(
        @NotNull Boolean completed
) {
}
