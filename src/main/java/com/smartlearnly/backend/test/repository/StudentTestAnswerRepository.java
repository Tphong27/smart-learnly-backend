
package com.smartlearnly.backend.test.repository;

import com.smartlearnly.backend.test.entity.StudentTestAnswer;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudentTestAnswerRepository
        extends JpaRepository<StudentTestAnswer, UUID> {

    List<StudentTestAnswer>
    findByAttemptId(UUID attemptId);

    Optional<StudentTestAnswer> findByAttemptIdAndQuestionId(
            UUID attemptId,
            UUID questionId);

    @Modifying
    @Query("delete from StudentTestAnswer answer where answer.attemptId in :attemptIds")
    void deleteByAttemptIds(@Param("attemptIds") List<UUID> attemptIds);
}

