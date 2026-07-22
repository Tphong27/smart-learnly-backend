package com.smartlearnly.backend.curriculum.entity;

import com.smartlearnly.backend.learning.lesson.entity.LessonStatus;
import com.smartlearnly.backend.learning.lesson.entity.LessonStatusConverter;
import com.smartlearnly.backend.learning.lesson.entity.LessonType;
import com.smartlearnly.backend.learning.lesson.entity.LessonTypeConverter;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.SQLRestriction;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "curriculum_lessons", schema = "public")
@SQLRestriction("deleted_at IS NULL")
public class CurriculumLesson {
    @Id
    @GeneratedValue
    private UUID id;

    // FK composite (curriculum_section_id, curriculum_version_id) → curriculum_sections(id, curriculum_version_id).
    // Cột version_id được ghi bằng tay trong @PrePersist/@PreUpdate từ section.curriculumVersion để tránh xung đột
    // giữa Hibernate composite JoinColumn (insertable=false gây null) và NOT NULL constraint của DB.
    @Column(name = "curriculum_version_id", nullable = false)
    private UUID curriculumVersionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "curriculum_section_id", referencedColumnName = "id", nullable = false)
    private CurriculumSection section;

    @Column(name = "lesson_identity_id", nullable = false)
    private UUID lessonIdentityId;

    @Column(name = "source_lesson_id")
    private UUID sourceLessonId;

    @Column(name = "source_curriculum_lesson_id")
    private UUID sourceCurriculumLessonId;

    @Column(nullable = false)
    private String title;

    @Convert(converter = LessonTypeConverter.class)
    @Column(name = "lesson_type", nullable = false, columnDefinition = "lesson_type")
    @ColumnTransformer(write = "?::lesson_type")
    private LessonType type;

    @Column(name = "video_url", length = 500)
    private String videoUrl;

    @Column
    private String content;

    @Column(name = "attachment_url", length = 500)
    private String attachmentUrl;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "is_preview", nullable = false)
    private Boolean preview;

    @Convert(converter = LessonStatusConverter.class)
    @Column(nullable = false, columnDefinition = "lesson_status")
    @ColumnTransformer(write = "?::lesson_status")
    private LessonStatus status;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "test_id")
    private UUID testId;

    @OneToMany(mappedBy = "lesson", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC, createdAt ASC")
    private List<CurriculumLessonResource> resources = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (preview == null) {
            preview = false;
        }
        if (status == null) {
            status = LessonStatus.DRAFT;
        }
        if (sortOrder == null) {
            sortOrder = 0;
        }
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
        syncCurriculumVersionId();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
        syncCurriculumVersionId();
    }

    private void syncCurriculumVersionId() {
        if (section != null && section.getCurriculumVersion() != null) {
            curriculumVersionId = section.getCurriculumVersion().getId();
        }
    }

    public void addResource(CurriculumLessonResource resource) {
        resource.setLesson(this);
        resources.add(resource);
    }
}
