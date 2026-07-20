package com.smartlearnly.backend.videoai.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class VideoAiDtos {
    private VideoAiDtos() {
    }

    public record GenerateJobRequest(
            @Pattern(regexp = "auto|vi|en", message = "sourceLanguage must be auto, vi, or en")
            String sourceLanguage
    ) {
    }

    public record JobResponse(
            UUID id,
            String jobType,
            String status,
            String stage,
            int progressPercent,
            int attemptCount,
            String errorMessage,
            UUID contentId,
            UUID targetLessonId,
            UUID batchId,
            UUID setId,
            Instant createdAt,
            Instant completedAt
    ) {
    }

    public record StatusResponse(
            boolean enabled,
            boolean eligible,
            String reason,
            boolean hlsReady,
            UUID sourceVersion,
            JobResponse activeJob,
            UUID contentId,
            String contentStatus,
            Long revision
    ) {
    }

    public record PublishContentRequest(@NotNull @Min(0) Long revision) {
    }

    public record TranscriptSegmentRequest(
            @NotNull @Min(0) Long startMs,
            @NotNull @Min(1) Long endMs,
            @NotBlank @Size(max = 10000) String text
    ) {
    }

    public record ChapterRequest(
            @NotNull @Min(0) Long startMs,
            @NotNull @Min(1) Long endMs,
            @NotBlank @Size(max = 255) String title,
            @Size(max = 10000) String summary
    ) {
    }

    public record SaveContentRequest(
            @NotNull @Min(0) Long revision,
            @Size(max = 50000) String summary,
            @Size(max = 30) List<@NotBlank @Size(max = 2000) String> keyPoints,
            @Valid @Size(max = 5000) List<TranscriptSegmentRequest> segments,
            @Valid @Size(max = 100) List<ChapterRequest> chapters
    ) {
    }

    public record TranscriptSegmentResponse(
            int index,
            long startMs,
            long endMs,
            String text
    ) {
    }

    public record ChapterResponse(
            int index,
            long startMs,
            long endMs,
            String title,
            String summary
    ) {
    }

    public record ContentResponse(
            UUID id,
            UUID lessonId,
            UUID sourceVersion,
            String language,
            String transcriptText,
            String summary,
            List<String> keyPoints,
            String status,
            long revision,
            List<TranscriptSegmentResponse> segments,
            List<ChapterResponse> chapters,
            Instant updatedAt,
            Instant publishedAt
    ) {
    }

    public record LearnerContentResponse(
            boolean available,
            ContentResponse content
    ) {
    }

    public record FlashcardTargetResponse(
            UUID lessonId,
            UUID setId,
            String title
    ) {
    }

    public record GenerateFlashcardsRequest(
            @NotNull UUID targetLessonId,
            @Min(1) @Max(30) Integer desiredCount,
            @Pattern(regexp = "easy|medium|hard", message = "difficulty must be easy, medium, or hard")
            String difficulty
    ) {
    }
}
