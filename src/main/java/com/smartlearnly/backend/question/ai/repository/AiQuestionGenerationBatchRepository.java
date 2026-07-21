package com.smartlearnly.backend.question.ai.repository;

import com.smartlearnly.backend.question.ai.entity.AiQuestionGenerationBatch;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiQuestionGenerationBatchRepository extends JpaRepository<AiQuestionGenerationBatch, UUID> {
    Optional<AiQuestionGenerationBatch> findByRequestedByAndIdempotencyKey(UUID requestedBy, String idempotencyKey);

    List<AiQuestionGenerationBatch> findByQuestionBankIdOrderByCreatedAtDesc(UUID questionBankId);

    long countByRequestedByAndCreatedAtAfter(UUID requestedBy, Instant createdAtAfter);

    boolean existsByRequestedByAndQuestionBankIdAndStatus(UUID requestedBy, UUID questionBankId, String status);
}
