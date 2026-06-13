package com.smartlearnly.backend.learning.lesson.entity;

import com.smartlearnly.backend.learning.module.entity.CourseModule;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
@Table(name = "lessons", schema = "public")
public class Lesson {
    public static final String TYPE_RICH_TEXT = "rich_text";
    public static final String TYPE_VIDEO = "video";
    public static final String TYPE_PDF = "pdf";
    public static final String TYPE_QUIZ = "quiz";
    public static final String TYPE_ASSIGNMENT = "assignment";
    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_INACTIVE = "inactive";

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "module_id", nullable = false)
    private CourseModule module;

    @Column(nullable = false)
    private String title;

    @Column
    private String content;

    @Column(name = "lesson_type", nullable = false, columnDefinition = "lesson_type")
    @ColumnTransformer(write = "?::lesson_type")
    private String lessonType;

    @Column(name = "order_index", nullable = false)
    private Integer orderIndex;

    @Column(name = "is_preview", nullable = false)
    private boolean preview;

    @Column(nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
        if (lessonType == null || lessonType.isBlank()) {
            lessonType = TYPE_RICH_TEXT;
        }
        if (orderIndex == null) {
            orderIndex = 0;
        }
        if (status == null || status.isBlank()) {
            status = STATUS_ACTIVE;
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
