package com.smartlearnly.backend.rag.entity;

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
@Table(name = "rag_material_snapshots", schema = "public")
public class RagMaterialSnapshot {
    public static final String STATUS_READY = "ready";

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(name = "lesson_id")
    private UUID lessonId;

    @Column(name = "curriculum_lesson_id")
    private UUID curriculumLessonId;

    @Column(name = "lesson_resource_id")
    private UUID lessonResourceId;

    @Column(name = "curriculum_lesson_resource_id")
    private UUID curriculumLessonResourceId;

    @Column(name = "source_name", nullable = false)
    private String sourceName;

    @Column(nullable = false, length = 128)
    private String checksum;

    @Column(nullable = false, length = 64)
    private String version;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "extracted_at")
    private Instant extractedAt;

    @Column(name = "chunked_at")
    private Instant chunkedAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public boolean isReady() {
        return STATUS_READY.equalsIgnoreCase(status)
                && checksum != null && !checksum.isBlank()
                && version != null && !version.isBlank();
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (status == null) {
            status = "pending";
        }
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
