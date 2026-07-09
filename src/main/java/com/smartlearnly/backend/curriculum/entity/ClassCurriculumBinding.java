package com.smartlearnly.backend.curriculum.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
@Table(name = "class_curriculum_bindings", schema = "public")
public class ClassCurriculumBinding {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "class_id", nullable = false)
    private UUID classId;

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(name = "base_master_version_id", nullable = false)
    private UUID baseMasterVersionId;

    @Column(name = "draft_version_id")
    private UUID draftVersionId;

    @Column(name = "published_version_id")
    private UUID publishedVersionId;

    @Convert(converter = CurriculumCustomizationStateConverter.class)
    @Column(name = "customization_state", nullable = false, columnDefinition = "curriculum_customization_state")
    @ColumnTransformer(write = "?::curriculum_customization_state")
    private CurriculumCustomizationState customizationState;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (customizationState == null) {
            customizationState = CurriculumCustomizationState.INHERITED;
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
