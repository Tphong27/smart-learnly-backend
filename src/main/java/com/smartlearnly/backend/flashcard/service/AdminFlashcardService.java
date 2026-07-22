package com.smartlearnly.backend.flashcard.service;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.course.entity.Course;
import com.smartlearnly.backend.course.repository.CourseRepository;
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
import com.smartlearnly.backend.course.service.CourseAccessService;
import com.smartlearnly.backend.curriculum.entity.CurriculumLesson;
import com.smartlearnly.backend.curriculum.entity.CurriculumSection;
import com.smartlearnly.backend.curriculum.repository.CurriculumLessonRepository;
import com.smartlearnly.backend.curriculum.repository.CurriculumSectionRepository;
import com.smartlearnly.backend.learning.lesson.entity.Lesson;
import com.smartlearnly.backend.learning.lesson.entity.LessonStatus;
import com.smartlearnly.backend.learning.lesson.entity.LessonType;
import com.smartlearnly.backend.learning.lesson.repository.LessonRepository;
import com.smartlearnly.backend.user.entity.UserAccount;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminFlashcardService {
    private final CourseRepository courseRepository;
    private final LessonRepository lessonRepository;
    private final FlashcardSetRepository flashcardSetRepository;
    private final FlashcardCardRepository flashcardCardRepository;
    private final CurrentUserService currentUserService;
    private final CurriculumLessonRepository curriculumLessonRepository;
    private final CurriculumSectionRepository curriculumSectionRepository;
    private final CourseAccessService courseAccessService;

    @Transactional
    public FlashcardLessonCreatedResponse createFlashcardLesson(
            UUID courseId,
            UUID sectionId,
            CreateFlashcardLessonRequest request) {
        courseAccessService.requireUpdatableCourse(courseId);
        Course course = findCourse(courseId);
        CurriculumSection section = findCurriculumSection(courseId, sectionId);
        UserAccount actor = currentUserService.requireAuthenticatedUser();

        CurriculumLesson lesson = new CurriculumLesson();
        lesson.setSection(section);
        lesson.setLessonIdentityId(UUID.randomUUID());
        lesson.setTitle(normalizeRequired(request.title(), "Flashcard lesson title is required"));
        lesson.setType(LessonType.FLASHCARD);
        lesson.setStatus(parseLessonStatus(request.status(), LessonStatus.DRAFT));
        lesson.setPreview(Boolean.TRUE.equals(request.isPreview()));
        lesson.setSortOrder(request.sortOrder() == null
                ? curriculumLessonRepository.findMaxSortOrderBySectionId(sectionId) + 1
                : request.sortOrder());

        CurriculumLesson savedLesson = curriculumLessonRepository.save(lesson);

        FlashcardSet flashcardSet = new FlashcardSet();
        flashcardSet.setCurriculumLessonId(savedLesson.getId());
        flashcardSet.setCourse(course);
        flashcardSet.setCreatedBy(actor);
        flashcardSet.setTitle(savedLesson.getTitle());
        flashcardSet.setDescription(normalizeNullable(request.description()));
        flashcardSet.setIsPublic(false);
        flashcardSet.setIsOfficial(false);
        FlashcardSet savedSet = flashcardSetRepository.save(flashcardSet);

        return new FlashcardLessonCreatedResponse(savedLesson.getId(), savedSet.getId());
    }

    @Transactional(readOnly = true)
    public FlashcardSetResponse getSet(UUID setId) {
        FlashcardSet flashcardSet = findSet(setId);

        requireReadableAccess(flashcardSet);

        return toSetResponse(
                flashcardSet,
                findActiveCards(flashcardSet.getId()));
    }

    @Transactional(readOnly = true)
    public FlashcardSetResponse getSetByLesson(UUID lessonReferenceId) {
        FlashcardSet flashcardSet = resolveFlashcardSetByLessonReference(lessonReferenceId);

        requireReadableAccess(flashcardSet);

        return toSetResponse(flashcardSet, findActiveCards(flashcardSet.getId()));
    }

    @Transactional
    public FlashcardSetResponse updateSet(UUID setId, UpdateFlashcardSetRequest request) {
        FlashcardSet flashcardSet = findSet(setId);
        requireUpdatableAccess(flashcardSet);
        if (request.title() != null) {
            String title = normalizeRequired(request.title(), "Flashcard set title is required");
            flashcardSet.setTitle(title);
            updateLinkedLessonTitle(flashcardSet, title);
        }
        if (request.description() != null) {
            flashcardSet.setDescription(normalizeNullable(request.description()));
        }

        FlashcardSet saved = flashcardSetRepository.save(flashcardSet);
        return toSetResponse(saved, findActiveCards(saved.getId()));
    }

    @Transactional
    public void deleteSet(UUID setId) {
        FlashcardSet flashcardSet = findSet(setId);
        requireUpdatableAccess(flashcardSet);
        Instant now = Instant.now();
        flashcardSet.setDeletedAt(now);

        List<FlashcardCard> activeCards = findActiveCards(setId);
        activeCards.forEach(card -> card.setDeletedAt(now));
        flashcardCardRepository.saveAll(activeCards);

        deactivateLinkedLesson(flashcardSet);
        flashcardSetRepository.save(flashcardSet);
    }

    @Transactional
    public FlashcardCardResponse addCard(UUID setId, CreateFlashcardCardRequest request) {
        FlashcardSet flashcardSet = findSet(setId);
        requireUpdatableAccess(flashcardSet);
        FlashcardCard card = new FlashcardCard();
        card.setFlashcardSet(flashcardSet);
        applyCardCreateRequest(card, request);
        card.setOrderIndex(request.orderIndex() == null
                ? flashcardCardRepository.findMaxOrderIndexBySetId(setId) + 1
                : request.orderIndex());

        return toCardResponse(flashcardCardRepository.save(card));
    }

    @Transactional
    public FlashcardCardResponse updateCard(UUID cardId, UpdateFlashcardCardRequest request) {
        FlashcardCard card = findCard(cardId);
        requireUpdatableAccess(card.getFlashcardSet());
        requireActiveSet(card.getFlashcardSet());
        applyCardUpdateRequest(card, request);
        validateCard(card);

        return toCardResponse(flashcardCardRepository.save(card));
    }

    @Transactional
    public void deleteCard(UUID cardId) {
        FlashcardCard card = findCard(cardId);
        requireUpdatableAccess(card.getFlashcardSet());
        requireActiveSet(card.getFlashcardSet());
        card.setDeletedAt(Instant.now());
        flashcardCardRepository.save(card);
    }

    @Transactional
    public FlashcardSetResponse reorderCards(UUID setId, ReorderFlashcardCardsRequest request) {
        FlashcardSet flashcardSet = findSet(setId);
        requireUpdatableAccess(flashcardSet);
        List<FlashcardCard> activeCards = findActiveCards(setId);
        Map<UUID, FlashcardCard> cardsById = activeCards.stream()
                .collect(LinkedHashMap::new, (map, card) -> map.put(card.getId(), card), LinkedHashMap::putAll);
        assertReorderMatchesAllItems(request.ids(), cardsById.keySet());

        int orderIndex = 0;
        for (UUID cardId : request.ids()) {
            cardsById.get(cardId).setOrderIndex(orderIndex);
            orderIndex++;
        }

        flashcardCardRepository.saveAll(activeCards);
        return toSetResponse(flashcardSet, activeCards.stream()
                .sorted(Comparator.comparing(FlashcardCard::getOrderIndex))
                .toList());
    }

    private FlashcardSet resolveFlashcardSetByLessonReference(UUID lessonReferenceId) {
        // Trường hợp 1:
        // ID frontend gửi là legacy lesson ID.
        Optional<FlashcardSet> byLegacyLesson = flashcardSetRepository
                .findByLessonIdAndDeletedAtIsNull(
                        lessonReferenceId);

        if (byLegacyLesson.isPresent()) {
            return byLegacyLesson.get();
        }

        Optional<FlashcardSet> byCurriculumLesson = flashcardSetRepository
                .findByCurriculumLessonIdAndDeletedAtIsNull(
                        lessonReferenceId);

        if (byCurriculumLesson.isPresent()) {
            return byCurriculumLesson.get();
        }
        // Trường hợp 2:
        // ID frontend gửi là curriculum lesson ID, nhưng chưa có flashcard set liên kết
        // trực tiếp.
        CurriculumLesson curriculumLesson = curriculumLessonRepository
                .findById(lessonReferenceId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Flashcard set was not found"));

        UUID sourceLessonId = curriculumLesson.getSourceLessonId();

        if (sourceLessonId != null) {
            // Nếu curriculum lesson có source lesson, thì tìm flashcard set liên kết với
            // source lesson.
            Optional<FlashcardSet> bySourceLesson = flashcardSetRepository
                    .findByLessonIdAndDeletedAtIsNull(
                            sourceLessonId);

            if (bySourceLesson.isPresent()) {
                return bySourceLesson.get();
            }
        }

        // Trường hợp 3:
        // ID frontend gửi là curriculum lesson ID, nhưng chưa có flashcard set liên kết
        // trực tiếp.
        UUID sourceCurriculumLessonId = curriculumLesson.getSourceCurriculumLessonId();

        if (sourceCurriculumLessonId != null) {
            // Nếu curriculum lesson có source curriculum lesson, thì tìm flashcard set liên
            // kết với source curriculum lesson.
            Optional<FlashcardSet> bySourceCurriculumLesson = flashcardSetRepository
                    .findByCurriculumLessonIdAndDeletedAtIsNull(
                            sourceCurriculumLessonId);

            if (bySourceCurriculumLesson.isPresent()) {
                return bySourceCurriculumLesson.get();
            }
        }

        throw new BusinessException(
                ErrorCode.RESOURCE_NOT_FOUND,
                "Flashcard set was not found");
    }

    private void requireReadableAccess(FlashcardSet flashcardSet) {
        Course course = flashcardSet.getCourse();

        if (course == null || course.getDeletedAt() != null) {
            throw new BusinessException(
                    ErrorCode.RESOURCE_NOT_FOUND,
                    "Flashcard set was not found");
        }

        courseAccessService.requireReadableCourse(
                course.getId());
    }

    private void requireUpdatableAccess(FlashcardSet flashcardSet) {
        Course course = flashcardSet.getCourse();

        if (course == null || course.getDeletedAt() != null) {
            throw new BusinessException(
                    ErrorCode.RESOURCE_NOT_FOUND,
                    "Flashcard set was not found");
        }

        courseAccessService.requireUpdatableCourse(
                course.getId());
    }

    private Course findCourse(UUID courseId) {
        return courseRepository.findByIdAndDeletedAtIsNull(courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Course was not found"));
    }

    private CurriculumSection findCurriculumSection(UUID courseId, UUID sectionId) {
        CurriculumSection section = curriculumSectionRepository.findById(sectionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Section was not found"));

        if (!courseId.equals(section.getCurriculumVersion().getCourseId())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Section was not found");
        }

        return section;
    }

    private FlashcardSet findSet(UUID setId) {
        FlashcardSet flashcardSet = flashcardSetRepository.findByIdAndDeletedAtIsNull(setId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Flashcard set was not found"));
        requireLinkedFlashcardLesson(flashcardSet);
        return flashcardSet;
    }

    private FlashcardCard findCard(UUID cardId) {
        return flashcardCardRepository.findByIdAndDeletedAtIsNull(cardId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Flashcard card was not found"));
    }

    private List<FlashcardCard> findActiveCards(UUID setId) {
        return flashcardCardRepository.findActiveBySetIdOrderByOrderIndex(setId);
    }

    private void requireLinkedFlashcardLesson(FlashcardSet flashcardSet) {
        Lesson lesson = flashcardSet.getLesson();
        if (lesson != null) {
            if (lesson.getCourse() == null || lesson.getCourse().getDeletedAt() != null) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Flashcard lesson was not found");
            }
            if (lesson.getType() != LessonType.FLASHCARD) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST, "Flashcard set is not linked to a flashcard lesson");
            }
            return;
        }

        requireLinkedCurriculumLesson(flashcardSet);
    }

    private CurriculumLesson requireLinkedCurriculumLesson(FlashcardSet flashcardSet) {
        UUID curriculumLessonId = flashcardSet.getCurriculumLessonId();
        if (curriculumLessonId == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Flashcard lesson was not found");
        }

        CurriculumLesson lesson = curriculumLessonRepository.findById(curriculumLessonId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Flashcard lesson was not found"));
        if (lesson.getType() != LessonType.FLASHCARD) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Flashcard set is not linked to a flashcard lesson");
        }
        return lesson;
    }

    private void updateLinkedLessonTitle(FlashcardSet flashcardSet, String title) {
        if (flashcardSet.getLesson() != null) {
            Lesson lesson = flashcardSet.getLesson();
            lesson.setTitle(title);
            lessonRepository.save(lesson);
            return;
        }

        CurriculumLesson lesson = requireLinkedCurriculumLesson(flashcardSet);
        lesson.setTitle(title);
        curriculumLessonRepository.save(lesson);
    }

    private void deactivateLinkedLesson(FlashcardSet flashcardSet) {
        if (flashcardSet.getLesson() != null) {
            Lesson lesson = flashcardSet.getLesson();
            lesson.setStatus(LessonStatus.INACTIVE);
            lessonRepository.save(lesson);
            return;
        }

        CurriculumLesson lesson = requireLinkedCurriculumLesson(flashcardSet);
        lesson.setStatus(LessonStatus.INACTIVE);
        lesson.setDeletedAt(Instant.now());
        curriculumLessonRepository.save(lesson);
    }

    private void requireActiveSet(FlashcardSet flashcardSet) {
        if (flashcardSet == null || flashcardSet.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Flashcard set was not found");
        }
        requireLinkedFlashcardLesson(flashcardSet);
    }

    private void applyCardCreateRequest(FlashcardCard card, CreateFlashcardCardRequest request) {
        card.setFrontText(normalizeNullable(request.frontText()));
        card.setFrontImageUrl(normalizeNullable(request.frontImageUrl()));
        card.setBackText(normalizeNullable(request.backText()));
        card.setBackImageUrl(normalizeNullable(request.backImageUrl()));
        card.setHint(normalizeNullable(request.hint()));
        card.setExplanation(normalizeNullable(request.explanation()));
        validateCard(card);
    }

    private void applyCardUpdateRequest(FlashcardCard card, UpdateFlashcardCardRequest request) {
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
    }

    private void validateCard(FlashcardCard card) {
        boolean hasFront = hasText(card.getFrontText()) || hasText(card.getFrontImageUrl());
        boolean hasBack = hasText(card.getBackText()) || hasText(card.getBackImageUrl());
        if (!hasFront && !hasBack) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "At least one side needs text or an image"
            );
        }
    }

    private void assertReorderMatchesAllItems(List<UUID> requestedIds, Set<UUID> existingIds) {
        Set<UUID> uniqueRequestedIds = new HashSet<>(requestedIds);
        if (uniqueRequestedIds.size() != requestedIds.size()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Flashcard reorder list contains duplicate ids");
        }
        if (!uniqueRequestedIds.equals(existingIds)) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Flashcard reorder request must include every active card exactly once");
        }
    }

    private LessonStatus parseLessonStatus(String value, LessonStatus defaultStatus) {
        if (value == null || value.isBlank()) {
            return defaultStatus;
        }
        try {
            return LessonStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "Lesson status must be draft, published, or inactive");
        }
    }

    private FlashcardSetResponse toSetResponse(FlashcardSet flashcardSet, List<FlashcardCard> cards) {
        Lesson legacyLesson = flashcardSet.getLesson();
        if (legacyLesson != null) {
            requireLinkedFlashcardLesson(flashcardSet);
            return new FlashcardSetResponse(
                    flashcardSet.getId(),
                    legacyLesson.getId(),
                    legacyLesson.getCourse().getId(),
                    legacyLesson.getSection().getId(),
                    flashcardSet.getTitle(),
                    flashcardSet.getDescription(),
                    cards.stream().map(this::toCardResponse).toList(),
                    flashcardSet.getCreatedAt(),
                    flashcardSet.getUpdatedAt());
        }

        CurriculumLesson lesson = requireLinkedCurriculumLesson(flashcardSet);
        return new FlashcardSetResponse(
                flashcardSet.getId(),
                lesson.getId(),
                lesson.getSection().getCurriculumVersion().getCourseId(),
                lesson.getSection().getId(),
                flashcardSet.getTitle(),
                flashcardSet.getDescription(),
                cards.stream().map(this::toCardResponse).toList(),
                flashcardSet.getCreatedAt(),
                flashcardSet.getUpdatedAt());
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
                card.getUpdatedAt());
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
