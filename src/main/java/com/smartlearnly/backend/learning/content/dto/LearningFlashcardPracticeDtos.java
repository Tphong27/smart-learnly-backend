package com.smartlearnly.backend.learning.content.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class LearningFlashcardPracticeDtos {
    private LearningFlashcardPracticeDtos() {
    }

    public record FlashcardPracticeSetResponse(
            UUID id,
            UUID lessonId,
            UUID courseId,
            UUID sectionId,
            String title,
            String description,
            List<FlashcardPracticeCardResponse> cards
    ) {
    }

    public record FlashcardPracticeCardResponse(
            UUID id,
            UUID setId,
            String frontText,
            String frontImageUrl,
            String backText,
            String backImageUrl,
            String hint,
            String explanation,
            Integer orderIndex,
            FlashcardProgressSummary progress
    ) {
    }

    public record FlashcardProgressSummary(
            String learningStatus,
            String lastReviewResult,
            Integer repetitions,
            Integer intervalDays,
            Instant lastReviewedAt,
            Instant nextReviewAt
    ) {
    }

    public record FlashcardProgressRequest(
            @NotBlank(message = "Review result is required")
            String result
    ) {
    }

    public record FlashcardProgressResponse(
            UUID cardId,
            String learningStatus,
            String lastReviewResult,
            Integer repetitions,
            Integer intervalDays,
            Instant lastReviewedAt,
            Instant nextReviewAt
    ) {
    }
}
