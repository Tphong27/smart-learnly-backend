
package com.smartlearnly.backend.test.repository;

import com.smartlearnly.backend.test.entity.TestAttempt;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import com.smartlearnly.backend.test.entity.AttemptStatus;

public interface TestAttemptRepository
        extends JpaRepository<TestAttempt, UUID> {

    List<TestAttempt> findByTestIdAndStudentId(
            UUID testId,
            UUID studentId);

    List<TestAttempt> findByTestId(UUID testId);

    boolean existsByTestIdAndStatusIn(UUID testId, List<AttemptStatus> statuses);
}

