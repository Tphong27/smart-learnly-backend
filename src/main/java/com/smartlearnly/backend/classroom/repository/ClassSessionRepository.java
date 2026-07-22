package com.smartlearnly.backend.classroom.repository;

import com.smartlearnly.backend.classroom.entity.ClassSession;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClassSessionRepository
        extends JpaRepository<ClassSession, UUID> {

    List<ClassSession> findByClassIdAndSessionDateGreaterThanEqualOrderBySessionDateAscStartTimeAsc(UUID classId,
            LocalDate fromDate);

    @Query(value = """
            SELECT
                class_session.id AS "sessionId",
                class_offering.id AS "classId",
                course.id AS "courseId",
                course.title AS "courseTitle",
                class_offering.class_name AS "className",
                class_session.session_date AS "sessionDate",
                class_session.start_time AS "startTime",
                class_session.end_time AS "endTime",
                COALESCE(
                    class_session.trainer_id,
                    class_offering.trainer_id
                ) AS "trainerId",
                trainer.full_name AS "trainerName",
                class_session.meeting_url AS "meetingUrl"
            FROM public.class_sessions class_session
            JOIN public.classes class_offering
                ON class_offering.id = class_session.class_id
            JOIN public.courses course
                ON course.id = class_offering.course_id
            JOIN public.class_enrollments enrollment
                ON enrollment.class_id = class_offering.id
            LEFT JOIN public.users trainer
                ON trainer.id = COALESCE(
                    class_session.trainer_id,
                    class_offering.trainer_id
                )
            WHERE enrollment.student_id = :studentId
              AND enrollment.status IN (
                    'active'::public.enroll_status,
                    'completed'::public.enroll_status
              )
              AND class_offering.deleted_at IS NULL
              AND class_offering.status <>
                    'cancelled'::public.class_status
              AND course.deleted_at IS NULL
              AND class_session.session_date
                    BETWEEN :weekStart AND :weekEnd
            ORDER BY
                class_session.session_date ASC,
                class_session.start_time ASC,
                class_session.end_time ASC,
                class_session.id ASC
            """, nativeQuery = true)
    List<ScheduleProjection> findTraineeSchedule(
            @Param("studentId") UUID studentId,
            @Param("weekStart") LocalDate weekStart,
            @Param("weekEnd") LocalDate weekEnd);

    @Query(value = """
            SELECT
                class_session.id AS "sessionId",
                class_offering.id AS "classId",
                course.id AS "courseId",
                course.title AS "courseTitle",
                class_offering.class_name AS "className",
                class_session.session_date AS "sessionDate",
                class_session.start_time AS "startTime",
                class_session.end_time AS "endTime",
                class_session.trainer_id AS "trainerId",
                trainer.full_name AS "trainerName",
                class_session.meeting_url AS "meetingUrl"
            FROM public.class_sessions class_session
            JOIN public.classes class_offering
                ON class_offering.id = class_session.class_id
            JOIN public.courses course
                ON course.id = class_offering.course_id
            JOIN public.users trainer
                ON trainer.id = class_session.trainer_id
            WHERE class_offering.deleted_at IS NULL
              AND class_offering.status <>
                    'cancelled'::public.class_status
              AND course.deleted_at IS NULL
              AND class_session.session_date
                    BETWEEN :weekStart AND :weekEnd
              AND (
                    CAST(:trainerId AS uuid) IS NULL
                    OR class_session.trainer_id =
                        CAST(:trainerId AS uuid)
              )
            ORDER BY
                class_session.session_date ASC,
                class_session.start_time ASC,
                class_session.end_time ASC,
                trainer.full_name ASC,
                class_session.id ASC
            """, nativeQuery = true)
    List<ScheduleProjection> findStaffSchedule(
            @Param("trainerId") UUID trainerId,
            @Param("weekStart") LocalDate weekStart,
            @Param("weekEnd") LocalDate weekEnd);
}