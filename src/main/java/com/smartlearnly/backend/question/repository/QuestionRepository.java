package com.smartlearnly.backend.question.repository;

import com.smartlearnly.backend.question.entity.Question;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuestionRepository extends JpaRepository<Question, UUID>, JpaSpecificationExecutor<Question> {
    @Query(value = """
            SELECT q.*
            FROM public.questions q
            WHERE q.course_id = :courseId
              AND (CAST(:moduleId AS uuid) IS NULL OR q.module_id = CAST(:moduleId AS uuid))
              AND (CAST(:search AS text) IS NULL OR LOWER(q.question_text) LIKE CONCAT('%', LOWER(CAST(:search AS text)), '%'))
              AND (CAST(:type AS text) IS NULL OR q.question_type = CAST(:type AS public.question_type))
              AND (CAST(:status AS text) IS NULL OR q.status = CAST(:status AS public.question_status))
              AND (CAST(:includeArchived AS boolean) = TRUE OR q.status <> 'archived'::public.question_status)
              AND (CAST(:difficulty AS smallint) IS NULL OR q.difficulty = CAST(:difficulty AS smallint))
            ORDER BY q.updated_at DESC, q.id ASC
            """,
            countQuery = """
            SELECT COUNT(*)
            FROM public.questions q
            WHERE q.course_id = :courseId
              AND (CAST(:moduleId AS uuid) IS NULL OR q.module_id = CAST(:moduleId AS uuid))
              AND (CAST(:search AS text) IS NULL OR LOWER(q.question_text) LIKE CONCAT('%', LOWER(CAST(:search AS text)), '%'))
              AND (CAST(:type AS text) IS NULL OR q.question_type = CAST(:type AS public.question_type))
              AND (CAST(:status AS text) IS NULL OR q.status = CAST(:status AS public.question_status))
              AND (CAST(:includeArchived AS boolean) = TRUE OR q.status <> 'archived'::public.question_status)
              AND (CAST(:difficulty AS smallint) IS NULL OR q.difficulty = CAST(:difficulty AS smallint))
            """,
            nativeQuery = true)
    Page<Question> searchForAdmin(
            @Param("courseId") UUID courseId,
            @Param("moduleId") UUID moduleId,
            @Param("search") String search,
            @Param("type") String type,
            @Param("status") String status,
            @Param("includeArchived") boolean includeArchived,
            @Param("difficulty") Short difficulty,
            Pageable pageable
    );

    List<Question> findByCourseId(UUID courseId);

    /** Tìm ứng viên gần trùng bằng Jaccard token trong PostgreSQL và chỉ trả số lượng nhỏ nhất cần thiết. */
    @Query(value = """
            WITH input AS (
                SELECT public.smartlearnly_normalized_terms(CAST(:questionText AS text)) AS terms
            )
            SELECT q.*
            FROM public.questions q
            CROSS JOIN input
            WHERE q.course_id = :courseId
              AND public.smartlearnly_normalized_terms(q.question_text) && input.terms
              AND public.smartlearnly_token_jaccard(q.question_text, CAST(:questionText AS text)) >= :threshold
            ORDER BY public.smartlearnly_token_jaccard(q.question_text, CAST(:questionText AS text)) DESC,
                     q.updated_at DESC,
                     q.id ASC
            LIMIT :candidateLimit
            """, nativeQuery = true)
    List<Question> findNearDuplicateCandidatesInCourse(
            @Param("courseId") UUID courseId,
            @Param("questionText") String questionText,
            @Param("threshold") double threshold,
            @Param("candidateLimit") int candidateLimit);

    @Query(value = """
            SELECT EXISTS (
                SELECT 1
                FROM public.questions q
                WHERE q.course_id = :courseId
                  AND LOWER(q.question_text) = LOWER(:questionText)
                  AND q.status::text <> 'archived'
                  AND (CAST(:excludedQuestionId AS uuid) IS NULL OR q.id <> CAST(:excludedQuestionId AS uuid))
            )
            """, nativeQuery = true)
    boolean existsActiveDuplicateInCourse(
            @Param("courseId") UUID courseId,
            @Param("questionText") String questionText,
            @Param("excludedQuestionId") UUID excludedQuestionId
    );

    @Query(value = """
            SELECT q.*
            FROM public.questions q
            WHERE q.course_id = :courseId
              AND LOWER(q.question_text) = LOWER(:questionText)
            ORDER BY CASE WHEN q.status::text = 'archived' THEN 1 ELSE 0 END, q.updated_at DESC
            LIMIT 3
            """, nativeQuery = true)
    List<Question> findExactDuplicateCandidatesInCourse(
            @Param("courseId") UUID courseId,
            @Param("questionText") String questionText
    );

}
