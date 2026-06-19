package com.smartlearnly.backend.enrollment.entity;

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
@Table(name = "class_enrollments", schema = "public")
public class ClassEnrollment {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "class_id", nullable = false)
    private UUID classId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "enrollment_date", nullable = false)
    private Instant enrollmentDate;

    @Column(nullable = false)
    private BigDecimal price;

    @Convert(converter = EnrollmentStatusConverter.class)
    @Column(nullable = false, columnDefinition = "enroll_status")
    @ColumnTransformer(write = "?::enroll_status")
    private EnrollmentStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (enrollmentDate == null) {
            enrollmentDate = now;
        }
        if (price == null) {
            price = BigDecimal.ZERO;
        }
        if (status == null) {
            status = EnrollmentStatus.ACTIVE;
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
