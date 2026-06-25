package com.smartlearnly.backend.test.entity;


import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
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
@Table(name = "test_attempts", schema = "public")
public class TestAttempt {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "test_id", nullable = false)
    private UUID testId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time")
    private Instant endTime;

    private BigDecimal score;

    @Convert(converter = AttemptStatusConverter.class)
    @Column(nullable = false, columnDefinition = "attempt_status")
    @ColumnTransformer(write = "?::attempt_status")
    private AttemptStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "assignment_id")
    private UUID assignmentId;

    @PrePersist
    void prePersist() {
        if (startTime == null) {
            startTime = Instant.now();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
