package com.smartlearnly.backend.enrollment.repository;

import com.smartlearnly.backend.enrollment.entity.EnrollmentStatusHistory;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EnrollmentStatusHistoryRepository extends JpaRepository<EnrollmentStatusHistory, UUID> {
    @Query("""
            select history
            from EnrollmentStatusHistory history
            where history.courseEnrollmentId = :enrollmentId
            order by history.createdAt asc, history.id asc
            """)
    List<EnrollmentStatusHistory> findCourseHistory(@Param("enrollmentId") UUID enrollmentId);
}
