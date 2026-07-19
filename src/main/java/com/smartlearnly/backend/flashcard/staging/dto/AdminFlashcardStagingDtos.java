package com.smartlearnly.backend.flashcard.staging.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
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

    public record GenerateFromTextRequest(
            @NotBlank(message = "sourceText is required")
            @Size(min = 100, max = 20000, message = "sourceText must be between 100 and 20000 characters")
            String sourceText,

            @Min(value = 1, message = "desiredCount must be at least 1")
            @Max(value = 30, message = "desiredCount must not exceed 30")
            Integer desiredCount,

            String language,

            @Pattern(regexp = "(?i)^(easy|medium|hard)$", message = "difficulty must be easy, medium, or hard")
            String difficulty,

            @Pattern(regexp = "(?i)^(AI|RULE_BASED)$", message = "generationMode must be AI or RULE_BASED")
            String generationMode
    ) {
        public GenerateFromTextRequest {
            sourceText = normalizeNullable(sourceText);
            desiredCount = desiredCount == null ? 10 : desiredCount;
            language = normalizeDefault(language, "en");
            difficulty = normalizeNullable(difficulty);
            difficulty = difficulty == null ? null : difficulty.toLowerCase(Locale.ROOT);
            generationMode = normalizeDefault(generationMode, "AI")
                    .replace('-', '_')
                    .toUpperCase(Locale.ROOT);
        }

        private static String normalizeDefault(String value, String defaultValue) {
            String normalized = normalizeNullable(value);
            return normalized == null ? defaultValue : normalized;
        }

        private static String normalizeNullable(String value) {
            if (value == null) {
                return null;
            }
            String normalized = value.trim();
            return normalized.isEmpty() ? null : normalized;
        }
    }

    public record GenerateFromTranscriptRequest(
            @NotBlank(message = "transcriptText is required")
            String transcriptText,

            String sourceName,

            @Min(value = 1, message = "desiredCount must be at least 1")
            @Max(value = 30, message = "desiredCount must not exceed 30")
            Integer desiredCount,

            String language,

            @Pattern(regexp = "(?i)^(easy|medium|hard)$", message = "difficulty must be easy, medium, or hard")
            String difficulty,

            @Pattern(regexp = "(?i)^(AI|RULE_BASED)$", message = "generationMode must be AI or RULE_BASED")
            String generationMode
    ) {
        public GenerateFromTranscriptRequest {
            transcriptText = normalizeNullable(transcriptText);
            sourceName = normalizeNullable(sourceName);
            desiredCount = desiredCount == null ? 10 : desiredCount;
            language = normalizeDefault(language, "en");
            difficulty = normalizeNullable(difficulty);
            difficulty = difficulty == null ? null : difficulty.toLowerCase(Locale.ROOT);
            generationMode = normalizeDefault(generationMode, "AI")
                    .replace('-', '_')
                    .toUpperCase(Locale.ROOT);
        }

        private static String normalizeDefault(String value, String defaultValue) {
            String normalized = normalizeNullable(value);
            return normalized == null ? defaultValue : normalized;
        }

        private static String normalizeNullable(String value) {
            if (value == null) {
                return null;
            }
            String normalized = value.trim();
            return normalized.isEmpty() ? null : normalized;
        }
    }

    public record ApproveStagingCardsRequest(
            @NotEmpty(message = "At least one staging card id is required")
            @Size(max = 500, message = "Approval request must not exceed 500 staging cards")
            List<@NotNull(message = "Staging card id must not be null") UUID> stagingCardIds
    ) {
    }

    public record RejectStagingCardsRequest(
            @NotEmpty(message = "At least one staging card id is required")
            @Size(max = 500, message = "Rejection request must not exceed 500 staging cards")
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
            boolean imported,
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

    public record RejectStagingCardsResponse(
            int rejectedCount
    ) {
    }
}
