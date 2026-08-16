package com.smartlearnly.backend.question.repository;

import com.smartlearnly.backend.question.entity.QuestionAnswer;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuestionAnswerRepository extends JpaRepository<QuestionAnswer, UUID> {

    List<QuestionAnswer> findByQuestionId(UUID questionId);

    List<QuestionAnswer> findByQuestionIdOrderByOrderIndexAsc(UUID questionId);

    List<QuestionAnswer> findByQuestionIdInOrderByQuestionIdAscOrderIndexAsc(List<UUID> questionIds);

    long countByQuestionId(UUID questionId);

    @Query(value = """
            SELECT EXISTS (
                SELECT 1
                FROM public.student_test_answers
                WHERE selected_answer_id = :answerId
            )
            """, nativeQuery = true)
    boolean existsStudentSelectionById(@Param("answerId") UUID answerId);

    void deleteByQuestionId(UUID questionId);
}
