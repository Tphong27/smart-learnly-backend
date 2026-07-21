package com.smartlearnly.backend.videoai.dto;

import com.smartlearnly.backend.videoai.dto.VideoAiDtos.ContentResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.util.List;

public final class LearnerVideoAiToolsDtos {
    private LearnerVideoAiToolsDtos() {
    }

    public record GenerateToolRequest(
            @Min(1) @Max(20) Integer desiredCount,
            @Pattern(regexp = "easy|medium|hard", message = "difficulty must be easy, medium, or hard")
            String difficulty,
            Boolean regenerate
    ) {
    }

    public record FlashcardItem(String front, String back) {
    }

    public record FlashcardDeckResponse(
            List<FlashcardItem> cards,
            String difficulty,
            Instant generatedAt
    ) {
    }

    public record QuizQuestion(
            String prompt,
            List<String> options,
            int correctIndex,
            String explanation
    ) {
    }

    public record QuizResponse(
            List<QuizQuestion> questions,
            String difficulty,
            Instant generatedAt
    ) {
    }

    public record ToolsResponse(
            boolean available,
            String preparationStatus,
            ContentResponse content,
            FlashcardDeckResponse flashcards,
            QuizResponse quiz
    ) {
    }
}
