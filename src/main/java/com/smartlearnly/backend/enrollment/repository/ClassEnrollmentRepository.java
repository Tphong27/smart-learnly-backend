package com.smartlearnly.backend.enrollment.repository;

import com.smartlearnly.backend.enrollment.entity.ClassEnrollment;
import java.util.List;
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
                        @Param("studentId") UUID studentId);

        @Query(value = """
                        SELECT COUNT(*)
                        FROM public.class_enrollments enrollment
                        WHERE enrollment.class_id = :classId
                          AND LOWER(CAST(enrollment.status AS text)) = LOWER(:status)
                        """, nativeQuery = true)
        long countByClassIdAndStatus(
                        @Param("classId") UUID classId,
                        @Param("status") String status);

        @Query(value = """
                        SELECT enrollment.student_id
                        FROM public.class_enrollments enrollment
                        WHERE enrollment.class_id = :classId
                          AND enrollment.status IN (
                                'active'::public.enroll_status,
                                'completed'::public.enroll_status
                          )
                        """, nativeQuery = true)
        List<UUID> findActiveOrCompletedStudentIdsByClassId(@Param("classId") UUID classId);

        @Query(value = """
                        SELECT
                            course.id AS "id",
                            course.title AS "title",
                            course.slug AS "slug",
                            course.description AS "description",
                            course.price AS "price",
                            COALESCE(course.thumbnail_url, course.avatar_url) AS "avatarUrl",
                            course.is_featured AS "featured",

                            category.id AS "categoryId",
                            category.name AS "categoryName",
                            category.slug AS "categorySlug",

                            class_enrollment.id AS "enrollmentId",
                            class_enrollment.status::text AS "enrollmentStatus",
                            class_enrollment.enrollment_date AS "enrollmentDate",
                            'CLASS' AS "learningType",

                            course.status::text AS "courseStatus",
                            course.access_blocked_at AS "accessBlockedAt",
                            course.access_block_reason AS "accessBlockReason",

                            class_enrollment.id AS "classEnrollmentId",
                            class_offering.id AS "classId",
                            class_offering.class_name AS "className",
                            class_offering.status::text AS "classStatus",
                            trainer.full_name AS "classTrainerName",
                            class_offering.meeting_url AS "classMeetingUrl",
                            class_offering.schedule_description AS "classScheduleDescription",
                            class_offering.start_date AS "classStartDate",
                            class_offering.end_date AS "classEndDate",
                            class_offering.max_students AS "classMaxStudents",
                            (
                                SELECT COUNT(*)
                                FROM public.class_enrollments active_class_enrollment
                                WHERE active_class_enrollment.class_id = class_offering.id
                                  AND active_class_enrollment.status = 'active'::public.enroll_status
                            ) AS "classActiveEnrollmentCount"
                        FROM public.class_enrollments class_enrollment
                        JOIN public.classes class_offering
                            ON class_offering.id = class_enrollment.class_id
                           AND class_offering.deleted_at IS NULL
                        JOIN public.courses course
                            ON course.id = class_offering.course_id
                           AND course.deleted_at IS NULL
                        JOIN public.categories category
                            ON category.id = course.category_id
                        LEFT JOIN public.users trainer
                            ON trainer.id = class_offering.trainer_id
                        WHERE class_enrollment.student_id = :studentId
                          AND class_enrollment.status IN (
                              'active'::public.enroll_status,
                              'completed'::public.enroll_status
                          )
                        ORDER BY class_enrollment.enrollment_date DESC,
                                 class_offering.start_date ASC NULLS LAST,
                                 class_enrollment.id ASC
                        """, nativeQuery = true)
        List<MyCourseProjection> findActiveMyClasses(@Param("studentId") UUID studentId);
}
