package com.smartlearnly.backend.question.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class QuestionBankDto {

    private QuestionBankDto() {
    }

    public record CreateRequest(
            @NotNull(message = "Course ID is required")
            UUID courseId,

            @NotBlank(message = "Bank name is required")
            @Size(max = 255, message = "Bank name must not exceed 255 characters")
            String name,

            @Size(max = 2000, message = "Description must not exceed 2000 characters")
            String description,

            String status
    ) {
    }

    public record UpdateRequest(
            @NotBlank(message = "Bank name is required")
            @Size(max = 255, message = "Bank name must not exceed 255 characters")
            String name,

            @Size(max = 2000, message = "Description must not exceed 2000 characters")
            String description,

            String status
    ) {
    }

    public record RestoreRequest(
            @NotBlank(message = "Target status is required")
            String status
    ) {
    }

    public record Response(
            UUID bankId,
            UUID id,
            UUID courseId,
            String name,
            String description,
            String status,
            long questionCount,
            UUID createdBy,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}
