package com.smartlearnly.backend.curriculum.service;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.course.entity.Course;
import com.smartlearnly.backend.course.repository.CourseRepository;
import com.smartlearnly.backend.curriculum.entity.CurriculumLesson;
import com.smartlearnly.backend.curriculum.entity.CurriculumSection;
import com.smartlearnly.backend.curriculum.entity.CurriculumVersion;
import com.smartlearnly.backend.curriculum.repository.CurriculumLessonRepository;
import com.smartlearnly.backend.flashcard.dto.AdminFlashcardDtos.CreateFlashcardCardRequest;
import com.smartlearnly.backend.flashcard.dto.AdminFlashcardDtos.CreateFlashcardLessonRequest;
import com.smartlearnly.backend.flashcard.dto.AdminFlashcardDtos.FlashcardCardResponse;
import com.smartlearnly.backend.flashcard.dto.AdminFlashcardDtos.FlashcardLessonCreatedResponse;
import com.smartlearnly.backend.flashcard.dto.AdminFlashcardDtos.FlashcardSetResponse;
import com.smartlearnly.backend.flashcard.dto.AdminFlashcardDtos.ReorderFlashcardCardsRequest;
import com.smartlearnly.backend.flashcard.dto.AdminFlashcardDtos.UpdateFlashcardCardRequest;
import com.smartlearnly.backend.flashcard.dto.AdminFlashcardDtos.UpdateFlashcardSetRequest;
import com.smartlearnly.backend.flashcard.entity.FlashcardCard;
import com.smartlearnly.backend.flashcard.entity.FlashcardSet;
import com.smartlearnly.backend.flashcard.repository.FlashcardCardRepository;
import com.smartlearnly.backend.flashcard.repository.FlashcardSetRepository;
import com.smartlearnly.backend.learning.lesson.entity.LessonType;
import com.smartlearnly.backend.user.entity.UserAccount;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Trainer-scoped flashcard authoring for class-draft curriculum lessons.
 * Every operation verifies the lesson belongs to the trainer's class draft
 * AND that the flashcard set / card belongs to that lesson before touching
 * database rows. Sets are linked via {@code curriculum_lesson_id} so master
 * flashcards are unaffected.
 */
@Service
@RequiredArgsConstructor
public class TrainerLessonFlashcardService {
    private final TrainerClassCurriculumService trainerClassCurriculumService;
    private final CurriculumLessonRepository curriculumLessonRepository;
    private final CourseRepository courseRepository;
    private final FlashcardSetRepository flashcardSetRepository;
    private final FlashcardCardRepository flashcardCardRepository;
    private final CurrentUserService currentUserService;

    @Transactional
    public FlashcardLessonCreatedResponse createFlashcardSet(
            UUID classId,
            UUID lessonId,
            CreateFlashcardLessonRequest request
    ) {
        CurriculumLesson lesson = trainerClassCurriculumService.requireOwnedClassLessonForWrite(classId, lessonId);
        if (flashcardSetRepository.findByCurriculumLessonIdAndDeletedAtIsNull(lesson.getId()).isPresent()) {
            throw new BusinessException(ErrorCode.CONFLICT, "Flashcard set already exists for this lesson");
        }
        lesson.setType(LessonType.FLASHCARD);
        curriculumLessonRepository.save(lesson);

        UserAccount actor = currentUserService.requireAuthenticatedUser();
        FlashcardSet flashcardSet = new FlashcardSet();
        flashcardSet.setCurriculumLessonId(lesson.getId());
        flashcardSet.setCourse(loadCourseIfPossible(lesson));
        flashcardSet.setCreatedBy(actor);
        flashcardSet.setTitle(normalizeRequired(request.title(), "Flashcard lesson title is required"));
        flashcardSet.setDescription(normalizeNullable(request.description()));
        flashcardSet.setIsPublic(false);
        flashcardSet.setIsOfficial(false);
        FlashcardSet saved = flashcardSetRepository.save(flashcardSet);
        return new FlashcardLessonCreatedResponse(lesson.getId(), saved.getId());
    }

    @Transactional(readOnly = true)
    public FlashcardSetResponse getSetByLesson(UUID classId, UUID lessonId) {
        CurriculumLesson lesson = trainerClassCurriculumService.requireOwnedClassLessonForRead(classId, lessonId);
        FlashcardSet flashcardSet = requireSetByLesson(lesson.getId());
        return toSetResponse(lesson, flashcardSet, findActiveCards(flashcardSet.getId()));
    }

    @Transactional
    public FlashcardSetResponse updateSet(UUID classId, UUID lessonId, UUID setId, UpdateFlashcardSetRequest request) {
        CurriculumLesson lesson = trainerClassCurriculumService.requireOwnedClassLessonForWrite(classId, lessonId);
        FlashcardSet flashcardSet = requireSetForLesson(lesson.getId(), setId);
        if (request.title() != null) {
            String title = normalizeRequired(request.title(), "Flashcard set title is required");
            flashcardSet.setTitle(title);
            lesson.setTitle(title);
            curriculumLessonRepository.save(lesson);
        }
        if (request.description() != null) {
            flashcardSet.setDescription(normalizeNullable(request.description()));
        }
        FlashcardSet saved = flashcardSetRepository.save(flashcardSet);
        return toSetResponse(lesson, saved, findActiveCards(saved.getId()));
    }

    @Transactional
    public void deleteSet(UUID classId, UUID lessonId, UUID setId) {
        CurriculumLesson lesson = trainerClassCurriculumService.requireOwnedClassLessonForWrite(classId, lessonId);
        FlashcardSet flashcardSet = requireSetForLesson(lesson.getId(), setId);
        Instant now = Instant.now();
        flashcardSet.setDeletedAt(now);
        List<FlashcardCard> activeCards = findActiveCards(setId);
        activeCards.forEach(card -> card.setDeletedAt(now));
        flashcardCardRepository.saveAll(activeCards);
        flashcardSetRepository.save(flashcardSet);
    }

    @Transactional
    public FlashcardCardResponse addCard(UUID classId, UUID lessonId, UUID setId, CreateFlashcardCardRequest request) {
        CurriculumLesson lesson = trainerClassCurriculumService.requireOwnedClassLessonForWrite(classId, lessonId);
        FlashcardSet flashcardSet = requireSetForLesson(lesson.getId(), setId);
        FlashcardCard card = new FlashcardCard();
        card.setFlashcardSet(flashcardSet);
        card.setFrontText(normalizeNullable(request.frontText()));
        card.setFrontImageUrl(normalizeNullable(request.frontImageUrl()));
        card.setBackText(normalizeNullable(request.backText()));
        card.setBackImageUrl(normalizeNullable(request.backImageUrl()));
        card.setHint(normalizeNullable(request.hint()));
        card.setExplanation(normalizeNullable(request.explanation()));
        validateCard(card);
        card.setOrderIndex(request.orderIndex() == null
                ? flashcardCardRepository.findMaxOrderIndexBySetId(setId) + 1
                : request.orderIndex());
        return toCardResponse(flashcardCardRepository.save(card));
    }

    @Transactional
    public FlashcardCardResponse updateCard(
            UUID classId,
            UUID lessonId,
            UUID setId,
            UUID cardId,
            UpdateFlashcardCardRequest request
    ) {
        CurriculumLesson lesson = trainerClassCurriculumService.requireOwnedClassLessonForWrite(classId, lessonId);
        requireSetForLesson(lesson.getId(), setId);
        FlashcardCard card = requireCardForSet(setId, cardId);
        if (request.frontText() != null) {
            card.setFrontText(normalizeNullable(request.frontText()));
        }
        if (request.frontImageUrl() != null) {
            card.setFrontImageUrl(normalizeNullable(request.frontImageUrl()));
        }
        if (request.backText() != null) {
            card.setBackText(normalizeNullable(request.backText()));
        }
        if (request.backImageUrl() != null) {
            card.setBackImageUrl(normalizeNullable(request.backImageUrl()));
        }
        if (request.hint() != null) {
            card.setHint(normalizeNullable(request.hint()));
        }
        if (request.explanation() != null) {
            card.setExplanation(normalizeNullable(request.explanation()));
        }
        if (request.orderIndex() != null) {
            card.setOrderIndex(request.orderIndex());
        }
        validateCard(card);
        return toCardResponse(flashcardCardRepository.save(card));
    }

    @Transactional
    public void deleteCard(UUID classId, UUID lessonId, UUID setId, UUID cardId) {
        CurriculumLesson lesson = trainerClassCurriculumService.requireOwnedClassLessonForWrite(classId, lessonId);
        requireSetForLesson(lesson.getId(), setId);
        FlashcardCard card = requireCardForSet(setId, cardId);
        card.setDeletedAt(Instant.now());
        flashcardCardRepository.save(card);
    }

    @Transactional
    public FlashcardSetResponse reorderCards(
            UUID classId,
            UUID lessonId,
            UUID setId,
            ReorderFlashcardCardsRequest request
    ) {
        CurriculumLesson lesson = trainerClassCurriculumService.requireOwnedClassLessonForWrite(classId, lessonId);
        FlashcardSet flashcardSet = requireSetForLesson(lesson.getId(), setId);
        List<FlashcardCard> activeCards = findActiveCards(setId);
        Map<UUID, FlashcardCard> cardsById = activeCards.stream()
                .collect(LinkedHashMap::new, (map, card) -> map.put(card.getId(), card), LinkedHashMap::putAll);
        List<UUID> ids = request == null ? List.of() : request.ids();
        Set<UUID> uniqueIds = new HashSet<>(ids);
        if (uniqueIds.size() != ids.size()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Flashcard reorder list contains duplicate ids");
        }
        if (!uniqueIds.equals(cardsById.keySet())) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Flashcard reorder request must include every active card exactly once"
            );
        }
        int orderIndex = 0;
        for (UUID cardId : ids) {
            cardsById.get(cardId).setOrderIndex(orderIndex);
            orderIndex++;
        }
        flashcardCardRepository.saveAll(activeCards);
        return toSetResponse(lesson, flashcardSet, activeCards.stream()
                .sorted(Comparator.comparing(FlashcardCard::getOrderIndex))
                .toList());
    }

    private FlashcardSet requireSetByLesson(UUID curriculumLessonId) {
        return flashcardSetRepository.findByCurriculumLessonIdAndDeletedAtIsNull(curriculumLessonId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Flashcard set was not found"));
    }

    private FlashcardSet requireSetForLesson(UUID curriculumLessonId, UUID setId) {
        FlashcardSet flashcardSet = flashcardSetRepository.findByIdAndDeletedAtIsNull(setId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Flashcard set was not found"));
        if (!curriculumLessonId.equals(flashcardSet.getCurriculumLessonId())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Flashcard set was not found");
        }
        return flashcardSet;
    }

    private FlashcardCard requireCardForSet(UUID setId, UUID cardId) {
        FlashcardCard card = flashcardCardRepository.findByIdAndDeletedAtIsNull(cardId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Flashcard card was not found"));
        FlashcardSet flashcardSet = card.getFlashcardSet();
        if (flashcardSet == null
                || flashcardSet.getDeletedAt() != null
                || !setId.equals(flashcardSet.getId())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Flashcard card was not found");
        }
        return card;
    }

    private List<FlashcardCard> findActiveCards(UUID setId) {
        return flashcardCardRepository.findActiveBySetIdOrderByOrderIndex(setId);
    }

    private Course loadCourseIfPossible(CurriculumLesson lesson) {
        CurriculumSection section = lesson.getSection();
        if (section == null) {
            return null;
        }
        CurriculumVersion version = section.getCurriculumVersion();
        if (version == null || version.getCourseId() == null) {
            return null;
        }
        return courseRepository.findByIdAndDeletedAtIsNull(version.getCourseId()).orElse(null);
    }

    private void validateCard(FlashcardCard card) {
        boolean hasFront = hasText(card.getFrontText()) || hasText(card.getFrontImageUrl());
        boolean hasBack = hasText(card.getBackText()) || hasText(card.getBackImageUrl());
        if (!hasFront) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Flashcard front side requires text or image");
        }
        if (!hasBack) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Flashcard back side requires text or image");
        }
    }

    private FlashcardSetResponse toSetResponse(CurriculumLesson lesson, FlashcardSet flashcardSet, List<FlashcardCard> cards) {
        CurriculumSection section = lesson.getSection();
        CurriculumVersion version = section == null ? null : section.getCurriculumVersion();
        return new FlashcardSetResponse(
                flashcardSet.getId(),
                lesson.getId(),
                version == null ? null : version.getCourseId(),
                section == null ? null : section.getId(),
                flashcardSet.getTitle(),
                flashcardSet.getDescription(),
                cards.stream().map(this::toCardResponse).toList(),
                flashcardSet.getCreatedAt(),
                flashcardSet.getUpdatedAt()
        );
    }

    private FlashcardCardResponse toCardResponse(FlashcardCard card) {
        return new FlashcardCardResponse(
                card.getId(),
                card.getFlashcardSet().getId(),
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
}
