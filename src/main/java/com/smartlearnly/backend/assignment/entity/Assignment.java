package com.smartlearnly.backend.assignment.entity;


import jakarta.persistence.Column;
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

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "assignments", schema = "public")
public class Assignment {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "class_id", nullable = true)
    private UUID classId;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "instruction_file_url", columnDefinition = "TEXT")
    private String instructionFileUrl;

    @Column(name = "instruction_file_name")
    private String instructionFileName;

    @Column(name = "due_date")
    private Instant dueDate;

    @Column(name = "allow_late_submission", nullable = false)
    private Boolean allowLateSubmission;

    @Column(name = "lockout_date")
    private Instant lockoutDate;

    @Column(name = "max_score")
    private BigDecimal maxScore;

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

    @Column(name = "test_id")
    private UUID testId;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (allowLateSubmission == null) {
            allowLateSubmission = false;
        }
        if (isArchived == null) {
            isArchived = false;
        }
        if (isFlashtest == null) {
            isFlashtest = false;
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
