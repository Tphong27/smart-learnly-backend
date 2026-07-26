package com.smartlearnly.backend.question.ai.repository;

import com.smartlearnly.backend.question.ai.entity.AiQuestionGenerationSourceChunk;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiQuestionGenerationSourceChunkRepository extends JpaRepository<AiQuestionGenerationSourceChunk, UUID> {
    List<AiQuestionGenerationSourceChunk> findByGenerationSourceIdOrderByChunkIndexAsc(UUID generationSourceId);
}
