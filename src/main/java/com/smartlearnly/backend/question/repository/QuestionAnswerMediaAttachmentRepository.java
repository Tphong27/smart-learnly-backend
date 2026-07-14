package com.smartlearnly.backend.question.repository;

import com.smartlearnly.backend.question.entity.QuestionAnswerMediaAttachment;
import com.smartlearnly.backend.question.entity.QuestionMediaType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionAnswerMediaAttachmentRepository
        extends JpaRepository<QuestionAnswerMediaAttachment, UUID> {

    List<QuestionAnswerMediaAttachment> findByAnswerIdOrderByMediaTypeAscDisplayOrderAsc(UUID answerId);

    List<QuestionAnswerMediaAttachment> findByAnswerIdAndMediaTypeOrderByDisplayOrderAsc(
            UUID answerId, QuestionMediaType mediaType);

    Optional<QuestionAnswerMediaAttachment> findByAnswerIdAndMediaType(
            UUID answerId, QuestionMediaType mediaType);

    Optional<QuestionAnswerMediaAttachment> findByAnswerIdAndId(UUID answerId, UUID id);

    long countByAnswerIdAndMediaType(UUID answerId, QuestionMediaType mediaType);

    void deleteByAnswerId(UUID answerId);

    List<QuestionAnswerMediaAttachment> findByAnswerIdIn(List<UUID> answerIds);
}