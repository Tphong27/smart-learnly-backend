package com.smartlearnly.backend.flashcard.repository;

import com.smartlearnly.backend.flashcard.entity.FlashcardProgress;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FlashcardProgressRepository extends JpaRepository<FlashcardProgress, UUID> {
    @Query("""
            select progress
            from FlashcardProgress progress
            where progress.studentId = :studentId
              and progress.flashcard.id = :cardId
            """)
    Optional<FlashcardProgress> findByStudentIdAndCardId(
            @Param("studentId") UUID studentId,
            @Param("cardId") UUID cardId
    );

    @Query("""
            select progress
            from FlashcardProgress progress
            where progress.studentId = :studentId
              and progress.flashcard.id in :cardIds
            """)
    List<FlashcardProgress> findByStudentIdAndCardIds(
            @Param("studentId") UUID studentId,
            @Param("cardIds") Collection<UUID> cardIds
    );
}
