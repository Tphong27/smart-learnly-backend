
package com.smartlearnly.backend.test.repository;

import com.smartlearnly.backend.test.entity.TestQuestion;
import com.smartlearnly.backend.test.entity.TestQuestion.TestQuestionId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TestQuestionRepository
        extends JpaRepository<TestQuestion, TestQuestionId> {

    List<TestQuestion> findByIdTestId(UUID testId);
}

