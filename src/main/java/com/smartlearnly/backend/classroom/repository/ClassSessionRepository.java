package com.smartlearnly.backend.classroom.repository;

import com.smartlearnly.backend.classroom.entity.ClassSession;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClassSessionRepository extends JpaRepository<ClassSession, UUID> {

    /** Lấy các buổi học sắp tới của một lớp theo thời gian tăng dần. */
    List<ClassSession> findByClassIdAndSessionDateGreaterThanEqualOrderBySessionDateAscStartTimeAsc(UUID classId,
            LocalDate fromDate);

    /** Lấy các buổi học lân cận để kiểm tra xung đột lịch của giảng viên. */
    @Query(value = """
            SELECT class_session.*
            FROM public.class_sessions class_session
            JOIN public.classes class_offering
                ON class_offering.id = class_session.class_id
            WHERE class_session.trainer_id = :trainerId
              AND class_session.class_id <> :classId
              AND class_session.session_date BETWEEN :fromDate AND :toDate
              AND class_offering.deleted_at IS NULL
              AND class_offering.status IN (
                    'upcoming'::public.class_status,
                    'ongoing'::public.class_status
              )
            ORDER BY
                class_session.session_date,
                class_session.start_time,
                class_session.end_time
            """, nativeQuery = true)
    List<ClassSession> findTrainerSessionsForConflictCheck(
            @Param("trainerId") UUID trainerId,
            @Param("classId") UUID classId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate);
}
