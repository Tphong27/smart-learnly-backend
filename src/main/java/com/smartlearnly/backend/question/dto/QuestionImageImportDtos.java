package com.smartlearnly.backend.question.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public final class QuestionImageImportDtos {

    private QuestionImageImportDtos() {
    }

    public record Answer(
            @NotBlank(message = "Answer text is required")
            @Size(max = 4000, message = "Answer text must not exceed 4000 characters")
            String answerText,

            Boolean correct,
            Boolean isCorrect
    ) {
        public boolean correctValue() {
            return Boolean.TRUE.equals(correct) || Boolean.TRUE.equals(isCorrect);
        }
    }

    public record PreviewQuestion(
            String clientImportId,
            int questionNumber,
            String status,
            String questionText,
            String questionType,
            List<Answer> answers,
            Short difficulty,
            String explanation,
            List<String> warnings,
            List<String> errors
    ) {
    }

    public record PreviewResponse(
            String ocrText,
            List<PreviewQuestion> questions,
            List<String> warnings
    ) {
    }

    public record ConfirmQuestion(
            @NotBlank(message = "Question text is required")
            @Size(max = 10000, message = "Question text must not exceed 10000 characters")
            String questionText,

            @NotBlank(message = "Question type is required")
            String questionType,

            @Valid
            @NotEmpty(message = "At least two answers are required")
            @Size(max = 6, message = "Multiple choice questions support 2 to 6 answers")
            List<Answer> answers,

            @Min(value = 1, message = "Difficulty must be between 1 and 5")
            @Max(value = 5, message = "Difficulty must be between 1 and 5")
            Short difficulty,

            @Size(max = 10000, message = "Explanation must not exceed 10000 characters")
            String explanation,

            String bloomLevel,
            UUID moduleId
    ) {
    }

    public record ConfirmRequest(
            @NotNull(message = "Bank ID is required")
            UUID bankId,

            @NotNull(message = "Questions are required")
            @Size(min = 1, max = 1000, message = "Import size must be between 1 and 1000 questions")
            @Valid
            List<ConfirmQuestion> questions
    ) {
    }

    public record ConfirmItem(
            UUID questionId,
            String questionText,
            String status
    ) {
    }

    public record ConfirmResponse(
            int createdCount,
            int failedCount,
            List<ConfirmItem> items,
            List<QuestionImportDtos.ImportRowError> errors
    ) {
    }
}
