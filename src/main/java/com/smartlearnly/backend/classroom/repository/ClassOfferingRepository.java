package com.smartlearnly.backend.classroom.repository;

import com.smartlearnly.backend.classroom.entity.ClassOffering;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface ClassOfferingRepository extends JpaRepository<ClassOffering, UUID> {
    Optional<ClassOffering> findByIdAndDeletedAtIsNull(UUID id);

    @Query(value = """
            SELECT
                cls.id AS "classId",
                cls.course_id AS "courseId",
                course.title AS "courseTitle",
                course.slug AS "courseSlug",
                COALESCE(
                    course.thumbnail_url,
                    course.avatar_url
                ) AS "courseThumbnailUrl",
                cls.class_name AS "className",
                cls.trainer_id AS "trainerId",
                trainer.full_name AS "trainerName",
                cls.start_date AS "startDate",
                cls.end_date AS "endDate",
                cls.schedule_description AS "scheduleDescription",
                cls.price AS "price",
                cls.max_students AS "maxStudents",
                COUNT(enrollment.id) FILTER (
                    WHERE enrollment.status =
                        'active'::public.enroll_status
                ) AS "activeEnrollmentCount",
                cls.status::text AS "status"
            FROM public.classes cls
            JOIN public.courses course
                ON course.id = cls.course_id
            LEFT JOIN public.users trainer
                ON trainer.id = cls.trainer_id
            LEFT JOIN public.class_enrollments enrollment
                ON enrollment.class_id = cls.id
            WHERE cls.deleted_at IS NULL
              AND course.deleted_at IS NULL
              AND course.status = 'published'::public.course_status
              AND cls.status = 'upcoming'::public.class_status
              AND cls.start_date >= CURRENT_DATE
              AND cls.price IS NOT NULL
              AND (
                  :keyword IS NULL
                  OR cls.class_name ILIKE :keyword ESCAPE '\\'
                  OR course.title ILIKE :keyword ESCAPE '\\'
              )
              AND (:courseId IS NULL OR cls.course_id = :courseId)
              AND (
                    CAST(:startFrom AS date) IS NULL
                    OR cls.start_date >= CAST(:startFrom AS date)
                  )
              AND (
                    CAST(:startTo AS date) IS NULL
                    OR cls.start_date <= CAST(:startTo AS date)
                  )
              AND (:minPrice IS NULL OR cls.price >= :minPrice)
              AND (:maxPrice IS NULL OR cls.price <= :maxPrice)
            GROUP BY
                cls.id,
                course.id,
                trainer.id
            HAVING COUNT(enrollment.id) FILTER (
                WHERE enrollment.status =
                    'active'::public.enroll_status
            ) < cls.max_students
            ORDER BY
                cls.start_date ASC,
                cls.created_at DESC
            """, countQuery = """
                        SELECT COUNT(*)
                        FROM public.classes cls
                        JOIN public.courses course
                            ON course.id = cls.course_id
                        WHERE cls.deleted_at IS NULL
                          AND course.deleted_at IS NULL
                          AND course.status = 'published'::public.course_status
                          AND cls.status = 'upcoming'::public.class_status
                          AND cls.start_date >= CURRENT_DATE
                          AND cls.price IS NOT NULL
                          AND (
                              :keyword IS NULL
                              OR cls.class_name ILIKE :keyword ESCAPE '\\'
                              OR course.title ILIKE :keyword ESCAPE '\\'
                          )
                          AND (:courseId IS NULL OR cls.course_id = :courseId)
                          AND (
                                CAST(:startFrom AS date) IS NULL
                                OR cls.start_date >= CAST(:startFrom AS date)
                              )
                          AND (
                                CAST(:startTo AS date) IS NULL
                                OR cls.start_date <= CAST(:startTo AS date)
                              )
                          AND (:minPrice IS NULL OR cls.price >= :minPrice)
                          AND (:maxPrice IS NULL OR cls.price <= :maxPrice)
                          AND (
                                SELECT COUNT(*)
                                FROM public.class_enrollments counted_enrollment
                                WHERE counted_enrollment.class_id = cls.id
                                    AND counted_enrollment.status =
                                    'active'::public.enroll_status
                               ) < cls.max_students
            """, nativeQuery = true)
    Page<OpeningScheduleProjection> findOpeningSchedules(
            @Param("keyword") String keyword,
            @Param("courseId") UUID courseId,
            @Param("startFrom") LocalDate startFrom,
            @Param("startTo") LocalDate startTo,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice,
            Pageable pageable);

    @Query(value = """
            SELECT
                class_offering.id AS "classId",
                class_offering.course_id AS "courseId",
                course.title AS "courseTitle",
                course.slug AS "courseSlug",
                COALESCE(
                    course.thumbnail_url,
                    course.avatar_url
                ) AS "courseThumbnailUrl",
                class_offering.class_name AS "className",
                class_offering.trainer_id AS "trainerId",
                trainer.full_name AS "trainerName",
                class_offering.start_date AS "startDate",
                class_offering.end_date AS "endDate",
                class_offering.schedule_description
                    AS "scheduleDescription",
                class_offering.price AS "price",
                class_offering.max_students AS "maxStudents",
                COUNT(class_enrollment.id) FILTER (
                    WHERE class_enrollment.status =
                        'active'::public.enroll_status
                ) AS "activeEnrollmentCount",
                class_offering.status::text AS "status"
            FROM public.classes class_offering
            JOIN public.courses course
                ON course.id = class_offering.course_id
            LEFT JOIN public.users trainer
                ON trainer.id = class_offering.trainer_id
            LEFT JOIN public.class_enrollments class_enrollment
                ON class_enrollment.class_id = class_offering.id
            WHERE class_offering.id = :classId
              AND class_offering.deleted_at IS NULL
              AND course.deleted_at IS NULL
              AND course.status =
                  'published'::public.course_status
              AND class_offering.status =
                  'upcoming'::public.class_status
              AND class_offering.start_date >= CURRENT_DATE
              AND class_offering.price IS NOT NULL
            GROUP BY
                class_offering.id,
                course.id,
                trainer.id
            """, nativeQuery = true)
    Optional<OpeningScheduleProjection> findOpeningScheduleDetail(
            @Param("classId") UUID classId);

    @Query(value = """
            SELECT
                class_offering.id AS "id",
                class_offering.course_id AS "courseId",
                course.title AS "courseTitle",
                class_offering.class_name AS "className",
                class_offering.trainer_id AS "trainerId",
                trainer.full_name AS "trainerName",
                class_offering.schedule_description AS "scheduleDescription",
                class_offering.start_date AS "startDate",
                class_offering.end_date AS "endDate",
                class_offering.max_students AS "maxStudents",
                COUNT(class_enrollment.id) FILTER (
                    WHERE class_enrollment.status = 'active'::public.enroll_status
                ) AS "activeEnrollmentCount",
                class_offering.status::text AS "status",
                class_offering.created_at AS "createdAt",
                class_offering.updated_at AS "updatedAt"
            FROM public.classes class_offering
            JOIN public.courses course ON course.id = class_offering.course_id
            LEFT JOIN public.users trainer ON trainer.id = class_offering.trainer_id
            LEFT JOIN public.class_enrollments class_enrollment
                ON class_enrollment.class_id = class_offering.id
            WHERE class_offering.id = :classId
              AND class_offering.deleted_at IS NULL
            GROUP BY class_offering.id, course.title, trainer.full_name
            """, nativeQuery = true)
    Optional<ClassAdminProjection> findAdminClassById(@Param("classId") UUID classId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select classOffering from ClassOffering classOffering "
            + "where classOffering.id = :id and classOffering.deletedAt is null")
    Optional<ClassOffering> findByIdForUpdate(@Param("id") UUID id);

    @Query(value = """
            SELECT
                class_offering.id AS "id",
                class_offering.course_id AS "courseId",
                course.title AS "courseTitle",
                class_offering.class_name AS "className",
                class_offering.trainer_id AS "trainerId",
                trainer.full_name AS "trainerName",
                class_offering.schedule_description AS "scheduleDescription",
                class_offering.price AS "price",
                class_offering.start_date AS "startDate",
                class_offering.end_date AS "endDate",
                class_offering.max_students AS "maxStudents",
                COUNT(class_enrollment.id) FILTER (
                    WHERE class_enrollment.status = 'active'::public.enroll_status
                ) AS "activeEnrollmentCount",
                class_offering.status::text AS "status",
                class_offering.created_at AS "createdAt",
                class_offering.updated_at AS "updatedAt"
            FROM public.classes class_offering
            JOIN public.courses course ON course.id = class_offering.course_id
            LEFT JOIN public.users trainer ON trainer.id = class_offering.trainer_id
            LEFT JOIN public.class_enrollments class_enrollment
                ON class_enrollment.class_id = class_offering.id
            WHERE class_offering.deleted_at IS NULL
              AND (:courseId IS NULL OR class_offering.course_id = :courseId)
              AND (:trainerId IS NULL OR class_offering.trainer_id = :trainerId)
              AND (:status IS NULL OR class_offering.status::text = :status)
              AND (
                  :keyword IS NULL
                  OR class_offering.class_name ILIKE :keyword ESCAPE '\\'
              )
            GROUP BY class_offering.id, course.title, trainer.full_name
            ORDER BY class_offering.created_at DESC, class_offering.id ASC
            """, countQuery = """
            SELECT COUNT(*)
            FROM public.classes class_offering
            WHERE class_offering.deleted_at IS NULL
              AND (:courseId IS NULL OR class_offering.course_id = :courseId)
              AND (:trainerId IS NULL OR class_offering.trainer_id = :trainerId)
              AND (:status IS NULL OR class_offering.status::text = :status)
              AND (
                  :keyword IS NULL
                  OR class_offering.class_name ILIKE :keyword ESCAPE '\\'
              )
            """, nativeQuery = true)
    Page<ClassAdminProjection> findAdminClasses(
            @Param("courseId") UUID courseId,
            @Param("trainerId") UUID trainerId,
            @Param("status") String status,
            @Param("keyword") String keyword,
            Pageable pageable);

    @Query(value = """
            SELECT
                class_offering.id AS "id",
                class_offering.course_id AS "courseId",
                course.title AS "courseTitle",
                class_offering.class_name AS "className",
                class_offering.trainer_id AS "trainerId",
                trainer.full_name AS "trainerName",
                class_offering.schedule_description AS "scheduleDescription",
                class_offering.price AS "price",
                class_offering.start_date AS "startDate",
                class_offering.end_date AS "endDate",
                class_offering.max_students AS "maxStudents",
                COUNT(class_enrollment.id) FILTER (
                    WHERE class_enrollment.status = 'active'::public.enroll_status
                ) AS "activeEnrollmentCount",
                class_offering.status::text AS "status",
                class_offering.created_at AS "createdAt",
                class_offering.updated_at AS "updatedAt"
            FROM public.classes class_offering
            JOIN public.courses course ON course.id = class_offering.course_id
            LEFT JOIN public.users trainer ON trainer.id = class_offering.trainer_id
            LEFT JOIN public.class_enrollments class_enrollment
                ON class_enrollment.class_id = class_offering.id
            WHERE class_offering.deleted_at IS NULL
              AND class_offering.id = :classId
            GROUP BY class_offering.id, course.title, trainer.full_name
            """, nativeQuery = true)
    Optional<ClassAdminProjection> findAdminClassDetail(@Param("classId") UUID classId);

    @Query(value = """
            SELECT EXISTS (
                SELECT 1 FROM public.class_enrollments enrollment
                WHERE enrollment.class_id = :classId
                UNION ALL
                SELECT 1 FROM public.transactions tx
                WHERE tx.class_id = :classId
                UNION ALL
                SELECT 1 FROM public.order_items item
                WHERE item.class_id = :classId
            )
            """, nativeQuery = true)
    boolean hasCommercialHistory(@Param("classId") UUID classId);

    @Query(value = """
            SELECT
                class_offering.id AS "id",
                class_offering.course_id AS "courseId",
                class_offering.class_name AS "className",
                class_offering.trainer_id AS "trainerId",
                trainer.full_name AS "trainerName",
                class_offering.schedule_description AS "scheduleDescription",
                class_offering.price AS "price",
                class_offering.start_date AS "startDate",
                class_offering.end_date AS "endDate",
                class_offering.max_students AS "maxStudents",
                COUNT(class_enrollment.id) FILTER (
                    WHERE class_enrollment.status = 'active'::public.enroll_status
                ) AS "activeEnrollmentCount",
                class_offering.status::text AS "status"
            FROM public.classes class_offering
            LEFT JOIN public.users trainer ON trainer.id = class_offering.trainer_id
            LEFT JOIN public.class_enrollments class_enrollment
                ON class_enrollment.class_id = class_offering.id
            WHERE class_offering.course_id = :courseId
              AND class_offering.deleted_at IS NULL
              AND class_offering.status IN (
                  'upcoming'::public.class_status,
                  'ongoing'::public.class_status
              )
            GROUP BY class_offering.id, trainer.full_name
            ORDER BY class_offering.start_date ASC NULLS LAST,
                     class_offering.created_at DESC
            """, nativeQuery = true)
    List<CoursePublicProjection> findPublicClassesByCourseId(@Param("courseId") UUID courseId);

    @Query(value = """
            SELECT
                class_offering.id AS "id",
                class_offering.course_id AS "courseId",
                course.title AS "courseTitle",
                class_offering.class_name AS "className",
                class_offering.trainer_id AS "trainerId",
                trainer.full_name AS "trainerName",
                class_offering.schedule_description AS "scheduleDescription",
                class_offering.price AS "price",
                class_offering.start_date AS "startDate",
                class_offering.end_date AS "endDate",
                class_offering.max_students AS "maxStudents",
                COUNT(class_enrollment.id) FILTER (
                    WHERE class_enrollment.status = 'active'::public.enroll_status
                ) AS "activeEnrollmentCount",
                class_offering.status::text AS "status",
                class_offering.created_at AS "createdAt",
                class_offering.updated_at AS "updatedAt"
            FROM public.classes class_offering
            JOIN public.courses course ON course.id = class_offering.course_id
            LEFT JOIN public.users trainer ON trainer.id = class_offering.trainer_id
            LEFT JOIN public.class_enrollments class_enrollment
                ON class_enrollment.class_id = class_offering.id
            WHERE class_offering.deleted_at IS NULL
              AND class_offering.trainer_id = :trainerId
              AND (:status IS NULL OR class_offering.status::text = :status)
              AND (
                  :keyword IS NULL
                  OR class_offering.class_name ILIKE :keyword ESCAPE '\\'
                  OR course.title ILIKE :keyword ESCAPE '\\'
              )
            GROUP BY class_offering.id, course.title, trainer.full_name
            ORDER BY class_offering.start_date ASC NULLS LAST,
                     class_offering.created_at DESC,
                     class_offering.id ASC
            """, countQuery = """
            SELECT COUNT(*)
            FROM public.classes class_offering
            JOIN public.courses course ON course.id = class_offering.course_id
            WHERE class_offering.deleted_at IS NULL
              AND class_offering.trainer_id = :trainerId
              AND (:status IS NULL OR class_offering.status::text = :status)
              AND (
                  :keyword IS NULL
                  OR class_offering.class_name ILIKE :keyword ESCAPE '\\'
                  OR course.title ILIKE :keyword ESCAPE '\\'
              )
            """, nativeQuery = true)
    Page<ClassAdminProjection> findTrainerAssignedClasses(
            @Param("trainerId") UUID trainerId,
            @Param("status") String status,
            @Param("keyword") String keyword,
            Pageable pageable);

    @Query(value = """
            SELECT
                class_offering.id AS "id",
                class_offering.course_id AS "courseId",
                course.title AS "courseTitle",
                class_offering.class_name AS "className",
                class_offering.trainer_id AS "trainerId",
                trainer.full_name AS "trainerName",
                class_offering.schedule_description AS "scheduleDescription",
                class_offering.price AS "price",
                class_offering.start_date AS "startDate",
                class_offering.end_date AS "endDate",
                class_offering.max_students AS "maxStudents",
                COUNT(class_enrollment.id) FILTER (
                    WHERE class_enrollment.status = 'active'::public.enroll_status
                ) AS "activeEnrollmentCount",
                class_offering.status::text AS "status",
                class_offering.created_at AS "createdAt",
                class_offering.updated_at AS "updatedAt"
            FROM public.classes class_offering
            JOIN public.courses course ON course.id = class_offering.course_id
            LEFT JOIN public.users trainer ON trainer.id = class_offering.trainer_id
            LEFT JOIN public.class_enrollments class_enrollment
                ON class_enrollment.class_id = class_offering.id
            WHERE class_offering.deleted_at IS NULL
              AND class_offering.id = :classId
              AND class_offering.trainer_id = :trainerId
            GROUP BY class_offering.id, course.title, trainer.full_name
            """, nativeQuery = true)
    Optional<ClassAdminProjection> findTrainerAssignedClassDetail(
            @Param("classId") UUID classId,
            @Param("trainerId") UUID trainerId);

    @Query(value = """
            SELECT
                e.enumlabel AS "value",
                INITCAP(REPLACE(e.enumlabel, '_', ' ')) AS "label"
            FROM pg_type t
            JOIN pg_enum e ON t.oid = e.enumtypid
            JOIN pg_namespace n ON n.oid = t.typnamespace
            WHERE n.nspname = 'public'
              AND t.typname = 'class_status'
            ORDER BY e.enumsortorder
            """, nativeQuery = true)
    List<ClassStatusOptionProjection> findClassStatusOptions();

    @Query(value = """
            SELECT
                class_offering.id AS "id",
                class_offering.course_id AS "courseId",
                course.title AS "courseTitle",
                class_offering.class_name AS "className",
                class_offering.trainer_id AS "trainerId",
                trainer.full_name AS "trainerName",
                class_offering.schedule_description AS "scheduleDescription",
                class_offering.start_date AS "startDate",
                class_offering.end_date AS "endDate",
                class_offering.max_students AS "maxStudents",
                class_offering.price AS "price",
                COUNT(class_enrollment.id) FILTER (
                    WHERE class_enrollment.status = 'active'::public.enroll_status
                ) AS "activeEnrollmentCount",
                class_offering.status::text AS "status",
                class_offering.created_at AS "createdAt",
                class_offering.updated_at AS "updatedAt"
            FROM public.classes class_offering
            JOIN public.courses course ON course.id = class_offering.course_id
            LEFT JOIN public.users trainer ON trainer.id = class_offering.trainer_id
            LEFT JOIN public.class_enrollments class_enrollment
                ON class_enrollment.class_id = class_offering.id
            WHERE class_offering.deleted_at IS NULL
              AND (:courseId IS NULL OR class_offering.course_id = :courseId)
              AND (:trainerId IS NULL OR class_offering.trainer_id = :trainerId)
              AND class_offering.status IN (
                  'upcoming'::public.class_status,
                  'ongoing'::public.class_status
              )
            GROUP BY class_offering.id, course.title, trainer.full_name
            ORDER BY class_offering.start_date ASC NULLS LAST,
                     class_offering.created_at DESC
            """, nativeQuery = true)
    List<ClassAdminProjection> findAssignableClasses(
            @Param("courseId") UUID courseId,
            @Param("trainerId") UUID trainerId);
}
