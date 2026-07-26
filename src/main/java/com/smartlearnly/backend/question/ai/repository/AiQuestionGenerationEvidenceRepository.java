package com.smartlearnly.backend.question.ai.repository;

import com.smartlearnly.backend.question.ai.entity.AiQuestionGenerationEvidence;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiQuestionGenerationEvidenceRepository extends JpaRepository<AiQuestionGenerationEvidence, UUID> {
    List<AiQuestionGenerationEvidence> findByDraftId(UUID draftId);

    List<AiQuestionGenerationEvidence> findByDraftIdIn(List<UUID> draftIds);
}
