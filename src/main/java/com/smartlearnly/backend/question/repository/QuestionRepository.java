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
            WHERE (CAST(:bankId AS uuid) IS NULL OR q.question_bank_id = CAST(:bankId AS uuid))
              AND (
                    CAST(:courseId AS uuid) IS NULL
                    OR q.course_id = CAST(:courseId AS uuid)
                    OR q.question_bank_id IN (
                        SELECT b.id
                        FROM public.question_banks b
                        WHERE b.course_id = CAST(:courseId AS uuid)
                    )
                  )
              AND (CAST(:moduleId AS uuid) IS NULL OR q.module_id = CAST(:moduleId AS uuid))
              AND (CAST(:search AS text) IS NULL OR LOWER(q.question_text) LIKE CONCAT('%', LOWER(CAST(:search AS text)), '%'))
              AND (CAST(:type AS text) IS NULL OR q.question_type::text = CAST(:type AS text))
              AND (CAST(:status AS text) IS NULL OR q.status::text = CAST(:status AS text))
              AND (CAST(:difficulty AS smallint) IS NULL OR q.difficulty = CAST(:difficulty AS smallint))
            ORDER BY q.updated_at DESC
            """,
            countQuery = """
            SELECT COUNT(*)
            FROM public.questions q
            WHERE (CAST(:bankId AS uuid) IS NULL OR q.question_bank_id = CAST(:bankId AS uuid))
              AND (
                    CAST(:courseId AS uuid) IS NULL
                    OR q.course_id = CAST(:courseId AS uuid)
                    OR q.question_bank_id IN (
                        SELECT b.id
                        FROM public.question_banks b
                        WHERE b.course_id = CAST(:courseId AS uuid)
                    )
                  )
              AND (CAST(:moduleId AS uuid) IS NULL OR q.module_id = CAST(:moduleId AS uuid))
              AND (CAST(:search AS text) IS NULL OR LOWER(q.question_text) LIKE CONCAT('%', LOWER(CAST(:search AS text)), '%'))
              AND (CAST(:type AS text) IS NULL OR q.question_type::text = CAST(:type AS text))
              AND (CAST(:status AS text) IS NULL OR q.status::text = CAST(:status AS text))
              AND (CAST(:difficulty AS smallint) IS NULL OR q.difficulty = CAST(:difficulty AS smallint))
            """,
            nativeQuery = true)
    Page<Question> searchForAdmin(
            @Param("bankId") UUID bankId,
            @Param("courseId") UUID courseId,
            @Param("moduleId") UUID moduleId,
            @Param("search") String search,
            @Param("type") String type,
            @Param("status") String status,
            @Param("difficulty") Short difficulty,
            Pageable pageable
    );

    List<Question> findByCourseId(UUID courseId);

    List<Question> findByQuestionBankId(UUID questionBankId);

    long countByQuestionBankId(UUID questionBankId);

    long countByQuestionBankIdAndQuestionTextIgnoreCase(UUID questionBankId, String questionText);

    boolean existsByQuestionBankIdAndQuestionTextIgnoreCase(UUID questionBankId, String questionText);
}
