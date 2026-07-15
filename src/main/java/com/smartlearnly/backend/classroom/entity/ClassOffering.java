package com.smartlearnly.backend.classroom.entity;

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
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnTransformer;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "classes", schema = "public")
public class ClassOffering {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(name = "class_name", nullable = false)
    private String className;

    @Column(name = "trainer_id")
    private UUID trainerId;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "schedule_description")
    private String scheduleDescription;

    @Column(name = "max_students", nullable = false)
    private Integer maxStudents;

    @Convert(converter = ClassStatusConverter.class)
    @Column(name = "status", nullable = false, columnDefinition = "class_status")
    @ColumnTransformer(write = "?::class_status")
    private ClassStatus status;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "price", nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (maxStudents == null) {
            maxStudents = 30;
        }
        if (status == null) {
            status = ClassStatus.UPCOMING;
        }
        // if (price == null) {
        //     price = BigDecimal.ZERO;
        // }
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
