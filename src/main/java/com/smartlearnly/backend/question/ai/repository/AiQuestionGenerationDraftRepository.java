package com.smartlearnly.backend.question.ai.repository;

import com.smartlearnly.backend.question.ai.entity.AiQuestionGenerationDraft;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiQuestionGenerationDraftRepository extends JpaRepository<AiQuestionGenerationDraft, UUID> {
    List<AiQuestionGenerationDraft> findByBatchIdOrderByCreatedAtAsc(UUID batchId);
}
