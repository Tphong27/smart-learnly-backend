package com.smartlearnly.backend.question.repository;

import com.smartlearnly.backend.question.entity.QuestionBank;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuestionBankRepository extends JpaRepository<QuestionBank, UUID> {

    List<QuestionBank> findByCourseIdAndStatusOrderByUpdatedAtDesc(UUID courseId, String status);

    List<QuestionBank> findByCourseIdOrderByUpdatedAtDesc(UUID courseId);

    List<QuestionBank> findByStatusOrderByUpdatedAtDesc(String status);

    long countByIdAndCourseId(UUID bankId, UUID courseId);

    @Query(value = """
            SELECT b.*
            FROM public.question_banks b
            WHERE (CAST(:courseId AS uuid) IS NULL OR b.course_id = CAST(:courseId AS uuid))
              AND (CAST(:status AS text) IS NULL OR b.status = CAST(:status AS text))
              AND (
                    CAST(:search AS text) IS NULL
                    OR LOWER(COALESCE(b.name, '')) LIKE CONCAT('%', LOWER(CAST(:search AS text)), '%')
                    OR LOWER(COALESCE(b.description, '')) LIKE CONCAT('%', LOWER(CAST(:search AS text)), '%')
                  )
            ORDER BY b.updated_at DESC
            """,
            countQuery = """
            SELECT COUNT(*)
            FROM public.question_banks b
            WHERE (CAST(:courseId AS uuid) IS NULL OR b.course_id = CAST(:courseId AS uuid))
              AND (CAST(:status AS text) IS NULL OR b.status = CAST(:status AS text))
              AND (
                    CAST(:search AS text) IS NULL
                    OR LOWER(COALESCE(b.name, '')) LIKE CONCAT('%', LOWER(CAST(:search AS text)), '%')
                    OR LOWER(COALESCE(b.description, '')) LIKE CONCAT('%', LOWER(CAST(:search AS text)), '%')
                  )
            """,
            nativeQuery = true)
    Page<QuestionBank> searchForAdmin(
            @Param("courseId") UUID courseId,
            @Param("status") String status,
            @Param("search") String search,
            Pageable pageable
    );
}
