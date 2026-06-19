package com.smartlearnly.backend.enrollment.repository;

import com.smartlearnly.backend.enrollment.entity.ClassEnrollment;
import com.smartlearnly.backend.enrollment.entity.EnrollmentStatus;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClassEnrollmentRepository extends JpaRepository<ClassEnrollment, UUID> {
    Optional<ClassEnrollment> findByClassIdAndStudentId(UUID classId, UUID studentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select enrollment
            from ClassEnrollment enrollment
            where enrollment.classId = :classId and enrollment.studentId = :studentId
            """)
    Optional<ClassEnrollment> findByClassIdAndStudentIdForUpdate(
            @Param("classId") UUID classId,
            @Param("studentId") UUID studentId
    );

    long countByClassIdAndStatus(UUID classId, EnrollmentStatus status);
}
