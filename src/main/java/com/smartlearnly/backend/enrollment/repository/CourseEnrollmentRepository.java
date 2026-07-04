package com.smartlearnly.backend.enrollment.repository;

import com.smartlearnly.backend.enrollment.entity.CourseEnrollment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseEnrollmentRepository extends JpaRepository<CourseEnrollment, UUID> {
    Optional<CourseEnrollment> findByCourseIdAndStudentId(UUID courseId, UUID studentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select enrollment
            from CourseEnrollment enrollment
            where enrollment.courseId = :courseId and enrollment.studentId = :studentId
            """)
    Optional<CourseEnrollment> findByCourseIdAndStudentIdForUpdate(
            @Param("courseId") UUID courseId,
            @Param("studentId") UUID studentId
    );

    Optional<CourseEnrollment> findByIdAndStudentId(UUID id, UUID studentId);

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

                enrollment.id AS "enrollmentId",
                enrollment.status::text AS "enrollmentStatus",
                enrollment.enrollment_date AS "enrollmentDate",

                course.status::text AS "courseStatus",
                course.access_blocked_at AS "accessBlockedAt",
                course.access_block_reason AS "accessBlockReason",

                class_enrollment.id AS "classEnrollmentId",
                class_offering.id AS "classId",
                class_offering.class_name AS "className",
                class_offering.status::text AS "classStatus",
                trainer.full_name AS "classTrainerName",
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
            FROM public.course_enrollments enrollment
            JOIN public.courses course
                ON course.id = enrollment.course_id
            JOIN public.categories category
                ON category.id = course.category_id
            LEFT JOIN public.class_enrollments class_enrollment
                ON class_enrollment.student_id = enrollment.student_id
               AND class_enrollment.status IN (
                    'active'::public.enroll_status,
                    'completed'::public.enroll_status
               )
               AND EXISTS (
                    SELECT 1
                    FROM public.classes class_check
                    WHERE class_check.id = class_enrollment.class_id
                      AND class_check.course_id = course.id
                      AND class_check.deleted_at IS NULL
               )
            LEFT JOIN public.classes class_offering
                ON class_offering.id = class_enrollment.class_id
               AND class_offering.course_id = course.id
               AND class_offering.deleted_at IS NULL
            LEFT JOIN public.users trainer
                ON trainer.id = class_offering.trainer_id
            WHERE enrollment.student_id = :studentId
              AND enrollment.status IN (
                  'active'::public.enroll_status,
                  'completed'::public.enroll_status
              )
            ORDER BY enrollment.enrollment_date DESC,
                     class_offering.start_date ASC NULLS LAST,
                     enrollment.id ASC
            """, nativeQuery = true)
    List<MyCourseProjection> findActiveMyCourses(@Param("studentId") UUID studentId);

    @Query(
            value = """
                    SELECT
                        enrollment.id AS "enrollmentId",
                        course.id AS "courseId",
                        course.title AS "courseTitle",
                        course.slug AS "courseSlug",
                        enrollment.status::text AS "status",
                        enrollment.enrollment_date AS "enrollmentDate",
                        enrollment.updated_at AS "updatedAt"
                    FROM public.course_enrollments enrollment
                    JOIN public.courses course ON course.id = enrollment.course_id
                    WHERE enrollment.student_id = :studentId
                    ORDER BY enrollment.enrollment_date DESC, enrollment.id ASC
                    """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM public.course_enrollments enrollment
                    WHERE enrollment.student_id = :studentId
                    """,
            nativeQuery = true)
    Page<EnrollmentHistoryProjection> findHistory(
            @Param("studentId") UUID studentId,
            Pageable pageable
    );
}
