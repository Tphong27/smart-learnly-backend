package com.smartlearnly.backend.flashcard.learning.service;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.enrollment.entity.CourseEnrollment;
import com.smartlearnly.backend.enrollment.service.EnrollmentAccessService;
import com.smartlearnly.backend.flashcard.learning.dto.FlashcardLearningDtos.FlashcardPracticeCardResponse;
import com.smartlearnly.backend.flashcard.learning.dto.FlashcardLearningDtos.LearningFlashcardSetResponse;
import com.smartlearnly.backend.flashcard.learning.dto.FlashcardLearningDtos.FlashcardPracticeSetResponse;
import com.smartlearnly.backend.flashcard.learning.dto.FlashcardLearningDtos.FlashcardProgressRequest;
import com.smartlearnly.backend.flashcard.learning.dto.FlashcardLearningDtos.FlashcardProgressResponse;
import com.smartlearnly.backend.flashcard.learning.dto.FlashcardLearningDtos.FlashcardProgressSummary;
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
import com.smartlearnly.backend.curriculum.service.ClassCurriculumCompositionService;
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
    private final ClassCurriculumCompositionService compositionService;

    /** Liệt kê các bộ flashcard học viên có thể học cùng tiến độ tổng quan. */
    @Transactional(readOnly = true)
    public List<LearningFlashcardSetResponse> listLearningFlashcards() {
        UserAccount student = currentUserService.requireAuthenticatedUser();
        return flashcardSetRepository.findLearningFlashcardsForStudent(student.getId())
                .stream()
                .map(this::toLearningFlashcardSetResponse)
                .toList();
    }

    /** Lấy flashcard set hiệu lực của một lesson theo curriculum online hoặc lớp. */
    @Transactional(readOnly = true)
    public FlashcardPracticeSetResponse getLessonFlashcards(
            UUID lessonReferenceId,
            UUID classId) {
        UserAccount student = currentUserService.requireAuthenticatedUser();

        UUID courseId;
        CurriculumResolution resolution;
        if (classId == null) {
            CurriculumLesson requestedLesson = curriculumLessonRepository
                    .findById(lessonReferenceId)
                    .orElseThrow(() -> new BusinessException(
                            ErrorCode.RESOURCE_NOT_FOUND,
                            "Flashcard lesson was not found"));
            courseId = requestedLesson.getSection().getCurriculumVersion().getCourseId();
            resolution = curriculumResolutionService.resolveOnlineLearning(
                    courseId,
                    student.getId());
        } else {
            ClassOffering classOffering = classOfferingRepository
                    .findByIdAndDeletedAtIsNull(classId)
                    .orElseThrow(() -> new BusinessException(
                            ErrorCode.RESOURCE_NOT_FOUND,
                            "Class was not found"));
            courseId = classOffering.getCourseId();
            resolution = curriculumResolutionService.resolveTraineeLearning(
                    courseId,
                    classId,
                    student.getId());
        }

        CurriculumLesson curriculumLesson = compositionService
                .resolveEffectiveLesson(resolution.version(), lessonReferenceId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Flashcard lesson was not found in this class curriculum"));

        requireCurriculumFlashcardLesson(curriculumLesson);

        FlashcardSet flashcardSet = findEffectiveFlashcardSet(curriculumLesson);

        return toCurriculumPracticeSetResponse(flashcardSet, curriculumLesson, student.getId());
    }

    /** Lấy flashcard set theo id sau khi kiểm tra enrollment của học viên. */
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

    /** Cập nhật kết quả ôn thẻ và tính lịch xem lại kế tiếp cho học viên. */
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

    /** Bảo đảm curriculum lesson là flashcard đã xuất bản trước khi mở luyện tập. */
    private void requireCurriculumFlashcardLesson(
            CurriculumLesson lesson) {
        if (lesson.getType() != LessonType.FLASHCARD) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Lesson is not a flashcard lesson");
        }

        if (lesson.getStatus() != LessonStatus.PUBLISHED) {
            throw new BusinessException(
                    ErrorCode.RESOURCE_NOT_FOUND,
                    "Flashcard lesson was not found");
        }
    }

    /** Tìm set tùy biến của lớp hoặc set kế thừa phù hợp với curriculum lesson. */
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

    /** Tạo dữ liệu luyện tập cho flashcard set gắn với curriculum lesson. */
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

    /** Xác định course sở hữu flashcard set để kiểm tra quyền enrollment. */
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

    /** Lấy lesson legacy còn hiệu lực hoặc trả lỗi không tìm thấy. */
    private Lesson findLesson(UUID lessonId) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Lesson was not found"));
        if (lesson.getCourse().getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Lesson was not found");
        }
        return lesson;
    }

    /** Lấy flashcard set chưa xóa mềm theo id. */
    private FlashcardSet findSet(UUID setId) {
        return flashcardSetRepository.findByIdAndDeletedAtIsNull(setId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Flashcard set was not found"));
    }

    /** Lấy flashcard card còn hiệu lực và bảo đảm set cha còn tồn tại. */
    private FlashcardCard findCard(UUID cardId) {
        FlashcardCard card = flashcardCardRepository.findByIdAndDeletedAtIsNull(cardId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Flashcard card was not found"));
        if (card.getFlashcardSet() == null || card.getFlashcardSet().getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Flashcard card was not found");
        }
        return card;
    }

    /** Xác thực set legacy vẫn liên kết với lesson flashcard đã xuất bản. */
    private Lesson requireLinkedFlashcardLesson(FlashcardSet flashcardSet) {
        if (flashcardSet == null || flashcardSet.getDeletedAt() != null || flashcardSet.getLesson() == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Flashcard set was not found");
        }
        Lesson lesson = flashcardSet.getLesson();
        requireFlashcardLesson(lesson);
        return lesson;
    }

    /** Xác thực lesson legacy có đúng loại FLASHCARD và đã được xuất bản. */
    private void requireFlashcardLesson(Lesson lesson) {
        if (lesson.getType() != LessonType.FLASHCARD) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Lesson is not a flashcard lesson");
        }
        if (lesson.getStatus() == LessonStatus.INACTIVE) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Lesson was not found");
        }
    }

    /** Tạo dữ liệu luyện tập cho flashcard set legacy gắn trực tiếp với lesson. */
    private FlashcardPracticeSetResponse toPracticeSetResponse(FlashcardSet flashcardSet, UUID studentId) {
        Lesson lesson = requireLinkedFlashcardLesson(flashcardSet);
        List<FlashcardCard> cards = flashcardCardRepository.findActiveBySetIdOrderByOrderIndex(flashcardSet.getId());
        Map<UUID, FlashcardProgress> progressByCardId = findProgressByCardId(studentId, cards);

        return new FlashcardPracticeSetResponse(
                flashcardSet.getId(),
                lesson.getId(),
                lesson.getCourse().getId(),
                lesson.getModule().getId(),
                flashcardSet.getTitle(),
                flashcardSet.getDescription(),
                cards.stream()
                        .map(card -> toPracticeCardResponse(card, progressByCardId.get(card.getId())))
                        .toList());
    }

    /** Chuyển projection repository thành thẻ tổng quan flashcard cho màn danh sách học. */
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

    /** Tải tiến độ theo card id để tạo practice payload mà không truy vấn lặp. */
    private Map<UUID, FlashcardProgress> findProgressByCardId(UUID studentId, List<FlashcardCard> cards) {
        if (cards.isEmpty()) {
            return Collections.emptyMap();
        }
        List<UUID> cardIds = cards.stream().map(FlashcardCard::getId).toList();
        return flashcardProgressRepository.findByStudentIdAndCardIds(studentId, cardIds)
                .stream()
                .collect(Collectors.toMap(progress -> progress.getFlashcard().getId(), Function.identity()));
    }

    /** Chuyển card và tiến độ hiện có thành dữ liệu một thẻ luyện tập. */
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

    /** Chuyển progress entity thành tóm tắt spaced repetition cho client. */
    private FlashcardProgressSummary toProgressSummary(FlashcardProgress progress) {
        return new FlashcardProgressSummary(
                progress.getLearningStatus(),
                progress.getLastReviewResult(),
                progress.getRepetitions(),
                progress.getIntervalDays(),
                progress.getLastReviewedAt(),
                progress.getNextReviewAt());
    }

    /** Chuyển progress đã lưu thành response của thao tác ôn thẻ. */
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

    /** Khởi tạo tiến độ mặc định cho học viên ôn một thẻ lần đầu. */
    private FlashcardProgress newProgress(UUID studentId, FlashcardCard card) {
        FlashcardProgress progress = new FlashcardProgress();
        progress.setStudentId(studentId);
        progress.setFlashcard(card);
        progress.setRepetitions(0);
        progress.setIntervalDays(0);
        return progress;
    }

    /** Chuẩn hóa kết quả ôn thẻ về hai giá trị nghiệp vụ được hỗ trợ. */
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

    /** Tính khoảng lặp tiếp theo khi học viên đã nhớ thẻ. */
    private int nextKnownInterval(Integer currentIntervalDays) {
        int current = defaultInt(currentIntervalDays);
        if (current <= 0) {
            return 1;
        }
        return Math.min(current * 2, 30);
    }

    /** Trả số nguyên mặc định cho thuộc tính nullable của progress. */
    private int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }

    /** Ép số lượng Long từ projection về int an toàn cho DTO. */
    private int toInt(Long value) {
        return value == null ? 0 : Math.toIntExact(value);
    }
}
