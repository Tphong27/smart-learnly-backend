package com.smartlearnly.backend.flashcard.staging.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class AdminFlashcardStagingDtos {
    private AdminFlashcardStagingDtos() {
    }

    public record ImportQuestionBankRequest(
            @NotEmpty(message = "At least one question id is required")
            @Size(max = 500, message = "Question import must not exceed 500 questions")
            List<@NotNull(message = "Question id must not be null") UUID> questionIds
    ) {
    }

    public record ApproveStagingCardsRequest(
            @NotEmpty(message = "At least one staging card id is required")
            @Size(max = 500, message = "Approval request must not exceed 500 staging cards")
            List<@NotNull(message = "Staging card id must not be null") UUID> stagingCardIds
    ) {
    }

    public record UpdateStagingCardRequest(
            String frontText,

            String backText,

            @Size(max = 500, message = "Front image URL must not exceed 500 characters")
            String frontImageUrl,

            @Size(max = 500, message = "Back image URL must not exceed 500 characters")
            String backImageUrl,

            String hint,

            String explanation,

            @PositiveOrZero(message = "Sort order must not be negative")
            Integer sortOrder
    ) {
    }

    public record SourceQuestionResponse(
            UUID questionId,
            UUID id,
            UUID questionBankId,
            String questionBankName,
            UUID courseId,
            UUID moduleId,
            String questionText,
            String questionType,
            Short difficulty,
            String status,
            String explanation,
            List<SourceQuestionAnswerResponse> answers,
            List<String> correctAnswers
    ) {
    }

    public record SourceQuestionAnswerResponse(
            UUID answerId,
            UUID id,
            String answerText,
            boolean correct,
            boolean isCorrect,
            int displayOrder,
            int orderIndex
    ) {
    }

    public record StagingBatchResponse(
            UUID id,
            UUID flashcardSetId,
            UUID lessonId,
            UUID courseId,
            String sourceType,
            String status,
            String sourceName,
            List<StagingCardResponse> cards,
            Instant createdAt,
            Instant updatedAt,
            Instant approvedAt,
            UUID approvedBy
    ) {
    }

    public record StagingCardResponse(
            UUID id,
            UUID batchId,
            UUID sourceQuestionId,
            String frontText,
            String backText,
            String frontImageUrl,
            String backImageUrl,
            String hint,
            String explanation,
            String sourceExcerpt,
            String status,
            Integer sortOrder,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record ApproveStagingCardsResponse(
            int approvedCount,
            List<UUID> flashcardIds
    ) {
    }
}
