package com.smartlearnly.backend.videoai.entity;

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
@Table(name = "video_ai_jobs", schema = "public")
public class VideoAiJob {
    @Id
    @GeneratedValue
    private UUID id;
    @Column(name = "lesson_id", nullable = false)
    private UUID lessonId;
    @Column(name = "lesson_scope", nullable = false, length = 16)
    private String lessonScope;
    @Column(name = "course_id", nullable = false)
    private UUID courseId;
    @Column(name = "class_id")
    private UUID classId;
    @Column(name = "source_version", nullable = false)
    private UUID sourceVersion;
    @Column(name = "job_type", nullable = false, length = 32)
    private String jobType = "VIDEO_ARTIFACTS";
    @Column(nullable = false, length = 16)
    private String status = "pending";
    @Column(nullable = false, length = 32)
    private String stage = "queued";
    @Column(name = "progress_percent", nullable = false)
    private Integer progressPercent = 0;
    @Column(name = "source_language", nullable = false, length = 16)
    private String sourceLanguage = "auto";
    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount = 0;
    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;
    @Column(name = "content_id")
    private UUID contentId;
    @Column(name = "target_lesson_id")
    private UUID targetLessonId;
    @Column(name = "result_batch_id")
    private UUID resultBatchId;
    @Column(name = "result_set_id")
    private UUID resultSetId;
    @Column(name = "desired_count")
    private Integer desiredCount;
    @Column(length = 16)
    private String difficulty;
    @Column(name = "requested_by", nullable = false)
    private UUID requestedBy;
    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;
    @Column(name = "lease_expires_at")
    private Instant leaseExpiresAt;
    @Column(name = "lease_owner")
    private UUID leaseOwner;
    @Column(name = "lease_heartbeat_at")
    private Instant leaseHeartbeatAt;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "started_at")
    private Instant startedAt;
    @Column(name = "completed_at")
    private Instant completedAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (nextAttemptAt == null) nextAttemptAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
