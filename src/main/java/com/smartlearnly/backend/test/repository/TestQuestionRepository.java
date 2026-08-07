
package com.smartlearnly.backend.test.repository;

import com.smartlearnly.backend.test.entity.TestQuestion;
import com.smartlearnly.backend.test.entity.TestQuestion.TestQuestionId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TestQuestionRepository
        extends JpaRepository<TestQuestion, TestQuestionId> {

    /** Tải danh sách câu hỏi theo contract cũ đang được các luồng test dùng. */
    List<TestQuestion> findByIdTestId(UUID testId);

    /** Tải danh sách câu hỏi theo thứ tự do người soạn cấu hình. */
    List<TestQuestion> findByIdTestIdOrderByOrderIndexAsc(UUID testId);
}

