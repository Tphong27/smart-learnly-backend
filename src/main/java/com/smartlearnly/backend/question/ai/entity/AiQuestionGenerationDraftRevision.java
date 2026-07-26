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
import org.hibernate.annotations.ColumnTransformer;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "ai_question_generation_draft_revisions", schema = "public")
public class AiQuestionGenerationDraftRevision {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "draft_id", nullable = false)
    private UUID draftId;

    @Column(name = "changed_by")
    private UUID changedBy;

    @Column(name = "before_snapshot", columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String beforeSnapshot;

    @Column(name = "after_snapshot", columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String afterSnapshot;

    @Column(name = "change_type", nullable = false, length = 32)
    private String changeType;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
