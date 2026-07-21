package com.smartlearnly.backend.question.ai.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "ai_question_generation_evidences", schema = "public")
public class AiQuestionGenerationEvidence {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "draft_id", nullable = false)
    private UUID draftId;

    @Column(name = "generation_source_id", nullable = false)
    private UUID generationSourceId;

    @Column(name = "material_chunk_id")
    private UUID materialChunkId;

    @Column(name = "chunk_reference", nullable = false)
    private String chunkReference;

    @Column(name = "source_excerpt", nullable = false, columnDefinition = "TEXT")
    private String sourceExcerpt;

    @Column(name = "supports_correct_answer", nullable = false)
    private Boolean supportsCorrectAnswer;

    @Column(name = "evidence_status", nullable = false, length = 32)
    private String evidenceStatus;

    @Column(name = "reviewer_confirmed_by")
    private UUID reviewerConfirmedBy;

    @Column(name = "reviewer_confirmed_at")
    private Instant reviewerConfirmedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (supportsCorrectAnswer == null) supportsCorrectAnswer = false;
        if (evidenceStatus == null) evidenceStatus = AiQuestionGenerationDraft.EVIDENCE_INVALID;
        if (createdAt == null) createdAt = Instant.now();
    }
}
