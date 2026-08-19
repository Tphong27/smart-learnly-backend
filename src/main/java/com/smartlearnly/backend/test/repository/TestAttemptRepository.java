
package com.smartlearnly.backend.test.repository;

import com.smartlearnly.backend.test.entity.TestAttempt;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TestAttemptRepository
        extends JpaRepository<TestAttempt, UUID> {

    List<TestAttempt> findByTestIdAndStudentId(
            UUID testId,
            UUID studentId);

    List<TestAttempt> findByTestId(UUID testId);

    boolean existsByTestId(UUID testId);

    List<TestAttempt> findByTestIdOrderByStartTimeAsc(UUID testId);

    List<TestAttempt> findByTestIdAndStudentIdAndClassIdIsNullOrderByStartTimeDesc(
            UUID testId,
            UUID studentId);

    List<TestAttempt> findByTestIdAndStudentIdAndClassIdOrderByStartTimeDesc(
            UUID testId,
            UUID studentId,
            UUID classId);

    @Query(value = """
            SELECT EXISTS (
                SELECT 1
                FROM public.test_attempts
                WHERE test_id = :testId
                  AND status = 'doing'::public.attempt_status
                  AND (end_time IS NULL OR end_time > CURRENT_TIMESTAMP)
            )
            """, nativeQuery = true)
    boolean existsActiveByTestId(@Param("testId") UUID testId);

    /** Lấy các attempt đang làm và chưa hết thời gian của một test. */
    @Query(value = """
            SELECT *
            FROM public.test_attempts
            WHERE test_id = :testId
              AND status = 'doing'::public.attempt_status
              AND (end_time IS NULL OR end_time > CURRENT_TIMESTAMP)
            """, nativeQuery = true)
    List<TestAttempt> findActiveByTestId(@Param("testId") UUID testId);
}

