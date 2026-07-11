package com.smartlearnly.backend.flashcard.service;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.enrollment.entity.CourseEnrollment;
import com.smartlearnly.backend.enrollment.service.EnrollmentAccessService;
import com.smartlearnly.backend.flashcard.dto.FlashcardLearningDtos.FlashcardPracticeCardResponse;
import com.smartlearnly.backend.flashcard.dto.FlashcardLearningDtos.LearningFlashcardSetResponse;
import com.smartlearnly.backend.flashcard.dto.FlashcardLearningDtos.FlashcardPracticeSetResponse;
import com.smartlearnly.backend.flashcard.dto.FlashcardLearningDtos.FlashcardProgressRequest;
import com.smartlearnly.backend.flashcard.dto.FlashcardLearningDtos.FlashcardProgressResponse;
import com.smartlearnly.backend.flashcard.dto.FlashcardLearningDtos.FlashcardProgressSummary;
import com.smartlearnly.backend.flashcard.entity.FlashcardCard;
import com.smartlearnly.backend.flashcard.entity.FlashcardProgress;
import com.smartlearnly.backend.flashcard.entity.FlashcardSet;
import com.smartlearnly.backend.flashcard.repository.FlashcardCardRepository;
import com.smartlearnly.backend.flashcard.repository.FlashcardProgressRepository;
import com.smartlearnly.backend.flashcard.repository.FlashcardSetRepository;
import com.smartlearnly.backend.flashcard.repository.FlashcardSetRepository.LearningFlashcardSetProjection;
import com.smartlearnly.backend.learning.lesson.entity.Lesson;
import com.smartlearnly.backend.learning.lesson.entity.LessonStatus;
import com.smartlearnly.backend.learning.lesson.entity.LessonType;
import com.smartlearnly.backend.learning.lesson.repository.LessonRepository;
import com.smartlearnly.backend.curriculum.entity.CurriculumLesson;
import com.smartlearnly.backend.curriculum.repository.CurriculumLessonRepository;
import com.smartlearnly.backend.classroom.entity.ClassOffering;
import com.smartlearnly.backend.classroom.repository.ClassOfferingRepository;
import com.smartlearnly.backend.curriculum.service.CurriculumResolution;
import com.smartlearnly.backend.curriculum.service.CurriculumResolutionService;
import com.smartlearnly.backend.user.entity.UserAccount;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FlashcardLearningService {
    private static final String RESULT_KNOWN = "known";
    private static final String RESULT_STILL_LEARNING = "still_learning";
    private static final String STATUS_KNOWN = "known";
    private static final String STATUS_LEARNING = "learning";

    private final LessonRepository lessonRepository;
    private final FlashcardSetRepository flashcardSetRepository;
    private final FlashcardCardRepository flashcardCardRepository;
    private final FlashcardProgressRepository flashcardProgressRepository;
    private final EnrollmentAccessService enrollmentAccessService;
    private final CurrentUserService currentUserService;
    private final CurriculumLessonRepository curriculumLessonRepository;
    private final ClassOfferingRepository classOfferingRepository;
    private final CurriculumResolutionService curriculumResolutionService;

    @Transactional(readOnly = true)
    public List<LearningFlashcardSetResponse> listLearningFlashcards() {
        UserAccount student = currentUserService.requireAuthenticatedUser();
        return flashcardSetRepository.findLearningFlashcardsForStudent(student.getId())
                .stream()
                .map(this::toLearningFlashcardSetResponse)
                .toList();
    }

    // @Transactional(readOnly = true)
    // public FlashcardPracticeSetResponse getLessonFlashcards(UUID lessonId) {
    // Lesson lesson = findLesson(lessonId);
    // requireFlashcardLesson(lesson);
    // CourseEnrollment enrollment =
    // enrollmentAccessService.requireCourseAccess(lesson.getCourse().getId());
    // FlashcardSet flashcardSet =
    // flashcardSetRepository.findByLessonIdAndDeletedAtIsNull(lessonId)
    // .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
    // "Flashcard set was not found"));

    // return toPracticeSetResponse(flashcardSet, enrollment.getStudentId());
    // }

    @Transactional(readOnly = true)
    public FlashcardPracticeSetResponse getLessonFlashcards(
            UUID lessonReferenceId,
            UUID classId) {
        UserAccount student = currentUserService.requireAuthenticatedUser();

        ClassOffering classOffering = classOfferingRepository
                .findByIdAndDeletedAtIsNull(classId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Class was not found"));

        UUID courseId = classOffering.getCourseId();

        CurriculumResolution resolution = curriculumResolutionService.resolveTraineeLearning(
                courseId,
                classId,
                student.getId());

        CurriculumLesson curriculumLesson = curriculumLessonRepository
                .findEffectiveLessonReference(
                        resolution.version().getId(),
                        lessonReferenceId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Flashcard lesson was not found in this class curriculum"));

        requireCurriculumFlashcardLesson(curriculumLesson);

        FlashcardSet flashcardSet = findEffectiveFlashcardSet(curriculumLesson);

        return toCurriculumPracticeSetResponse(flashcardSet, curriculumLesson, student.getId());
    }

    @Transactional(readOnly = true)
    public FlashcardPracticeSetResponse getSetPractice(UUID setId) {
        UserAccount student = currentUserService.requireAuthenticatedUser();

        FlashcardSet flashcardSet = findSet(setId);

        if (flashcardSet.getCurriculumLessonId() != null) {
            CurriculumLesson curriculumLesson = curriculumLessonRepository
                    .findById(
                            flashcardSet.getCurriculumLessonId())
                    .orElseThrow(() -> new BusinessException(
                            ErrorCode.RESOURCE_NOT_FOUND,
                            "Flashcard curriculum lesson was not found"));

            UUID courseId = curriculumLesson
                    .getSection()
                    .getCurriculumVersion()
                    .getCourseId();

            enrollmentAccessService.requireCourseAccess(
                    courseId);

            return toCurriculumPracticeSetResponse(
                    flashcardSet,
                    curriculumLesson,
                    student.getId());
        }

        Lesson lesson = requireLinkedFlashcardLesson(
                flashcardSet);

        CourseEnrollment enrollment = enrollmentAccessService.requireCourseAccess(
                lesson.getCourse().getId());

        return toPracticeSetResponse(
                flashcardSet,
                enrollment.getStudentId());
    }

    @Transactional
    public FlashcardProgressResponse submitProgress(
            UUID cardId,
            FlashcardProgressRequest request) {
        FlashcardCard card = findCard(cardId);

        FlashcardSet flashcardSet = card.getFlashcardSet();

        UUID courseId = resolveFlashcardCourseId(flashcardSet);

        CourseEnrollment enrollment = enrollmentAccessService.requireCourseAccess(courseId);

        String result = normalizeResult(request.result());

        FlashcardProgress progress = flashcardProgressRepository
                .findByStudentIdAndCardId(
                        enrollment.getStudentId(),
                        cardId)
                .orElseGet(() -> newProgress(
                        enrollment.getStudentId(),
                        card));

        Instant now = Instant.now();

        if (RESULT_KNOWN.equals(result)) {
            int nextInterval = nextKnownInterval(
                    progress.getIntervalDays());

            progress.setLearningStatus(
                    STATUS_KNOWN);

            progress.setLastReviewResult(
                    RESULT_KNOWN);

            progress.setRepetitions(
                    defaultInt(
                            progress.getRepetitions()) + 1);

            progress.setIntervalDays(
                    nextInterval);

            progress.setLastReviewedAt(
                    now);

            progress.setNextReviewAt(
                    now.plus(
                            nextInterval,
                            ChronoUnit.DAYS));
        } else {
            progress.setLearningStatus(
                    STATUS_LEARNING);

            progress.setLastReviewResult(
                    RESULT_STILL_LEARNING);

            progress.setIntervalDays(
                    1);

            progress.setLastReviewedAt(
                    now);

            progress.setNextReviewAt(
                    now.plus(
                            1,
                            ChronoUnit.DAYS));
        }

        progress.setUpdatedAt(now);

        return toProgressResponse(
                flashcardProgressRepository
                        .save(progress));
    }

    private void requireCurriculumFlashcardLesson(
            CurriculumLesson lesson) {
        if (lesson.getType() != LessonType.FLASHCARD) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Lesson is not a flashcard lesson");
        }

        if (lesson.getStatus() == LessonStatus.INACTIVE) {
            throw new BusinessException(
                    ErrorCode.RESOURCE_NOT_FOUND,
                    "Flashcard lesson was not found");
        }
    }

    private FlashcardSet findEffectiveFlashcardSet(CurriculumLesson curriculumLesson) {
        // 1. Flashcard riêng của class curriculum.
        FlashcardSet customizedSet = flashcardSetRepository
                .findByCurriculumLessonIdAndDeletedAtIsNull(
                        curriculumLesson.getId())
                .orElse(null);

        if (customizedSet != null) {
            return customizedSet;
        }

        // 2. Curriculum đang kế thừa master/legacy flashcard.
        UUID sourceLessonId = curriculumLesson.getSourceLessonId();

        if (sourceLessonId != null) {
            FlashcardSet inheritedSet = flashcardSetRepository
                    .findByLessonIdAndDeletedAtIsNull(
                            sourceLessonId)
                    .orElse(null);

            if (inheritedSet != null) {
                return inheritedSet;
            }
        }

        // 3. lessonIdentityId hiện cũng chính là legacy lesson id
        // đối với dữ liệu master đã migrate của project.
        UUID lessonIdentityId = curriculumLesson.getLessonIdentityId();

        if (lessonIdentityId != null) {
            FlashcardSet identitySet = flashcardSetRepository
                    .findByLessonIdAndDeletedAtIsNull(
                            lessonIdentityId)
                    .orElse(null);

            if (identitySet != null) {
                return identitySet;
            }
        }

        throw new BusinessException(
                ErrorCode.RESOURCE_NOT_FOUND,
                "Flashcard set was not found for this curriculum lesson");
    }

    private FlashcardPracticeSetResponse toCurriculumPracticeSetResponse(
            FlashcardSet flashcardSet,
            CurriculumLesson curriculumLesson,
            UUID studentId) {
        List<FlashcardCard> cards = flashcardCardRepository
                .findActiveBySetIdOrderByOrderIndex(
                        flashcardSet.getId());

        Map<UUID, FlashcardProgress> progressByCardId = findProgressByCardId(
                studentId,
                cards);

        UUID courseId = curriculumLesson
                .getSection()
                .getCurriculumVersion()
                .getCourseId();

        return new FlashcardPracticeSetResponse(
                flashcardSet.getId(),
                curriculumLesson.getId(),
                courseId,
                curriculumLesson.getSection().getId(),
                flashcardSet.getTitle(),
                flashcardSet.getDescription(),
                cards.stream()
                        .map(card -> toPracticeCardResponse(
                                card,
                                progressByCardId.get(
                                        card.getId())))
                        .toList());
    }

    private UUID resolveFlashcardCourseId(FlashcardSet flashcardSet) {
        if (flashcardSet.getCurriculumLessonId() != null) {
            CurriculumLesson curriculumLesson = curriculumLessonRepository
                    .findById(
                            flashcardSet.getCurriculumLessonId())
                    .orElseThrow(() -> new BusinessException(
                            ErrorCode.RESOURCE_NOT_FOUND,
                            "Flashcard curriculum lesson was not found"));

            return curriculumLesson
                    .getSection()
                    .getCurriculumVersion()
                    .getCourseId();
        }

        Lesson lesson = requireLinkedFlashcardLesson(
                flashcardSet);

        return lesson.getCourse().getId();
    }

    private Lesson findLesson(UUID lessonId) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Lesson was not found"));
        if (lesson.getCourse().getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Lesson was not found");
        }
        return lesson;
    }

    private FlashcardSet findSet(UUID setId) {
        return flashcardSetRepository.findByIdAndDeletedAtIsNull(setId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Flashcard set was not found"));
    }

    private FlashcardCard findCard(UUID cardId) {
        FlashcardCard card = flashcardCardRepository.findByIdAndDeletedAtIsNull(cardId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Flashcard card was not found"));
        if (card.getFlashcardSet() == null || card.getFlashcardSet().getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Flashcard card was not found");
        }
        return card;
    }

    private Lesson requireLinkedFlashcardLesson(FlashcardSet flashcardSet) {
        if (flashcardSet == null || flashcardSet.getDeletedAt() != null || flashcardSet.getLesson() == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Flashcard set was not found");
        }
        Lesson lesson = flashcardSet.getLesson();
        requireFlashcardLesson(lesson);
        return lesson;
    }

    private void requireFlashcardLesson(Lesson lesson) {
        if (lesson.getType() != LessonType.FLASHCARD) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Lesson is not a flashcard lesson");
        }
        if (lesson.getStatus() == LessonStatus.INACTIVE) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Lesson was not found");
        }
    }

    private FlashcardPracticeSetResponse toPracticeSetResponse(FlashcardSet flashcardSet, UUID studentId) {
        Lesson lesson = requireLinkedFlashcardLesson(flashcardSet);
        List<FlashcardCard> cards = flashcardCardRepository.findActiveBySetIdOrderByOrderIndex(flashcardSet.getId());
        Map<UUID, FlashcardProgress> progressByCardId = findProgressByCardId(studentId, cards);

        return new FlashcardPracticeSetResponse(
                flashcardSet.getId(),
                lesson.getId(),
                lesson.getCourse().getId(),
                lesson.getSection().getId(),
                flashcardSet.getTitle(),
                flashcardSet.getDescription(),
                cards.stream()
                        .map(card -> toPracticeCardResponse(card, progressByCardId.get(card.getId())))
                        .toList());
    }

    private LearningFlashcardSetResponse toLearningFlashcardSetResponse(LearningFlashcardSetProjection projection) {
        return new LearningFlashcardSetResponse(
                projection.getCourseId(),
                projection.getCourseTitle(),
                projection.getCourseSlug(),
                projection.getSectionId(),
                projection.getSectionTitle(),
                projection.getSectionSortOrder(),
                projection.getLessonId(),
                projection.getLessonTitle(),
                projection.getLessonSortOrder(),
                projection.getSetId(),
                projection.getSetTitle(),
                toInt(projection.getCardCount()),
                toInt(projection.getKnownCount()),
                toInt(projection.getStillLearningCount()),
                toInt(projection.getNotStartedCount()),
                projection.getLastReviewedAt());
    }

    private Map<UUID, FlashcardProgress> findProgressByCardId(UUID studentId, List<FlashcardCard> cards) {
        if (cards.isEmpty()) {
            return Collections.emptyMap();
        }
        List<UUID> cardIds = cards.stream().map(FlashcardCard::getId).toList();
        return flashcardProgressRepository.findByStudentIdAndCardIds(studentId, cardIds)
                .stream()
                .collect(Collectors.toMap(progress -> progress.getFlashcard().getId(), Function.identity()));
    }

    private FlashcardPracticeCardResponse toPracticeCardResponse(FlashcardCard card, FlashcardProgress progress) {
        return new FlashcardPracticeCardResponse(
                card.getId(),
                card.getFlashcardSet().getId(),
                card.getFrontText(),
                card.getFrontImageUrl(),
                card.getBackText(),
                card.getBackImageUrl(),
                card.getHint(),
                card.getExplanation(),
                card.getOrderIndex(),
                progress == null ? null : toProgressSummary(progress));
    }

    private FlashcardProgressSummary toProgressSummary(FlashcardProgress progress) {
        return new FlashcardProgressSummary(
                progress.getLearningStatus(),
                progress.getLastReviewResult(),
                progress.getRepetitions(),
                progress.getIntervalDays(),
                progress.getLastReviewedAt(),
                progress.getNextReviewAt());
    }

    private FlashcardProgressResponse toProgressResponse(FlashcardProgress progress) {
        return new FlashcardProgressResponse(
                progress.getFlashcard().getId(),
                progress.getLearningStatus(),
                progress.getLastReviewResult(),
                progress.getRepetitions(),
                progress.getIntervalDays(),
                progress.getLastReviewedAt(),
                progress.getNextReviewAt());
    }

    private FlashcardProgress newProgress(UUID studentId, FlashcardCard card) {
        FlashcardProgress progress = new FlashcardProgress();
        progress.setStudentId(studentId);
        progress.setFlashcard(card);
        progress.setRepetitions(0);
        progress.setIntervalDays(0);
        return progress;
    }

    private String normalizeResult(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Review result must be known or still_learning");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!RESULT_KNOWN.equals(normalized) && !RESULT_STILL_LEARNING.equals(normalized)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Review result must be known or still_learning");
        }
        return normalized;
    }

    private int nextKnownInterval(Integer currentIntervalDays) {
        int current = defaultInt(currentIntervalDays);
        if (current <= 0) {
            return 1;
        }
        return Math.min(current * 2, 30);
    }

    private int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }

    private int toInt(Long value) {
        return value == null ? 0 : Math.toIntExact(value);
    }
}
