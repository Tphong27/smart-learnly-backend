package com.smartlearnly.backend.flashcard.staging.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.flashcard.staging.dto.AdminFlashcardStagingDtos.UpdateStagingCardRequest;
import com.smartlearnly.backend.flashcard.staging.entity.FlashcardStagingCard;
import com.smartlearnly.backend.flashcard.staging.repository.FlashcardStagingCardRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FlashcardStagingCardEditServiceTest {
    @Mock
    private FlashcardStagingCardRepository stagingCardRepository;
    @Mock
    private AdminFlashcardStagingService adminFlashcardStagingService;

    private FlashcardStagingCardEditService editService;

    @BeforeEach
    void setUp() {
        editService = new FlashcardStagingCardEditService(stagingCardRepository, adminFlashcardStagingService);
    }

    @Test
    void updateCardShouldRejectMissingFrontTextEvenWithImage() {
        UUID cardId = UUID.randomUUID();
        FlashcardStagingCard card = draftCard();
        when(stagingCardRepository.findById(cardId)).thenReturn(Optional.of(card));

        assertThatThrownBy(() -> editService.updateCard(
                cardId,
                new UpdateStagingCardRequest(" ", "Back", "https://cdn.test/front.png", null, null, null, null)
        ))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
        verify(adminFlashcardStagingService, never()).updateCard(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void updateCardShouldRejectMissingBackTextEvenWithImage() {
        UUID cardId = UUID.randomUUID();
        FlashcardStagingCard card = draftCard();
        when(stagingCardRepository.findById(cardId)).thenReturn(Optional.of(card));

        assertThatThrownBy(() -> editService.updateCard(
                cardId,
                new UpdateStagingCardRequest("Front", " ", null, "https://cdn.test/back.png", null, null, null)
        ))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
        verify(adminFlashcardStagingService, never()).updateCard(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void updateCardShouldDelegateWithoutSortOrderWhenStrictTextIsSatisfied() {
        UUID cardId = UUID.randomUUID();
        FlashcardStagingCard card = draftCard();
        when(stagingCardRepository.findById(cardId)).thenReturn(Optional.of(card));

        editService.updateCard(
                cardId,
                new UpdateStagingCardRequest("Front", "Back", null, null, "Hint", null, 99)
        );

        ArgumentCaptor<UpdateStagingCardRequest> requestCaptor = ArgumentCaptor.forClass(UpdateStagingCardRequest.class);
        verify(adminFlashcardStagingService).updateCard(org.mockito.ArgumentMatchers.eq(cardId), requestCaptor.capture());
        assertThat(requestCaptor.getValue().sortOrder()).isNull();
    }

    private FlashcardStagingCard draftCard() {
        FlashcardStagingCard card = new FlashcardStagingCard();
        card.setFrontText("Existing front");
        card.setBackText("Existing back");
        card.setStatus("draft");
        return card;
    }
}
