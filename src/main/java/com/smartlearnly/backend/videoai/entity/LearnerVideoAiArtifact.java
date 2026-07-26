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
import org.hibernate.annotations.ColumnTransformer;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "learner_video_ai_artifacts", schema = "public")
public class LearnerVideoAiArtifact {
    @Id
    @GeneratedValue
    private UUID id;
    @Column(name = "student_id", nullable = false)
    private UUID studentId;
    @Column(name = "course_id", nullable = false)
    private UUID courseId;
    @Column(name = "class_id")
    private UUID classId;
    @Column(name = "lesson_id", nullable = false)
    private UUID lessonId;
    @Column(name = "source_version", nullable = false)
    private UUID sourceVersion;
    @Column(name = "artifact_type", nullable = false, length = 16)
    private String artifactType;
    @Column(name = "content_json", nullable = false, columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String contentJson;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
