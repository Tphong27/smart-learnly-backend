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

public final class QuestionImportDtos {

    private QuestionImportDtos() {
    }

    public record ImportRow(
            @NotNull(message = "Row number is required")
            @Min(value = 1, message = "Row number must be 1 or greater")
            Integer rowNumber,

            @NotBlank(message = "Question text is required")
            @Size(max = 10000, message = "Question text must not exceed 10000 characters")
            String questionText,

            @NotBlank(message = "Question type is required")
            String questionType,

            @NotEmpty(message = "At least two answers are required")
            @Size(max = 6, message = "Multiple choice questions support 2 to 6 answers")
            List<@NotBlank(message = "Answer text is required") @Size(max = 4000, message = "Answer text must not exceed 4000 characters") String> options,

            @NotBlank(message = "Correct answer is required")
            String correctAnswer,

            @Size(max = 10000, message = "Explanation must not exceed 10000 characters")
            String explanation,

            @Min(value = 1, message = "Difficulty must be between 1 and 5")
            @Max(value = 5, message = "Difficulty must be between 1 and 5")
            Short difficulty,

            String bloomLevel,

            UUID moduleId
    ) {
    }

    public record ImportBatchRequest(
            @NotNull(message = "Bank ID is required")
            UUID bankId,

            @NotNull(message = "Rows are required")
            @Size(min = 1, max = 1000, message = "Batch size must be between 1 and 1000 rows")
            @Valid
            List<ImportRow> rows
    ) {
    }

    public record ImportRowError(
            int rowNumber,
            List<String> errors
    ) {
    }

    public record ImportBatchResponse(
            int requested,
            int created,
            List<UUID> createdQuestionIds,
            List<ImportRowError> errors
    ) {
    }
}