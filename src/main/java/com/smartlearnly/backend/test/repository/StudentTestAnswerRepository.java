
package com.smartlearnly.backend.test.repository;

import com.smartlearnly.backend.test.entity.StudentTestAnswer;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentTestAnswerRepository
        extends JpaRepository<StudentTestAnswer, UUID> {

    List<StudentTestAnswer>
    findByAttemptId(UUID attemptId);
}

