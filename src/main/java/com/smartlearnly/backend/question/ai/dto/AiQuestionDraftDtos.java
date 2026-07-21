package com.smartlearnly.backend.question.ai.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class AiQuestionDraftDtos {
    private AiQuestionDraftDtos() {
    }

    public record SourceOptionResponse(
            UUID generationSourceId,
            UUID materialSnapshotId,
            UUID materialId,
            UUID courseId,
            UUID lessonId,
            UUID curriculumLessonId,
            String sourceName,
            String checksum,
            String version,
            String ragStatus,
            Instant updatedAt
    ) {
    }

    public record CreateBatchRequest(
            @NotEmpty(message = "At least one generation source is required")
            List<UUID> generationSourceIds,

            @NotEmpty(message = "At least one question type is required")
            List<String> questionTypes,

            @NotNull(message = "Requested count is required")
            Integer requestedCount,

            UUID moduleId,

            @NotBlank(message = "Language is required")
            String language,

            @Size(max = 2000, message = "Generation instruction must not exceed 2000 characters")
            String generationInstruction,

            @NotBlank(message = "Idempotency key is required")
            @Size(max = 120, message = "Idempotency key must not exceed 120 characters")
            String idempotencyKey
    ) {
    }

    public record AnswerPayload(
            @NotBlank(message = "Answer text is required")
            @Size(max = 4000, message = "Answer text must not exceed 4000 characters")
            String answerText,
            Boolean correct,
            Integer orderIndex
    ) {
        public boolean correctValue() {
            return Boolean.TRUE.equals(correct);
        }
    }

    public record EvidenceResponse(
            UUID evidenceId,
            UUID generationSourceId,
            UUID materialChunkId,
            String chunkReference,
            String sourceExcerpt,
            boolean supportsCorrectAnswer,
            String evidenceStatus,
            UUID reviewerConfirmedBy,
            Instant reviewerConfirmedAt
    ) {
    }

    public record DraftResponse(
            UUID draftId,
            UUID id,
            UUID batchId,
            String status,
            String validationStatus,
            String evidenceStatus,
            Integer version,
            String questionText,
            String questionType,
            String explanation,
            UUID moduleId,
            List<AnswerPayload> answers,
            List<String> validationWarnings,
            List<DuplicateCandidateResponse> duplicateCandidates,
            List<EvidenceResponse> evidences,
            UUID createdQuestionId,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record DuplicateCandidateResponse(
            UUID questionId,
            String questionText,
            String status,
            String matchType
    ) {
    }

    public record BatchResponse(
            UUID batchId,
            UUID id,
            UUID questionBankId,
            UUID courseId,
            UUID requestedBy,
            String status,
            Integer requestedCount,
            Integer generatedCount,
            Integer usableCount,
            String language,
            List<String> questionTypes,
            String generationInstruction,
            String provider,
            String model,
            Integer retryCount,
            String errorCode,
            String safeErrorMessage,
            List<SourceResponse> sources,
            List<DraftResponse> drafts,
            Instant createdAt,
            Instant updatedAt,
            Instant completedAt
    ) {
    }

    public record SourceResponse(
            UUID sourceId,
            UUID generationSourceId,
            String sourceKind,
            UUID materialId,
            UUID materialSnapshotId,
            String sourceName,
            String sourceChecksum,
            String sourceVersion,
            String ragStatus
    ) {
    }

    public record UpdateDraftRequest(
            @NotNull(message = "Version is required")
            Integer version,

            @NotBlank(message = "Question text is required")
            @Size(max = 10000, message = "Question text must not exceed 10000 characters")
            String questionText,

            @Size(max = 10000, message = "Explanation must not exceed 10000 characters")
            String explanation,

            UUID moduleId,

            @Valid
            @NotEmpty(message = "At least two answers are required")
            List<AnswerPayload> answers
    ) {
    }

    public record RejectDraftRequest(
            @NotNull(message = "Version is required")
            Integer version,
            String reasonCode,
            @Size(max = 1000, message = "Reject reason note must not exceed 1000 characters")
            String note
    ) {
    }

    public record EvidenceConfirmationRequest(
            @NotNull(message = "Version is required")
            Integer version,
            boolean evidenceStillFits
    ) {
    }

    public record AddSelectedRequest(
            @Valid
            @NotEmpty(message = "At least one draft must be selected")
            List<SelectedDraft> drafts,
            @NotBlank(message = "Idempotency key is required")
            @Size(max = 120, message = "Idempotency key must not exceed 120 characters")
            String idempotencyKey
    ) {
    }

    public record SelectedDraft(
            @NotNull(message = "Draft ID is required")
            UUID draftId,
            @NotNull(message = "Version is required")
            Integer version
    ) {
    }

    public record AddSelectedResponse(
            List<CreatedQuestion> created,
            List<SkippedItem> skippedItems
    ) {
    }

    public record CreatedQuestion(UUID draftId, UUID questionId) {
    }

    public record SkippedItem(UUID draftId, String reasonCode, String message) {
    }
}
