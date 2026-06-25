
package com.smartlearnly.backend.test.repository;

import com.smartlearnly.backend.test.entity.TestAttempt;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TestAttemptRepository
        extends JpaRepository<TestAttempt, UUID> {

    List<TestAttempt> findByTestIdAndStudentId(
            UUID testId,
            UUID studentId);
}

