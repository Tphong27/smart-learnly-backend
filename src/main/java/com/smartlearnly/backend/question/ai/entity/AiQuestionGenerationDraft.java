package com.smartlearnly.backend.question.ai.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnTransformer;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "ai_question_generation_drafts", schema = "public")
public class AiQuestionGenerationDraft {
    public static final String STATUS_GENERATED_DRAFT = "generated_draft";
    public static final String STATUS_ACCEPTED = "accepted";
    public static final String STATUS_REJECTED = "rejected";
    public static final String VALIDATION_VALID = "valid";
    public static final String VALIDATION_WARNING = "warning";
    public static final String VALIDATION_INVALID = "invalid";
    public static final String EVIDENCE_VALID = "valid";
    public static final String EVIDENCE_NEEDS_REVIEW = "needs_review";
    public static final String EVIDENCE_INVALID = "invalid";

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "batch_id", nullable = false)
    private UUID batchId;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "validation_status", nullable = false, length = 32)
    private String validationStatus;

    @Column(name = "evidence_status", nullable = false, length = 32)
    private String evidenceStatus;

    @Version
    @Column(nullable = false)
    private Integer version;

    @Column(name = "question_text", nullable = false, columnDefinition = "TEXT")
    private String questionText;

    @Column(name = "question_type", nullable = false, length = 32)
    private String questionType;

    @Column(columnDefinition = "TEXT")
    private String explanation;

    @Column(name = "module_id")
    private UUID moduleId;

    @Column(name = "answers_json", nullable = false, columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String answersJson;

    @Column(name = "validation_warnings", nullable = false, columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String validationWarnings;

    @Column(name = "duplicate_candidates", nullable = false, columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String duplicateCandidates;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "created_question_id")
    private UUID createdQuestionId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (status == null) status = STATUS_GENERATED_DRAFT;
        if (validationStatus == null) validationStatus = VALIDATION_INVALID;
        if (evidenceStatus == null) evidenceStatus = EVIDENCE_INVALID;
        if (validationWarnings == null) validationWarnings = "[]";
        if (duplicateCandidates == null) duplicateCandidates = "[]";
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
