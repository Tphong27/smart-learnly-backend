package com.smartlearnly.backend.flashcard.staging.repository;

import com.smartlearnly.backend.flashcard.staging.entity.FlashcardStagingCard;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FlashcardStagingCardRepository extends JpaRepository<FlashcardStagingCard, UUID> {
    List<FlashcardStagingCard> findByBatchIdInOrderBySortOrderAscCreatedAtAsc(Collection<UUID> batchIds);

    List<FlashcardStagingCard> findByIdIn(Collection<UUID> ids);

    long countByBatchIdAndStatus(UUID batchId, String status);

    @Query("""
            select card
            from FlashcardStagingCard card
            where card.batch.flashcardSet.id = :setId
              and card.status = 'draft'
            order by card.batch.createdAt desc, card.sortOrder asc, card.createdAt asc
            """)
    List<FlashcardStagingCard> findDraftBySetId(@Param("setId") UUID setId);
}
