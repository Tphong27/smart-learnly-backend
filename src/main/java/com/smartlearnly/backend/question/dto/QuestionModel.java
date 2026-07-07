package com.smartlearnly.backend.question.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class QuestionModel {

    private QuestionModel() {
    }

    public record AnswerRequest(
            UUID answerId,
            UUID id,

            @NotBlank(message = "Answer text is required")
            @Size(max = 4000, message = "Answer text must not exceed 4000 characters")
            String answerText,

            Boolean correct,
            Boolean isCorrect,
            Integer displayOrder,
            Integer orderIndex
    ) {
        public boolean correctValue() {
            return Boolean.TRUE.equals(correct) || Boolean.TRUE.equals(isCorrect);
        }

        public Integer resolvedOrder() {
            return displayOrder != null ? displayOrder : orderIndex;
        }
    }

    public record CreateRequest(
            UUID bankId,
            UUID questionBankId,
            UUID courseId,
            UUID moduleId,

            @NotBlank(message = "Question text is required")
            @Size(max = 10000, message = "Question text must not exceed 10000 characters")
            String questionText,

            @NotBlank(message = "Question type is required")
            String questionType,

            String bloomLevel,

            @Min(value = 1, message = "Difficulty must be between 1 and 5")
            @Max(value = 5, message = "Difficulty must be between 1 and 5")
            Short difficulty,

            @Size(max = 10000, message = "Explanation must not exceed 10000 characters")
            String explanation,

            String status,

            @Valid
            @NotEmpty(message = "At least two answers are required")
            List<AnswerRequest> answers
    ) {
        public UUID resolvedBankId() {
            return bankId != null ? bankId : questionBankId;
        }
    }

    public record UpdateRequest(
            UUID bankId,
            UUID questionBankId,
            UUID courseId,
            UUID moduleId,

            @NotBlank(message = "Question text is required")
            @Size(max = 10000, message = "Question text must not exceed 10000 characters")
            String questionText,

            @NotBlank(message = "Question type is required")
            String questionType,

            String bloomLevel,

            @Min(value = 1, message = "Difficulty must be between 1 and 5")
            @Max(value = 5, message = "Difficulty must be between 1 and 5")
            Short difficulty,

            @Size(max = 10000, message = "Explanation must not exceed 10000 characters")
            String explanation,

            String status,

            @Valid
            @NotEmpty(message = "At least two answers are required")
            List<AnswerRequest> answers
    ) {
        public UUID resolvedBankId() {
            return bankId != null ? bankId : questionBankId;
        }
    }

    public record AnswerResponse(
            UUID answerId,
            UUID id,
            String answerText,
            boolean correct,
            boolean isCorrect,
            int displayOrder,
            int orderIndex
    ) {
    }

    public record Response(
            UUID questionId,
            UUID id,
            UUID bankId,
            UUID questionBankId,
            UUID courseId,
            UUID moduleId,
            String questionText,
            String questionType,
            String bloomLevel,
            Short difficulty,
            String explanation,
            String imageUrl,
            String audioUrl,
            List<QuestionMediaAttachmentResponse> mediaAttachments,
            boolean aiGenerated,
            String importSource,
            String status,
            long answerCount,
            List<AnswerResponse> answers,
            UUID createdBy,
            UUID reviewedBy,
            Instant reviewedAt,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}
