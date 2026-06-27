package com.smartlearnly.backend.flashcard.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class AdminFlashcardDtos {
    private AdminFlashcardDtos() {
    }

    public record CreateFlashcardLessonRequest(
            @NotBlank(message = "Flashcard lesson title is required")
            @Size(max = 255, message = "Flashcard lesson title must not exceed 255 characters")
            String title,

            String description,

            @PositiveOrZero(message = "Sort order must not be negative")
            Integer sortOrder,

            Boolean isPreview,

            @Pattern(regexp = "(?i)draft|published|inactive", message = "Lesson status must be draft, published, or inactive")
            String status
    ) {
    }

    public record FlashcardLessonCreatedResponse(
            UUID lessonId,
            UUID setId
    ) {
    }

    public record UpdateFlashcardSetRequest(
            @Size(max = 255, message = "Flashcard set title must not exceed 255 characters")
            String title,
            String description
    ) {
    }

    public record CreateFlashcardCardRequest(
            String frontText,
            @Size(max = 500, message = "Front image URL must not exceed 500 characters")
            String frontImageUrl,
            String backText,
            @Size(max = 500, message = "Back image URL must not exceed 500 characters")
            String backImageUrl,
            String hint,
            String explanation,
            @PositiveOrZero(message = "Order index must not be negative")
            Integer orderIndex
    ) {
    }

    public record UpdateFlashcardCardRequest(
            String frontText,
            @Size(max = 500, message = "Front image URL must not exceed 500 characters")
            String frontImageUrl,
            String backText,
            @Size(max = 500, message = "Back image URL must not exceed 500 characters")
            String backImageUrl,
            String hint,
            String explanation,
            @PositiveOrZero(message = "Order index must not be negative")
            Integer orderIndex
    ) {
    }

    public record ReorderFlashcardCardsRequest(
            @JsonAlias("orderedIds")
            @NotEmpty(message = "Reorder list must not be empty")
            @Size(max = 500, message = "Reorder list must not exceed 500 cards")
            List<@NotNull(message = "Card id must not be null") UUID> ids
    ) {
    }

    public record FlashcardSetResponse(
            UUID id,
            UUID lessonId,
            UUID courseId,
            UUID sectionId,
            String title,
            String description,
            List<FlashcardCardResponse> cards,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record FlashcardCardResponse(
            UUID id,
            UUID setId,
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
}
