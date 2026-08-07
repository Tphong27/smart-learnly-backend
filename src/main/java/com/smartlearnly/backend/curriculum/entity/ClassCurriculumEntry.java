package com.smartlearnly.backend.curriculum.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

/**
 * Thin reference to a lesson within a CLASS-scoped curriculum version (the reference + delta
 * composition model).
 *
 * <p>An entry either inherits a master lesson (sourceCurriculumLessonId set, no content copied)
 * or owns a materialized {@link CurriculumLesson} row (materializedLessonId set). Inherited
 * lessons resolve their content against the current published master at read time and follow
 * master updates until the trainer edits them or attaches an artifact, which materializes the
 * row ({@code materialize-on-write}).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "class_curriculum_entries", schema = "public")
@SQLRestriction("deleted_at IS NULL")
public class ClassCurriculumEntry {
    @Id
    @GeneratedValue
    private UUID id;

    // Denormalized copy of the owning class version id, kept in sync from section.curriculumVersion
    // (mirrors CurriculumLesson.curriculumVersionId).
    @Column(name = "class_version_id", nullable = false)
    private UUID classVersionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "section_id", nullable = false)
    private CurriculumSection section;

    @Column(name = "source_curriculum_lesson_id")
    private UUID sourceCurriculumLessonId;

    @Column(name = "lesson_identity_id", nullable = false)
    private UUID lessonIdentityId;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "hidden", nullable = false)
    private Boolean hidden;

    @Column(name = "materialized_lesson_id")
    private UUID materializedLessonId;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (sortOrder == null) {
            sortOrder = 0;
        }
        if (hidden == null) {
            hidden = false;
        }
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
        syncClassVersionId();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
        syncClassVersionId();
    }

    private void syncClassVersionId() {
        if (section != null && section.getCurriculumVersion() != null) {
            classVersionId = section.getCurriculumVersion().getId();
        }
    }
}
