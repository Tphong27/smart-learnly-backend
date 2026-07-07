package com.smartlearnly.backend.question.repository;

import com.smartlearnly.backend.question.entity.QuestionMediaAttachment;
import com.smartlearnly.backend.question.entity.QuestionMediaType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionMediaAttachmentRepository extends JpaRepository<QuestionMediaAttachment, UUID> {
    List<QuestionMediaAttachment> findByQuestionIdOrderByMediaTypeAscDisplayOrderAsc(UUID questionId);

    List<QuestionMediaAttachment> findByQuestionIdAndMediaTypeOrderByDisplayOrderAsc(UUID questionId, QuestionMediaType mediaType);

    Optional<QuestionMediaAttachment> findFirstByQuestionIdAndMediaTypeOrderByDisplayOrderAsc(UUID questionId, QuestionMediaType mediaType);

    Optional<QuestionMediaAttachment> findByQuestionIdAndId(UUID questionId, UUID id);

    long countByQuestionIdAndMediaType(UUID questionId, QuestionMediaType mediaType);
}
