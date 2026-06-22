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

public interface ClassOfferingRepository extends JpaRepository<ClassOffering, UUID> {
    Optional<ClassOffering> findByIdAndDeletedAtIsNull(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select classOffering from ClassOffering classOffering "
            + "where classOffering.id = :id and classOffering.deletedAt is null")
    Optional<ClassOffering> findByIdForUpdate(@Param("id") UUID id);

    @Query(
            value = """
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
                      AND (:status IS NULL OR class_offering.status::text = :status)
                      AND (
                          :keyword IS NULL
                          OR class_offering.class_name ILIKE :keyword ESCAPE '\\'
                      )
                    GROUP BY class_offering.id, course.title, trainer.full_name
                    ORDER BY class_offering.created_at DESC, class_offering.id ASC
                    """,
            countQuery = """
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
                    """,
            nativeQuery = true)
    Page<ClassAdminProjection> findAdminClasses(
            @Param("courseId") UUID courseId,
            @Param("trainerId") UUID trainerId,
            @Param("status") String status,
            @Param("keyword") String keyword,
            Pageable pageable
    );

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
}
