package com.smartlearnly.backend.question.ai.repository;

import com.smartlearnly.backend.question.ai.entity.AiQuestionGenerationSource;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiQuestionGenerationSourceRepository extends JpaRepository<AiQuestionGenerationSource, UUID> {
    List<AiQuestionGenerationSource> findByBatchId(UUID batchId);

    Optional<AiQuestionGenerationSource> findFirstByBatchId(UUID batchId);
}
