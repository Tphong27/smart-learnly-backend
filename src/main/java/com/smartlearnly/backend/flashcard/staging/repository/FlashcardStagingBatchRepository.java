package com.smartlearnly.backend.flashcard.staging.repository;

import com.smartlearnly.backend.flashcard.staging.entity.FlashcardStagingBatch;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FlashcardStagingBatchRepository extends JpaRepository<FlashcardStagingBatch, UUID> {
    List<FlashcardStagingBatch> findByFlashcardSetIdAndStatusOrderByCreatedAtDesc(UUID flashcardSetId, String status);
}
