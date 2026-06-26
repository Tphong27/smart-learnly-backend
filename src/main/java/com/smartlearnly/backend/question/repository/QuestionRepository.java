package com.smartlearnly.backend.question.repository;

import com.smartlearnly.backend.question.entity.Question;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface QuestionRepository extends JpaRepository<Question, UUID>, JpaSpecificationExecutor<Question> {

    List<Question> findByCourseId(UUID courseId);

    List<Question> findByQuestionBankId(UUID questionBankId);

    long countByQuestionBankId(UUID questionBankId);
}
