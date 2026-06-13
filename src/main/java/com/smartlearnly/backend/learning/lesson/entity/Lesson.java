package com.smartlearnly.backend.learning.lesson.entity;

import com.smartlearnly.backend.course.entity.Course;
import com.smartlearnly.backend.learning.module.entity.CourseSection;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
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
    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "section_id", nullable = false)
    private CourseSection section;

    @Column(nullable = false)
    private String title;

    @Convert(converter = LessonTypeConverter.class)
    @Column(name = "lesson_type", nullable = false, columnDefinition = "lesson_type")
    @ColumnTransformer(write = "?::lesson_type")
    private LessonType type;

    @Column(name = "video_url")
    private String videoUrl;

    @Column
    private String content;

    @Column(name = "attachment_url")
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

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

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
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
