package com.smartlearnly.backend.hls.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "hls_lessons", schema = "public")
public class HlsLesson {

    @Id
    @Column(name = "lesson_id")
    private UUID lessonId;

    @Column(name = "r2_base_path")
    private String r2BasePath;

    @Column(name = "hls_status")
    private String hlsStatus;

    @Column(name = "encryption_key_path")
    private String encryptionKeyPath;

    @Column(name = "qualities")
    private String qualities;

    @Column(name = "progress_percent")
    private Integer progressPercent = 0;

    @Column(name = "current_step")
    private String currentStep;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "processing_job_id")
    private UUID processingJobId;

    @Column(name = "source_object_key", length = 1024)
    private String sourceObjectKey;

    @Column(name = "processing_output_prefix", length = 1024)
    private String processingOutputPrefix;

    @Column(name = "processing_provider", length = 32)
    private String processingProvider;

    @Column(name = "workflow_dispatched_at")
    private Instant workflowDispatchedAt;

    @Column(name = "processing_completed_at")
    private Instant processingCompletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
        if (hlsStatus == null) {
            hlsStatus = "pending";
        }
        if (qualities == null) {
            qualities = "480p,720p";
        }
        if (progressPercent == null) {
            progressPercent = 0;
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public boolean isReady() {
        return "ready".equals(hlsStatus);
    }
}
