package com.smartlearnly.backend.flashcard.personal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.common.api.PageResponse;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.flashcard.entity.FlashcardCard;
import com.smartlearnly.backend.flashcard.entity.FlashcardSet;
import com.smartlearnly.backend.flashcard.personal.dto.PersonalFlashcardDtos.BulkDeletePersonalFlashcardCardsRequest;
import com.smartlearnly.backend.flashcard.personal.dto.PersonalFlashcardDtos.CreatePersonalFlashcardCardRequest;
import com.smartlearnly.backend.flashcard.personal.dto.PersonalFlashcardDtos.CreatePersonalFlashcardSetRequest;
import com.smartlearnly.backend.flashcard.personal.dto.PersonalFlashcardDtos.PersonalFlashcardSetDetailResponse;
import com.smartlearnly.backend.flashcard.personal.dto.PersonalFlashcardDtos.PersonalFlashcardSetSummaryResponse;
import com.smartlearnly.backend.flashcard.personal.dto.PersonalFlashcardDtos.PersonalFlashcardStudyResponse;
import com.smartlearnly.backend.flashcard.personal.dto.PersonalFlashcardDtos.ReorderPersonalFlashcardCardsRequest;
import com.smartlearnly.backend.flashcard.personal.dto.PersonalFlashcardDtos.ReplacePersonalFlashcardCardRequest;
import com.smartlearnly.backend.flashcard.repository.FlashcardCardRepository;
import com.smartlearnly.backend.flashcard.repository.FlashcardCardRepository.PersonalCardCountProjection;
import com.smartlearnly.backend.flashcard.repository.FlashcardSetRepository;
import com.smartlearnly.backend.user.entity.UserAccount;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class PersonalFlashcardServiceTest {
    @Mock
    private FlashcardSetRepository flashcardSetRepository;
    @Mock
    private FlashcardCardRepository flashcardCardRepository;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private PersonalFlashcardImageUrlPolicy imageUrlPolicy;

    private PersonalFlashcardService service;

    @BeforeEach
    void setUp() {
        service = new PersonalFlashcardService(
                flashcardSetRepository,
                flashcardCardRepository,
                currentUserService,
                imageUrlPolicy
        );
    }

    @Test
    void createSetShouldDeriveThePersonalClassificationFromTheActor() {
        UserAccount actor = user("TRAINEE");
        when(currentUserService.requireAuthenticatedUser()).thenReturn(actor);
        when(flashcardSetRepository.save(any())).thenAnswer(invocation -> {
            FlashcardSet saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            saved.setCreatedAt(Instant.now());
            saved.setUpdatedAt(Instant.now());
            return saved;
        });

        service.createSet(new CreatePersonalFlashcardSetRequest("  Vocabulary  ", "  Notes  "));

        ArgumentCaptor<FlashcardSet> captor = ArgumentCaptor.forClass(FlashcardSet.class);
        verify(flashcardSetRepository).save(captor.capture());
        FlashcardSet saved = captor.getValue();
        assertThat(saved.getCreatedBy()).isSameAs(actor);
        assertThat(saved.getCourse()).isNull();
        assertThat(saved.getLesson()).isNull();
        assertThat(saved.getCurriculumLessonId()).isNull();
        assertThat(saved.getIsPublic()).isFalse();
        assertThat(saved.getIsOfficial()).isFalse();
        assertThat(saved.getTitle()).isEqualTo("Vocabulary");
        assertThat(saved.getDescription()).isEqualTo("Notes");
    }

    @Test
    void newlyCreatedPersonalCardShouldAppearImmediatelyInDetailAndStudy() {
        UserAccount actor = user("SME");
        FlashcardSet set = personalSet(actor);
        UUID cardId = UUID.randomUUID();
        Instant now = Instant.now();
        when(currentUserService.requireAuthenticatedUser()).thenReturn(actor);
        when(flashcardSetRepository.findPersonalForUpdateByIdAndOwnerId(set.getId(), actor.getId()))
                .thenReturn(Optional.of(set));
        when(flashcardCardRepository.countActiveBySetId(set.getId())).thenReturn(0L);
        when(flashcardCardRepository.findMaxOrderIndexBySetId(set.getId())).thenReturn(-1);
        when(flashcardCardRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            FlashcardCard card = invocation.getArgument(0);
            card.setId(cardId);
            card.setCreatedAt(now);
            card.setUpdatedAt(now);
            return card;
        });
        when(flashcardSetRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.addCard(set.getId(), new CreatePersonalFlashcardCardRequest(
                "Front", null, "Back", null, null, null
        ));

        FlashcardCard savedCard = new FlashcardCard();
        savedCard.setId(cardId);
        savedCard.setFlashcardSet(set);
        savedCard.setFrontText("Front");
        savedCard.setBackText("Back");
        savedCard.setOrderIndex(0);
        savedCard.setCreatedAt(now);
        savedCard.setUpdatedAt(now);
        when(flashcardSetRepository.findPersonalByIdAndOwnerId(set.getId(), actor.getId()))
                .thenReturn(Optional.of(set));
        when(flashcardCardRepository.findPersonalActiveBySetIdOrderByOrderIndex(set.getId()))
                .thenReturn(List.of(savedCard));

        PersonalFlashcardSetDetailResponse detail = service.getSet(set.getId());
        PersonalFlashcardStudyResponse study = service.getStudy(set.getId());

        assertThat(detail.cards()).extracting(card -> card.id()).containsExactly(cardId);
        assertThat(study.cards()).extracting(card -> card.id()).containsExactly(cardId);
    }

    @Test
    void listSetsWithMissingQueryShouldUseNoSearchQueryPathAndActiveCardCounts() {
        UserAccount actor = user("TRAINER");
        FlashcardSet set = personalSet(actor);
        PersonalCardCountProjection count = org.mockito.Mockito.mock(PersonalCardCountProjection.class);
        when(count.getSetId()).thenReturn(set.getId());
        when(count.getCardCount()).thenReturn(3L);
        when(currentUserService.requireAuthenticatedUser()).thenReturn(actor);
        when(flashcardSetRepository.findPersonalSetsByOwnerOrderByUpdated(
                eq(actor.getId()), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(set), PageRequest.of(0, 20), 1));
        when(flashcardCardRepository.countActiveCardsBySetIds(List.of(set.getId()))).thenReturn(List.of(count));

        PageResponse<PersonalFlashcardSetSummaryResponse> response = service.listSets(null, "updated_desc", 0, 20);

        assertThat(response.items()).singleElement()
                .extracting(PersonalFlashcardSetSummaryResponse::activeCardCount)
                .isEqualTo(3L);
    }

    @Test
    void listSetsWithBlankQueryShouldUseNoSearchQueryPath() {
        UserAccount actor = user("TRAINEE");
        FlashcardSet set = personalSet(actor);
        when(currentUserService.requireAuthenticatedUser()).thenReturn(actor);
        when(flashcardSetRepository.findPersonalSetsByOwnerOrderByTitle(eq(actor.getId()), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(set), PageRequest.of(0, 20), 1));
        when(flashcardCardRepository.countActiveCardsBySetIds(List.of(set.getId()))).thenReturn(List.of());

        PageResponse<PersonalFlashcardSetSummaryResponse> response = service.listSets("   ", "title_asc", 0, 20);

        assertThat(response.items()).hasSize(1);
    }

    @Test
    void listSetsWithNonblankQueryShouldTrimAndUseSearchQueryPath() {
        UserAccount actor = user("SME");
        FlashcardSet set = personalSet(actor);
        set.setTitle("Personal chemistry");
        when(currentUserService.requireAuthenticatedUser()).thenReturn(actor);
        when(flashcardSetRepository.findPersonalSetsByOwnerAndTitleSearchOrderByUpdated(
                eq(actor.getId()), eq("chem"), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(set), PageRequest.of(0, 20), 1));
        when(flashcardCardRepository.countActiveCardsBySetIds(List.of(set.getId()))).thenReturn(List.of());

        PageResponse<PersonalFlashcardSetSummaryResponse> response = service.listSets("  chem  ", "updated_desc", 0, 20);

        assertThat(response.items()).singleElement()
                .extracting(PersonalFlashcardSetSummaryResponse::title)
                .isEqualTo("Personal chemistry");
    }

    @Test
    void addCardShouldRequireBothFrontAndBackToHaveContent() {
        UserAccount actor = user("TRAINEE");
        FlashcardSet set = personalSet(actor);
        when(currentUserService.requireAuthenticatedUser()).thenReturn(actor);
        when(flashcardSetRepository.findPersonalForUpdateByIdAndOwnerId(set.getId(), actor.getId()))
                .thenReturn(Optional.of(set));

        assertThatThrownBy(() -> service.addCard(
                set.getId(),
                new CreatePersonalFlashcardCardRequest("Front only", null, "   ", null, null, null)
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
    }

    @Test
    void addCardShouldRejectTheFiveHundredFirstActiveCard() {
        UserAccount actor = user("TRAINEE");
        FlashcardSet set = personalSet(actor);
        when(currentUserService.requireAuthenticatedUser()).thenReturn(actor);
        when(flashcardSetRepository.findPersonalForUpdateByIdAndOwnerId(set.getId(), actor.getId()))
                .thenReturn(Optional.of(set));
        when(flashcardCardRepository.countActiveBySetId(set.getId())).thenReturn(500L);

        assertThatThrownBy(() -> service.addCard(
                set.getId(),
                new CreatePersonalFlashcardCardRequest("Front", null, "Back", null, null, null)
        )).isInstanceOfSatisfying(BusinessException.class, exception -> {
            assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST);
            assertThat(exception.getMessage()).contains("500 active cards");
        });
        verify(flashcardCardRepository, never()).saveAndFlush(any());
    }

    @Test
    void replaceCardShouldReturnFlushedUpdatedAt() {
        UserAccount actor = user("TRAINER");
        FlashcardSet set = personalSet(actor);
        FlashcardCard card = card(set, 0);
        Instant oldUpdatedAt = Instant.parse("2026-07-25T10:00:00Z");
        Instant flushedUpdatedAt = Instant.parse("2026-07-25T10:05:00Z");
        card.setUpdatedAt(oldUpdatedAt);
        when(currentUserService.requireAuthenticatedUser()).thenReturn(actor);
        when(flashcardSetRepository.findPersonalForUpdateByIdAndOwnerId(set.getId(), actor.getId()))
                .thenReturn(Optional.of(set));
        when(flashcardCardRepository.findActiveBySetIdAndIdIn(set.getId(), List.of(card.getId())))
                .thenReturn(List.of(card));
        when(flashcardCardRepository.saveAndFlush(card)).thenAnswer(invocation -> {
            card.setUpdatedAt(flushedUpdatedAt);
            return card;
        });
        when(flashcardSetRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.replaceCard(
                set.getId(),
                card.getId(),
                new ReplacePersonalFlashcardCardRequest("Updated front", null, "Updated back", null, null, null)
        );

        assertThat(response.updatedAt()).isEqualTo(flushedUpdatedAt);
    }

    @Test
    void replaceCardShouldAcceptAnUnchangedStoredImageUrl() {
        UserAccount actor = user("SME");
        FlashcardSet set = personalSet(actor);
        FlashcardCard card = card(set, 0);
        String imageUrl = "https://cdn.test/flashcard-sets/" + set.getId() + "/images/front.png";
        card.setFrontImageUrl(imageUrl);
        when(currentUserService.requireAuthenticatedUser()).thenReturn(actor);
        when(flashcardSetRepository.findPersonalForUpdateByIdAndOwnerId(set.getId(), actor.getId()))
                .thenReturn(Optional.of(set));
        when(flashcardCardRepository.findActiveBySetIdAndIdIn(set.getId(), List.of(card.getId())))
                .thenReturn(List.of(card));
        when(flashcardCardRepository.saveAndFlush(card)).thenReturn(card);
        when(flashcardSetRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.replaceCard(
                set.getId(),
                card.getId(),
                new ReplacePersonalFlashcardCardRequest("Front", imageUrl, "Back", null, null, null)
        );

        verify(imageUrlPolicy).validateNewOrUnchanged(imageUrl, imageUrl, set.getId());
    }

    @Test
    void bulkDeleteAndReorderShouldOperateOnActiveCardsWithoutStatusFiltering() {
        UserAccount actor = user("SME");
        FlashcardSet set = personalSet(actor);
        FlashcardCard first = card(set, 0);
        FlashcardCard second = card(set, 1);
        when(currentUserService.requireAuthenticatedUser()).thenReturn(actor);
        when(flashcardSetRepository.findPersonalForUpdateByIdAndOwnerId(set.getId(), actor.getId()))
                .thenReturn(Optional.of(set));
        when(flashcardCardRepository.findPersonalActiveBySetIdOrderByOrderIndex(set.getId()))
                .thenReturn(List.of(first, second));
        when(flashcardSetRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.reorderCards(set.getId(), new ReorderPersonalFlashcardCardsRequest(List.of(second.getId(), first.getId())));

        assertThat(second.getOrderIndex()).isZero();
        assertThat(first.getOrderIndex()).isEqualTo(1);
        when(flashcardCardRepository.findActiveBySetIdAndIdIn(set.getId(), List.of(first.getId())))
                .thenReturn(List.of(first));

        service.bulkDeleteCards(set.getId(), new BulkDeletePersonalFlashcardCardsRequest(List.of(first.getId())));

        assertThat(first.getDeletedAt()).isNotNull();
        verify(flashcardCardRepository).saveAll(List.of(first, second));
        verify(flashcardCardRepository).saveAll(List.of(first));
    }

    @Test
    void adminShouldNotBeEligibleForPersonalFlashcards() {
        when(currentUserService.requireAuthenticatedUser()).thenReturn(user("ADMIN"));

        assertThatThrownBy(() -> service.listSets(null, "updated_desc", 0, 20))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void tmoShouldNotBeEligibleForPersonalFlashcards() {
        when(currentUserService.requireAuthenticatedUser()).thenReturn(user("TMO"));

        assertThatThrownBy(() -> service.listSets(null, "updated_desc", 0, 20))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void crossOwnerDetailAndMutationShouldReturnNotFound() {
        UserAccount actor = user("TRAINEE");
        UUID setId = UUID.randomUUID();
        UUID cardId = UUID.randomUUID();
        when(currentUserService.requireAuthenticatedUser()).thenReturn(actor);
        when(flashcardSetRepository.findPersonalByIdAndOwnerId(setId, actor.getId()))
                .thenReturn(Optional.empty());
        when(flashcardSetRepository.findPersonalForUpdateByIdAndOwnerId(setId, actor.getId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getSet(setId))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
        assertThatThrownBy(() -> service.replaceCard(
                setId,
                cardId,
                new ReplacePersonalFlashcardCardRequest("Front", null, "Back", null, null, null)
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.errorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private UserAccount user(String role) {
        UserAccount user = new UserAccount();
        user.setId(UUID.randomUUID());
        user.setRole(role);
        user.setEmail(role.toLowerCase() + "@smartlearnly.dev");
        return user;
    }

    private FlashcardSet personalSet(UserAccount owner) {
        FlashcardSet set = new FlashcardSet();
        set.setId(UUID.randomUUID());
        set.setCreatedBy(owner);
        set.setTitle("Personal set");
        set.setIsPublic(false);
        set.setIsOfficial(false);
        set.setCreatedAt(Instant.now());
        set.setUpdatedAt(Instant.now());
        return set;
    }

    private FlashcardCard card(FlashcardSet set, int orderIndex) {
        FlashcardCard card = new FlashcardCard();
        card.setId(UUID.randomUUID());
        card.setFlashcardSet(set);
        card.setFrontText("Front " + orderIndex);
        card.setBackText("Back " + orderIndex);
        card.setOrderIndex(orderIndex);
        card.setCreatedAt(Instant.now());
        card.setUpdatedAt(Instant.now());
        return card;
    }
}
