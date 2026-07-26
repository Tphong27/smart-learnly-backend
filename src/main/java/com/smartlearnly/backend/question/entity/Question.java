package com.smartlearnly.backend.question.entity;


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
@Table(name = "questions", schema = "public")
public class Question {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "question_bank_id")
    private UUID questionBankId;

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(name = "module_id")
    private UUID moduleId;

    @Column(name = "clo_id")
    private UUID cloId;

    @Column(name = "question_text", nullable = false, columnDefinition = "TEXT")
    private String questionText;

    @Convert(converter = QuestionTypeConverter.class)
    @Column(name = "question_type", nullable = false, columnDefinition = "question_type")
    @ColumnTransformer(write = "?::question_type")
    private QuestionType questionType;

    @Convert(converter = BloomLevelConverter.class)
    @Column(name = "bloom_level", columnDefinition = "bloom_level")
    @ColumnTransformer(write = "?::bloom_level")
    private BloomLevel bloomLevel;

    private Short difficulty;

    @Column(columnDefinition = "TEXT")
    private String explanation;


    @Column(name = "is_ai_generated", nullable = false)
    private Boolean isAiGenerated;

    @Column(name = "import_source", length = 50)
    private String importSource;

    @Convert(converter = QuestionStatusConverter.class)
    @Column(nullable = false, columnDefinition = "question_status")
    @ColumnTransformer(write = "?::question_status")
    private QuestionStatus status;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (isAiGenerated == null) {
            isAiGenerated = false;
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
