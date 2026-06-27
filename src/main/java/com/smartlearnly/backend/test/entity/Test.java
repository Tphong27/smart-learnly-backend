package com.smartlearnly.backend.test.entity;


import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
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
@Table(name = "tests", schema = "public")
public class Test {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "module_id")
    private UUID moduleId;

    @Column(name = "class_id")
    private UUID classId;

    @Column(name = "course_id")
    private UUID courseId;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Convert(converter = TestTypeConverter.class)
    @Column(name = "test_type", nullable = false, columnDefinition = "test_type")
    @ColumnTransformer(write = "?::test_type")
    private TestType testType;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Column(name = "max_attempts")
    private Integer maxAttempts;

    @Column(name = "pass_score")
    private BigDecimal passScore;

    @Column(name = "shuffle_questions", nullable = false)
    private Boolean shuffleQuestions;

    @Column(name = "shuffle_answers", nullable = false)
    private Boolean shuffleAnswers;

    @Column(name = "show_answers_after", nullable = false)
    private Boolean showAnswersAfter;

    @Column(name = "is_published", nullable = false)
    private Boolean isPublished;

    @Column(name = "is_archived", nullable = false)
    private Boolean isArchived;

    @Column(name = "is_flashtest", nullable = false)
    private Boolean isFlashtest;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (shuffleQuestions == null) shuffleQuestions = false;
        if (shuffleAnswers == null) shuffleAnswers = false;
        if (showAnswersAfter == null) showAnswersAfter = true;
        if (isPublished == null) isPublished = false;
        if (isArchived == null) isArchived = false;
        if (isFlashtest == null) isFlashtest = false;
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
