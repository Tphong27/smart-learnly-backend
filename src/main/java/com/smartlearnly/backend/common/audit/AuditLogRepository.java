package com.smartlearnly.backend.common.audit;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository
        extends JpaRepository<AuditLog, UUID>,
        JpaSpecificationExecutor<AuditLog> {

    @Query(value = """
                        SELECT al.*
                        FROM public.audit_logs al
                        WHERE (
                            (
                                UPPER(al.target_type) = 'COURSE'
                                AND al.target_id = :courseId
                            )
                            OR (
                                UPPER(al.target_type) = 'CURRICULUM_SECTION'
                                AND EXISTS (
                                    SELECT 1
                                    FROM public.curriculum_sections cs
                                    JOIN public.curriculum_versions cv
                                      ON cv.id = cs.curriculum_version_id
                                    WHERE cs.id::text = al.target_id
                                      AND cv.course_id::text = :courseId
                                )
                            )
                            OR (
                                UPPER(al.target_type) = 'CURRICULUM_LESSON'
                                AND EXISTS (
                                    SELECT 1
                                    FROM public.curriculum_lessons cl
                                    JOIN public.curriculum_versions cv
                                      ON cv.id = cl.curriculum_version_id
                                    WHERE cl.id::text = al.target_id
                                      AND cv.course_id::text = :courseId
                                )
                            )
                        )
                        AND (
                            :keyword IS NULL
                            OR :keyword = ''
                            OR LOWER(COALESCE(al.actor_email, ''))
                                LIKE LOWER(CONCAT('%', :keyword, '%'))
                            OR LOWER(COALESCE(al.summary, ''))
                                LIKE LOWER(CONCAT('%', :keyword, '%'))
                            OR LOWER(COALESCE(al.target_id, ''))
                                LIKE LOWER(CONCAT('%', :keyword, '%'))
                            OR LOWER(COALESCE(al.action, ''))
                                LIKE LOWER(CONCAT('%', :keyword, '%'))
                        )
                        AND (
                            :action IS NULL
                            OR :action = ''
                            OR UPPER(al.action) = UPPER(:action)
                        )
                        AND (
                            :actorRole IS NULL
                            OR :actorRole = ''
                            OR UPPER(COALESCE(al.actor_role, ''))
                                = UPPER(:actorRole)
                        )
            AND (
                CAST(:fromTime AS timestamptz) IS NULL
                OR al.occurred_at >= CAST(:fromTime AS timestamptz)
            )
            AND (
                CAST(:toTime AS timestamptz) IS NULL
                OR al.occurred_at <= CAST(:toTime AS timestamptz)
            )
                        ORDER BY al.occurred_at DESC, al.id DESC
                        """, countQuery = """
                        SELECT COUNT(*)
                        FROM public.audit_logs al
                        WHERE (
                            (
                                UPPER(al.target_type) = 'COURSE'
                                AND al.target_id = :courseId
                            )
                            OR (
                                UPPER(al.target_type) = 'CURRICULUM_SECTION'
                                AND EXISTS (
                                    SELECT 1
                                    FROM public.curriculum_sections cs
                                    JOIN public.curriculum_versions cv
                                      ON cv.id = cs.curriculum_version_id
                                    WHERE cs.id::text = al.target_id
                                      AND cv.course_id::text = :courseId
                                )
                            )
                            OR (
                                UPPER(al.target_type) = 'CURRICULUM_LESSON'
                                AND EXISTS (
                                    SELECT 1
                                    FROM public.curriculum_lessons cl
                                    JOIN public.curriculum_versions cv
                                      ON cv.id = cl.curriculum_version_id
                                    WHERE cl.id::text = al.target_id
                                      AND cv.course_id::text = :courseId
                                )
                            )
                        )
                        AND (
                            :keyword IS NULL
                            OR :keyword = ''
                            OR LOWER(COALESCE(al.actor_email, ''))
                                LIKE LOWER(CONCAT('%', :keyword, '%'))
                            OR LOWER(COALESCE(al.summary, ''))
                                LIKE LOWER(CONCAT('%', :keyword, '%'))
                            OR LOWER(COALESCE(al.target_id, ''))
                                LIKE LOWER(CONCAT('%', :keyword, '%'))
                            OR LOWER(COALESCE(al.action, ''))
                                LIKE LOWER(CONCAT('%', :keyword, '%'))
                        )
                        AND (
                            :action IS NULL
                            OR :action = ''
                            OR UPPER(al.action) = UPPER(:action)
                        )
                        AND (
                            :actorRole IS NULL
                            OR :actorRole = ''
                            OR UPPER(COALESCE(al.actor_role, ''))
                                = UPPER(:actorRole)
                        )
                        AND (
                CAST(:fromTime AS timestamptz) IS NULL
                OR al.occurred_at >= CAST(:fromTime AS timestamptz)
            )
            AND (
                CAST(:toTime AS timestamptz) IS NULL
                OR al.occurred_at <= CAST(:toTime AS timestamptz)
            )
                        """, nativeQuery = true)
    Page<AuditLog> findCourseChangeHistory(
            @Param("courseId") String courseId,
            @Param("keyword") String keyword,
            @Param("action") String action,
            @Param("actorRole") String actorRole,
            @Param("fromTime") Instant fromTime,
            @Param("toTime") Instant toTime,
            Pageable pageable);

    @Query(value = """
            SELECT al.*
            FROM public.audit_logs al
            WHERE al.id = :auditLogId
              AND (
                  (
                      UPPER(al.target_type) = 'COURSE'
                      AND al.target_id = :courseId
                  )
                  OR (
                      UPPER(al.target_type) = 'CURRICULUM_SECTION'
                      AND EXISTS (
                          SELECT 1
                          FROM public.curriculum_sections cs
                          JOIN public.curriculum_versions cv
                            ON cv.id = cs.curriculum_version_id
                          WHERE cs.id::text = al.target_id
                            AND cv.course_id::text = :courseId
                      )
                  )
                  OR (
                      UPPER(al.target_type) = 'CURRICULUM_LESSON'
                      AND EXISTS (
                          SELECT 1
                          FROM public.curriculum_lessons cl
                          JOIN public.curriculum_versions cv
                            ON cv.id = cl.curriculum_version_id
                          WHERE cl.id::text = al.target_id
                            AND cv.course_id::text = :courseId
                      )
                  )
              )
            """, nativeQuery = true)
    Optional<AuditLog> findCourseChangeHistoryDetail(
            @Param("courseId") String courseId,
            @Param("auditLogId") UUID auditLogId);
}