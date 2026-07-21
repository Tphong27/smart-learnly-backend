package com.smartlearnly.backend.question.ai.repository;

import com.smartlearnly.backend.question.ai.entity.AiQuestionGenerationDraftRevision;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface AiQuestionGenerationDraftRevisionRepository extends JpaRepository<AiQuestionGenerationDraftRevision, UUID> {
}
