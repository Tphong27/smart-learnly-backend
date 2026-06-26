package com.smartlearnly.backend.question.repository;

import com.smartlearnly.backend.question.entity.QuestionBank;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionBankRepository extends JpaRepository<QuestionBank, UUID> {

    List<QuestionBank> findByCourseIdAndStatusOrderByUpdatedAtDesc(UUID courseId, String status);

    List<QuestionBank> findByCourseIdOrderByUpdatedAtDesc(UUID courseId);

    List<QuestionBank> findByStatusOrderByUpdatedAtDesc(String status);

    long countByIdAndCourseId(UUID bankId, UUID courseId);
}
