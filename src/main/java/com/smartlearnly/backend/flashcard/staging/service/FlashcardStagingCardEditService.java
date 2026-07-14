package com.smartlearnly.backend.flashcard.staging.service;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.flashcard.staging.dto.AdminFlashcardStagingDtos.StagingCardResponse;
import com.smartlearnly.backend.flashcard.staging.dto.AdminFlashcardStagingDtos.UpdateStagingCardRequest;
import com.smartlearnly.backend.flashcard.staging.entity.FlashcardStagingCard;
import com.smartlearnly.backend.flashcard.staging.repository.FlashcardStagingCardRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FlashcardStagingCardEditService {
    private final FlashcardStagingCardRepository stagingCardRepository;
    private final AdminFlashcardStagingService adminFlashcardStagingService;

    @Transactional
    public StagingCardResponse updateCard(UUID stagingCardId, UpdateStagingCardRequest request) {
        FlashcardStagingCard card = stagingCardRepository.findById(stagingCardId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Flashcard staging card was not found"));
        if (!"draft".equalsIgnoreCase(card.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Only draft staging cards can be edited");
        }

        String frontText = request.frontText() == null ? card.getFrontText() : normalizeNullable(request.frontText());
        String backText = request.backText() == null ? card.getBackText() : normalizeNullable(request.backText());
        if (!hasText(frontText) || !hasText(backText)) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Staging flashcards require both frontText and backText"
            );
        }

        UpdateStagingCardRequest sanitizedRequest = new UpdateStagingCardRequest(
                request.frontText(),
                request.backText(),
                request.frontImageUrl(),
                request.backImageUrl(),
                request.hint(),
                request.explanation(),
                null
        );
        return adminFlashcardStagingService.updateCard(stagingCardId, sanitizedRequest);
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
