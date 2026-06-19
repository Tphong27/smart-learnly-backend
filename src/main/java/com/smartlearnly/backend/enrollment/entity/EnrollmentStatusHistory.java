package com.smartlearnly.backend.enrollment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
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
@Table(name = "enrollment_status_history", schema = "public")
public class EnrollmentStatusHistory {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "course_enrollment_id")
    private UUID courseEnrollmentId;

    @Column(name = "class_enrollment_id")
    private UUID classEnrollmentId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Convert(converter = EnrollmentStatusConverter.class)
    @Column(name = "from_status", columnDefinition = "enroll_status")
    @ColumnTransformer(write = "?::enroll_status")
    private EnrollmentStatus fromStatus;

    @Convert(converter = EnrollmentStatusConverter.class)
    @Column(name = "to_status", nullable = false, columnDefinition = "enroll_status")
    @ColumnTransformer(write = "?::enroll_status")
    private EnrollmentStatus toStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EnrollmentTransitionSource source;

    @Column
    private String reason;

    @Column(name = "transaction_id")
    private UUID transactionId;

    @Column(name = "changed_by")
    private UUID changedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
