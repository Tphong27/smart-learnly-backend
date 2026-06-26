package com.smartlearnly.backend.question.repository;

import com.smartlearnly.backend.question.entity.QuestionAnswer;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionAnswerRepository extends JpaRepository<QuestionAnswer, UUID> {

    List<QuestionAnswer> findByQuestionId(UUID questionId);

    List<QuestionAnswer> findByQuestionIdOrderByOrderIndexAsc(UUID questionId);

    long countByQuestionId(UUID questionId);

    void deleteByQuestionId(UUID questionId);
}
