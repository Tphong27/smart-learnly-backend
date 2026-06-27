package com.smartlearnly.backend.flashcard.repository;

import com.smartlearnly.backend.flashcard.entity.FlashcardCard;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FlashcardCardRepository extends JpaRepository<FlashcardCard, UUID> {
    Optional<FlashcardCard> findByIdAndDeletedAtIsNull(UUID id);

    @Query("""
            select card
            from FlashcardCard card
            where card.flashcardSet.id = :setId
              and card.deletedAt is null
            order by card.orderIndex asc, card.createdAt asc
            """)
    List<FlashcardCard> findActiveBySetIdOrderByOrderIndex(@Param("setId") UUID setId);

    @Query("""
            select coalesce(max(card.orderIndex), -1)
            from FlashcardCard card
            where card.flashcardSet.id = :setId
              and card.deletedAt is null
            """)
    int findMaxOrderIndexBySetId(@Param("setId") UUID setId);
}
