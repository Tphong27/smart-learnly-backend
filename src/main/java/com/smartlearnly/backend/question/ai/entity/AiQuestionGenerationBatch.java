package com.smartlearnly.backend.question.ai.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
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
@Table(name = "ai_question_generation_batches", schema = "public")
public class AiQuestionGenerationBatch {
    public static final String STATUS_REQUESTED = "requested";
    public static final String STATUS_PROCESSING = "processing";
    public static final String STATUS_READY = "ready";
    public static final String STATUS_FAILED = "failed";

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "question_bank_id", nullable = false)
    private UUID questionBankId;

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(name = "requested_by", nullable = false)
    private UUID requestedBy;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "generation_instruction", columnDefinition = "TEXT")
    private String generationInstruction;

    @Column(name = "instruction_snapshot", columnDefinition = "TEXT")
    private String instructionSnapshot;

    @Column(name = "requested_question_types", nullable = false, columnDefinition = "TEXT")
    private String requestedQuestionTypes;

    @Column(name = "requested_count", nullable = false)
    private Integer requestedCount;

    @Column(name = "generated_count", nullable = false)
    private Integer generatedCount;

    @Column(name = "usable_count", nullable = false)
    private Integer usableCount;

    @Column(nullable = false, length = 8)
    private String language;

    @Column(name = "prompt_template_version", nullable = false, length = 64)
    private String promptTemplateVersion;

    @Column(nullable = false, length = 64)
    private String provider;

    @Column(nullable = false, length = 128)
    private String model;

    @Column(name = "idempotency_key", nullable = false, length = 120)
    private String idempotencyKey;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount;

    @Column(name = "quota_charged", nullable = false)
    private Boolean quotaCharged;

    @Column(name = "error_code", length = 80)
    private String errorCode;

    @Column(name = "safe_error_message", columnDefinition = "TEXT")
    private String safeErrorMessage;

    @Column(name = "usage_prompt_tokens")
    private Integer usagePromptTokens;

    @Column(name = "usage_completion_tokens")
    private Integer usageCompletionTokens;

    @Column(name = "usage_total_tokens")
    private Integer usageTotalTokens;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (status == null) status = STATUS_REQUESTED;
        if (generatedCount == null) generatedCount = 0;
        if (usableCount == null) usableCount = 0;
        if (retryCount == null) retryCount = 0;
        if (quotaCharged == null) quotaCharged = false;
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
