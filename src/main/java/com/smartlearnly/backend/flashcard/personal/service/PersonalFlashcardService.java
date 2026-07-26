package com.smartlearnly.backend.flashcard.personal.service;

import com.smartlearnly.backend.common.api.PageResponse;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.flashcard.entity.FlashcardCard;
import com.smartlearnly.backend.flashcard.entity.FlashcardSet;
import com.smartlearnly.backend.flashcard.personal.dto.PersonalFlashcardDtos.BulkDeletePersonalFlashcardCardsRequest;
import com.smartlearnly.backend.flashcard.personal.dto.PersonalFlashcardDtos.CreatePersonalFlashcardCardRequest;
import com.smartlearnly.backend.flashcard.personal.dto.PersonalFlashcardDtos.CreatePersonalFlashcardSetRequest;
import com.smartlearnly.backend.flashcard.personal.dto.PersonalFlashcardDtos;
import com.smartlearnly.backend.flashcard.personal.dto.PersonalFlashcardDtos.PersonalBulkDeleteResponse;
import com.smartlearnly.backend.flashcard.personal.dto.PersonalFlashcardDtos.PersonalFlashcardCardResponse;
import com.smartlearnly.backend.flashcard.personal.dto.PersonalFlashcardDtos.PersonalFlashcardSetDetailResponse;
import com.smartlearnly.backend.flashcard.personal.dto.PersonalFlashcardDtos.PersonalFlashcardSetSummaryResponse;
import com.smartlearnly.backend.flashcard.personal.dto.PersonalFlashcardDtos.PersonalFlashcardStudyResponse;
import com.smartlearnly.backend.flashcard.personal.dto.PersonalFlashcardDtos.ReorderPersonalFlashcardCardsRequest;
import com.smartlearnly.backend.flashcard.personal.dto.PersonalFlashcardDtos.ReplacePersonalFlashcardCardRequest;
import com.smartlearnly.backend.flashcard.personal.dto.PersonalFlashcardDtos.ReplacePersonalFlashcardSetRequest;
import com.smartlearnly.backend.flashcard.repository.FlashcardCardRepository;
import com.smartlearnly.backend.flashcard.repository.FlashcardCardRepository.PersonalCardCountProjection;
import com.smartlearnly.backend.flashcard.repository.FlashcardSetRepository;
import com.smartlearnly.backend.user.entity.UserAccount;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PersonalFlashcardService {
    private final FlashcardSetRepository flashcardSetRepository;
    private final FlashcardCardRepository flashcardCardRepository;
    private final CurrentUserService currentUserService;
    private final PersonalFlashcardImageUrlPolicy imageUrlPolicy;

    @Transactional(readOnly = true)
    public PageResponse<PersonalFlashcardSetSummaryResponse> listSets(
            String query,
            String sort,
            int page,
            int size
    ) {
        UserAccount actor = requireEligibleActor();
        String normalizedQuery = normalizeNullable(query);
        PageRequest pageable = PageRequest.of(page, size);
        Page<FlashcardSet> result = switch (parseSort(sort)) {
            case UPDATED_DESC -> normalizedQuery == null
                    ? flashcardSetRepository.findPersonalSetsByOwnerOrderByUpdated(actor.getId(), pageable)
                    : flashcardSetRepository.findPersonalSetsByOwnerAndTitleSearchOrderByUpdated(
                            actor.getId(), normalizedQuery, pageable);
            case TITLE_ASC -> normalizedQuery == null
                    ? flashcardSetRepository.findPersonalSetsByOwnerOrderByTitle(actor.getId(), pageable)
                    : flashcardSetRepository.findPersonalSetsByOwnerAndTitleSearchOrderByTitle(
                            actor.getId(), normalizedQuery, pageable);
        };

        List<UUID> setIds = result.getContent().stream().map(FlashcardSet::getId).toList();
        Map<UUID, Long> countsBySetId = setIds.isEmpty()
                ? Map.of()
                : flashcardCardRepository.countActiveCardsBySetIds(setIds).stream()
                        .collect(Collectors.toMap(
                                PersonalCardCountProjection::getSetId,
                                projection -> projection.getCardCount() == null ? 0L : projection.getCardCount()
                        ));

        return new PageResponse<>(
                result.getContent().stream()
                        .map(set -> toSummary(set, countsBySetId.getOrDefault(set.getId(), 0L)))
                        .toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    @Transactional
    public PersonalFlashcardSetDetailResponse createSet(CreatePersonalFlashcardSetRequest request) {
        UserAccount actor = requireEligibleActor();
        FlashcardSet flashcardSet = new FlashcardSet();
        flashcardSet.setCreatedBy(actor);
        flashcardSet.setCourse(null);
        flashcardSet.setLesson(null);
        flashcardSet.setCurriculumLessonId(null);
        flashcardSet.setIsPublic(false);
        flashcardSet.setIsOfficial(false);
        flashcardSet.setTitle(normalizeRequired(request.title(), "Flashcard set title is required"));
        flashcardSet.setDescription(normalizeNullable(request.description()));

        FlashcardSet saved = flashcardSetRepository.save(flashcardSet);
        return toDetail(saved, List.of());
    }

    @Transactional(readOnly = true)
    public PersonalFlashcardSetDetailResponse getSet(UUID setId) {
        FlashcardSet flashcardSet = requirePersonalSet(requireEligibleActor(), setId);
        return toDetail(flashcardSet, findActiveCards(flashcardSet.getId()));
    }

    @Transactional
    public PersonalFlashcardSetDetailResponse replaceSet(UUID setId, ReplacePersonalFlashcardSetRequest request) {
        FlashcardSet flashcardSet = requirePersonalSetForWrite(requireEligibleActor(), setId);
        flashcardSet.setTitle(normalizeRequired(request.title(), "Flashcard set title is required"));
        flashcardSet.setDescription(normalizeNullable(request.description()));
        FlashcardSet saved = flashcardSetRepository.save(flashcardSet);
        return toDetail(saved, findActiveCards(saved.getId()));
    }

    @Transactional
    public void deleteSet(UUID setId) {
        FlashcardSet flashcardSet = requirePersonalSetForWrite(requireEligibleActor(), setId);
        Instant now = Instant.now();
        flashcardSet.setDeletedAt(now);
        flashcardSet.setUpdatedAt(now);

        List<FlashcardCard> cards = findActiveCards(flashcardSet.getId());
        cards.forEach(card -> card.setDeletedAt(now));
        flashcardCardRepository.saveAll(cards);
        flashcardSetRepository.save(flashcardSet);
    }

    @Transactional
    public PersonalFlashcardCardResponse addCard(UUID setId, CreatePersonalFlashcardCardRequest request) {
        FlashcardSet flashcardSet = requirePersonalSetForWrite(requireEligibleActor(), setId);
        requireBelowCardLimit(setId);
        FlashcardCard card = new FlashcardCard();
        card.setFlashcardSet(flashcardSet);
        applyCardValues(
                card,
                request.frontText(),
                request.frontImageUrl(),
                request.backText(),
                request.backImageUrl(),
                request.hint(),
                request.explanation(),
                flashcardSet.getId()
        );
        card.setOrderIndex(flashcardCardRepository.findMaxOrderIndexBySetId(setId) + 1);

        FlashcardCard saved = flashcardCardRepository.saveAndFlush(card);
        touchSet(flashcardSet);
        return toCardResponse(saved);
    }

    @Transactional
    public PersonalFlashcardCardResponse replaceCard(
            UUID setId,
            UUID cardId,
            ReplacePersonalFlashcardCardRequest request
    ) {
        FlashcardSet flashcardSet = requirePersonalSetForWrite(requireEligibleActor(), setId);
        FlashcardCard card = requireActiveCard(flashcardSet.getId(), cardId);
        applyCardValues(
                card,
                request.frontText(),
                request.frontImageUrl(),
                request.backText(),
                request.backImageUrl(),
                request.hint(),
                request.explanation(),
                flashcardSet.getId()
        );

        FlashcardCard saved = flashcardCardRepository.saveAndFlush(card);
        touchSet(flashcardSet);
        return toCardResponse(saved);
    }

    @Transactional
    public void deleteCard(UUID setId, UUID cardId) {
        FlashcardSet flashcardSet = requirePersonalSetForWrite(requireEligibleActor(), setId);
        FlashcardCard card = requireActiveCard(flashcardSet.getId(), cardId);
        card.setDeletedAt(Instant.now());
        flashcardCardRepository.save(card);
        touchSet(flashcardSet);
    }

    @Transactional
    public PersonalBulkDeleteResponse bulkDeleteCards(
            UUID setId,
            BulkDeletePersonalFlashcardCardsRequest request
    ) {
        FlashcardSet flashcardSet = requirePersonalSetForWrite(requireEligibleActor(), setId);
        assertNoDuplicates(request.ids(), "Bulk delete list contains duplicate ids");
        List<FlashcardCard> cards = flashcardCardRepository.findActiveBySetIdAndIdIn(setId, request.ids());
        if (cards.size() != request.ids().size()) {
            throw cardNotFound();
        }

        Instant now = Instant.now();
        cards.forEach(card -> card.setDeletedAt(now));
        flashcardCardRepository.saveAll(cards);
        touchSet(flashcardSet, now);
        return new PersonalBulkDeleteResponse(cards.size(), now);
    }

    @Transactional
    public PersonalFlashcardSetDetailResponse reorderCards(
            UUID setId,
            ReorderPersonalFlashcardCardsRequest request
    ) {
        FlashcardSet flashcardSet = requirePersonalSetForWrite(requireEligibleActor(), setId);
        assertNoDuplicates(request.ids(), "Flashcard reorder list contains duplicate ids");

        List<FlashcardCard> activeCards = findActiveCards(setId);
        Map<UUID, FlashcardCard> cardsById = activeCards.stream()
                .collect(Collectors.toMap(FlashcardCard::getId, Function.identity()));
        Set<UUID> requestedIds = new HashSet<>(request.ids());
        Set<UUID> activeIds = cardsById.keySet();
        if (!activeIds.containsAll(requestedIds)) {
            throw cardNotFound();
        }
        if (!requestedIds.containsAll(activeIds)) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Flashcard reorder request must include every active card exactly once"
            );
        }

        int orderIndex = 0;
        for (UUID cardId : request.ids()) {
            cardsById.get(cardId).setOrderIndex(orderIndex++);
        }
        flashcardCardRepository.saveAll(activeCards);
        touchSet(flashcardSet);
        return toDetail(flashcardSet, findActiveCards(setId));
    }

    @Transactional(readOnly = true)
    public PersonalFlashcardStudyResponse getStudy(UUID setId) {
        FlashcardSet flashcardSet = requirePersonalSet(requireEligibleActor(), setId);
        return new PersonalFlashcardStudyResponse(
                flashcardSet.getId(),
                flashcardSet.getTitle(),
                findActiveCards(flashcardSet.getId()).stream().map(this::toCardResponse).toList()
        );
    }

    private UserAccount requireEligibleActor() {
        UserAccount actor = currentUserService.requireAuthenticatedUser();
        String role = actor.getRole() == null ? "" : actor.getRole().trim().toUpperCase(Locale.ROOT);
        if (!Set.of("TRAINEE", "TRAINER", "SME").contains(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "You are not allowed to use Personal Flashcards");
        }
        return actor;
    }

    private FlashcardSet requirePersonalSet(UserAccount actor, UUID setId) {
        return flashcardSetRepository.findPersonalByIdAndOwnerId(setId, actor.getId())
                .orElseThrow(this::setNotFound);
    }

    private FlashcardSet requirePersonalSetForWrite(UserAccount actor, UUID setId) {
        return flashcardSetRepository.findPersonalForUpdateByIdAndOwnerId(setId, actor.getId())
                .orElseThrow(this::setNotFound);
    }

    private FlashcardCard requireActiveCard(UUID setId, UUID cardId) {
        return flashcardCardRepository.findActiveBySetIdAndIdIn(setId, List.of(cardId)).stream()
                .findFirst()
                .orElseThrow(this::cardNotFound);
    }

    private List<FlashcardCard> findActiveCards(UUID setId) {
        return flashcardCardRepository.findPersonalActiveBySetIdOrderByOrderIndex(setId);
    }

    private void requireBelowCardLimit(UUID setId) {
        if (flashcardCardRepository.countActiveBySetId(setId)
                >= PersonalFlashcardDtos.MAX_ACTIVE_CARDS_PER_PERSONAL_SET) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Personal flashcard sets cannot exceed 500 active cards"
            );
        }
    }

    private void applyCardValues(
            FlashcardCard card,
            String frontText,
            String frontImageUrl,
            String backText,
            String backImageUrl,
            String hint,
            String explanation,
            UUID setId
    ) {
        String normalizedFrontImageUrl = normalizeNullable(frontImageUrl);
        String normalizedBackImageUrl = normalizeNullable(backImageUrl);
        imageUrlPolicy.validateNewOrUnchanged(normalizedFrontImageUrl, card.getFrontImageUrl(), setId);
        imageUrlPolicy.validateNewOrUnchanged(normalizedBackImageUrl, card.getBackImageUrl(), setId);

        card.setFrontText(normalizeNullable(frontText));
        card.setFrontImageUrl(normalizedFrontImageUrl);
        card.setBackText(normalizeNullable(backText));
        card.setBackImageUrl(normalizedBackImageUrl);
        card.setHint(normalizeNullable(hint));
        card.setExplanation(normalizeNullable(explanation));
        validateCard(card);
    }

    private void validateCard(FlashcardCard card) {
        if (!hasText(card.getFrontText()) && !hasText(card.getFrontImageUrl())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Flashcard front needs text or an image");
        }
        if (!hasText(card.getBackText()) && !hasText(card.getBackImageUrl())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Flashcard back needs text or an image");
        }
    }

    private void touchSet(FlashcardSet flashcardSet) {
        touchSet(flashcardSet, Instant.now());
    }

    private void touchSet(FlashcardSet flashcardSet, Instant now) {
        flashcardSet.setUpdatedAt(now);
        flashcardSetRepository.save(flashcardSet);
    }

    private PersonalFlashcardSetSummaryResponse toSummary(FlashcardSet flashcardSet, long cardCount) {
        return new PersonalFlashcardSetSummaryResponse(
                flashcardSet.getId(),
                flashcardSet.getTitle(),
                flashcardSet.getDescription(),
                cardCount,
                flashcardSet.getCreatedAt(),
                flashcardSet.getUpdatedAt()
        );
    }

    private PersonalFlashcardSetDetailResponse toDetail(FlashcardSet flashcardSet, List<FlashcardCard> cards) {
        return new PersonalFlashcardSetDetailResponse(
                flashcardSet.getId(),
                flashcardSet.getTitle(),
                flashcardSet.getDescription(),
                cards.stream().map(this::toCardResponse).toList(),
                flashcardSet.getCreatedAt(),
                flashcardSet.getUpdatedAt()
        );
    }

    private PersonalFlashcardCardResponse toCardResponse(FlashcardCard card) {
        return new PersonalFlashcardCardResponse(
                card.getId(),
                card.getFrontText(),
                card.getFrontImageUrl(),
                card.getBackText(),
                card.getBackImageUrl(),
                card.getHint(),
                card.getExplanation(),
                card.getOrderIndex(),
                card.getCreatedAt(),
                card.getUpdatedAt()
        );
    }

    private PersonalSort parseSort(String sort) {
        String normalized = normalizeNullable(sort);
        if (normalized == null || "updated_desc".equalsIgnoreCase(normalized)) {
            return PersonalSort.UPDATED_DESC;
        }
        if ("title_asc".equalsIgnoreCase(normalized)) {
            return PersonalSort.TITLE_ASC;
        }
        throw new BusinessException(ErrorCode.INVALID_REQUEST, "sort must be updated_desc or title_asc");
    }

    private void assertNoDuplicates(List<UUID> ids, String message) {
        if (new HashSet<>(ids).size() != ids.size()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, message);
        }
    }

    private BusinessException setNotFound() {
        return new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Personal flashcard set was not found");
    }

    private BusinessException cardNotFound() {
        return new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Personal flashcard card was not found");
    }

    private String normalizeRequired(String value, String message) {
        String normalized = normalizeNullable(value);
        if (normalized == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, message);
        }
        return normalized;
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

    private enum PersonalSort {
        UPDATED_DESC,
        TITLE_ASC
    }
}
