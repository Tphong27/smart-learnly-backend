
package com.smartlearnly.backend.question.repository;

import com.smartlearnly.backend.question.entity.Question;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionRepository
        extends JpaRepository<Question, UUID> {

    List<Question> findByCourseId(UUID courseId);

    List<Question> findByQuestionBankId(UUID questionBankId);
}

