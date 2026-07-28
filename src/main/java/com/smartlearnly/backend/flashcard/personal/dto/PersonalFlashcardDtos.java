package com.smartlearnly.backend.flashcard.personal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class PersonalFlashcardDtos {
    public static final int MAX_PERSONAL_CARD_REQUEST_SIZE = 500;

    private PersonalFlashcardDtos() {
    }

    public record CreatePersonalFlashcardSetRequest(
            @NotBlank(message = "Flashcard set title is required")
            @Size(max = 255, message = "Flashcard set title must not exceed 255 characters")
            String title,
            String description
    ) {
    }

    public record ReplacePersonalFlashcardSetRequest(
            @NotBlank(message = "Flashcard set title is required")
            @Size(max = 255, message = "Flashcard set title must not exceed 255 characters")
            String title,
            String description
    ) {
    }

    public record CreatePersonalFlashcardCardRequest(
            String frontText,
            @Size(max = 500, message = "Front image URL must not exceed 500 characters")
            String frontImageUrl,
            String backText,
            @Size(max = 500, message = "Back image URL must not exceed 500 characters")
            String backImageUrl,
            String hint,
            String explanation
    ) {
    }

    public record ReplacePersonalFlashcardCardRequest(
            String frontText,
            @Size(max = 500, message = "Front image URL must not exceed 500 characters")
            String frontImageUrl,
            String backText,
            @Size(max = 500, message = "Back image URL must not exceed 500 characters")
            String backImageUrl,
            String hint,
            String explanation
    ) {
    }

    public record BulkDeletePersonalFlashcardCardsRequest(
            @NotEmpty(message = "At least one flashcard id is required")
            @Size(max = MAX_PERSONAL_CARD_REQUEST_SIZE, message = "Bulk delete must not exceed 500 flashcards")
            List<@NotNull(message = "Flashcard id must not be null") UUID> ids
    ) {
    }

    public record ReorderPersonalFlashcardCardsRequest(
            @NotEmpty(message = "Reorder list must not be empty")
            @Size(max = MAX_PERSONAL_CARD_REQUEST_SIZE, message = "Reorder list must not exceed 500 flashcards")
            List<@NotNull(message = "Flashcard id must not be null") UUID> ids
    ) {
    }

    public record BulkCreatePersonalFlashcardCardsRequest(
            @NotEmpty(message = "At least one flashcard is required")
            @Size(max = MAX_PERSONAL_CARD_REQUEST_SIZE, message = "Bulk create must not exceed 500 flashcards")
            List<@NotNull(message = "Flashcard must not be null") CreatePersonalFlashcardCardRequest> cards
    ) {
    }

    public record GeneratePersonalFlashcardsFromTextRequest(
            @NotBlank(message = "Source text is required")
            @Size(max = 20_000, message = "Source text must not exceed 20000 characters")
            String sourceText,
            @NotNull(message = "Desired count is required")
            @Min(value = 1, message = "Desired count must be at least 1")
            @Max(value = 30, message = "Desired count must not exceed 30")
            Integer desiredCount,
            String language,
            String difficulty
    ) {
    }

    public record PersonalFlashcardSetSummaryResponse(
            UUID id,
            String title,
            String description,
            long activeCardCount,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record PersonalFlashcardSetDetailResponse(
            UUID id,
            String title,
            String description,
            List<PersonalFlashcardCardResponse> cards,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record PersonalFlashcardCardResponse(
            UUID id,
            String frontText,
            String frontImageUrl,
            String backText,
            String backImageUrl,
            String hint,
            String explanation,
            Integer orderIndex,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record PersonalFlashcardStudyResponse(
            UUID setId,
            String title,
            List<PersonalFlashcardCardResponse> cards
    ) {
    }

    public record PersonalBulkDeleteResponse(
            int deletedCount,
            Instant updatedAt
    ) {
    }

    public record PersonalGeneratedFlashcardsResponse(
            String sourceType,
            String sourceName,
            int requestedCount,
            List<PersonalGeneratedFlashcardCandidateResponse> cards
    ) {
    }

    public record PersonalGeneratedFlashcardCandidateResponse(
            String frontText,
            String backText,
            String hint,
            String explanation,
            String sourceExcerpt
    ) {
    }
}
