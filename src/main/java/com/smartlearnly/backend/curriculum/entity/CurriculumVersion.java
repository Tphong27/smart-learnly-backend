package com.smartlearnly.backend.curriculum.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
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

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "curriculum_versions", schema = "public")
public class CurriculumVersion {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(name = "class_id")
    private UUID classId;

    @Convert(converter = CurriculumScopeConverter.class)
    @Column(nullable = false, columnDefinition = "curriculum_scope")
    @ColumnTransformer(write = "?::curriculum_scope")
    private CurriculumScope scope;

    @Convert(converter = CurriculumStatusConverter.class)
    @Column(nullable = false, columnDefinition = "curriculum_status")
    @ColumnTransformer(write = "?::curriculum_status")
    private CurriculumStatus status;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @Column
    private String title;

    @Column(name = "source_version_id")
    private UUID sourceVersionId;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @OneToMany(mappedBy = "curriculumVersion", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC, createdAt ASC")
    private List<CurriculumSection> sections = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (status == null) {
            status = CurriculumStatus.DRAFT;
        }
        if (versionNumber == null) {
            versionNumber = 1;
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

    public void addSection(CurriculumSection section) {
        section.setCurriculumVersion(this);
        sections.add(section);
    }
}
