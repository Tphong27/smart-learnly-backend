package com.smartlearnly.backend.learning.content.service;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.course.access.service.CourseAccessService;
import com.smartlearnly.backend.course.entity.Course;
import com.smartlearnly.backend.course.entity.CourseStatus;
import com.smartlearnly.backend.course.repository.CourseRepository;
import com.smartlearnly.backend.course.preview.dto.PreviewTestAnswerResponse;
import com.smartlearnly.backend.course.preview.dto.PreviewTestQuestionResponse;
import com.smartlearnly.backend.test.definition.dto.TestQuestionModel;
import com.smartlearnly.backend.test.definition.service.TestQuestionService;
import com.smartlearnly.backend.curriculum.dto.CurriculumMetadataResponse;
import com.smartlearnly.backend.curriculum.entity.CurriculumLesson;
import com.smartlearnly.backend.curriculum.entity.CurriculumScope;
import com.smartlearnly.backend.curriculum.entity.CurriculumSection;
import com.smartlearnly.backend.curriculum.entity.CurriculumStatus;
import com.smartlearnly.backend.curriculum.entity.CurriculumVersion;
import com.smartlearnly.backend.curriculum.repository.CurriculumLessonRepository;
import com.smartlearnly.backend.curriculum.service.CurriculumDtoMapper;
import com.smartlearnly.backend.curriculum.service.CurriculumResolution;
import com.smartlearnly.backend.curriculum.service.CurriculumResolutionService;
import com.smartlearnly.backend.curriculum.service.ClassCurriculumCompositionService;
import com.smartlearnly.backend.enrollment.entity.CourseEnrollment;
import com.smartlearnly.backend.enrollment.repository.ClassEnrollmentRepository;
import com.smartlearnly.backend.enrollment.service.EnrollmentAccessService;
import com.smartlearnly.backend.flashcard.entity.FlashcardCard;
import com.smartlearnly.backend.flashcard.entity.FlashcardProgress;
import com.smartlearnly.backend.flashcard.entity.FlashcardSet;
import com.smartlearnly.backend.flashcard.repository.FlashcardCardRepository;
import com.smartlearnly.backend.flashcard.repository.FlashcardProgressRepository;
import com.smartlearnly.backend.flashcard.repository.FlashcardSetRepository;
import com.smartlearnly.backend.learning.content.dto.LearningContentResponse;
import com.smartlearnly.backend.learning.content.dto.LearningFlashcardPracticeDtos.FlashcardPracticeCardResponse;
import com.smartlearnly.backend.learning.content.dto.LearningFlashcardPracticeDtos.FlashcardPracticeSetResponse;
import com.smartlearnly.backend.learning.content.dto.LearningFlashcardPracticeDtos.FlashcardProgressRequest;
import com.smartlearnly.backend.learning.content.dto.LearningFlashcardPracticeDtos.FlashcardProgressResponse;
import com.smartlearnly.backend.learning.content.dto.LearningFlashcardPracticeDtos.FlashcardProgressSummary;
import com.smartlearnly.backend.lessonprogress.entity.LessonProgress;
import com.smartlearnly.backend.lessonprogress.repository.LessonProgressRepository;
import com.smartlearnly.backend.lessonprogress.trainee.service.TraineeProgressService;
import com.smartlearnly.backend.learning.lesson.entity.LessonStatus;
import com.smartlearnly.backend.learning.lesson.entity.LessonType;
import com.smartlearnly.backend.user.entity.UserAccount;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LearningContentService {
        private static final String RESULT_KNOWN = "known";
        private static final String RESULT_STILL_LEARNING = "still_learning";
        private static final String STATUS_KNOWN = "known";
        private static final String STATUS_LEARNING = "learning";

        private final CourseRepository courseRepository;
        private final CurrentUserService currentUserService;
        private final LessonProgressRepository lessonProgressRepository;
        private final CurriculumResolutionService curriculumResolutionService;
        private final CurriculumDtoMapper curriculumDtoMapper;
        private final CourseAccessService courseAccessService;
        private final EnrollmentAccessService enrollmentAccessService;
        private final CurriculumLessonRepository curriculumLessonRepository;
        private final ClassEnrollmentRepository classEnrollmentRepository;
        private final ClassCurriculumCompositionService compositionService;
        private final FlashcardSetRepository flashcardSetRepository;
        private final FlashcardCardRepository flashcardCardRepository;
        private final FlashcardProgressRepository flashcardProgressRepository;
        private final TraineeProgressService traineeProgressService;
        private final TestQuestionService testQuestionService;

        /**
         * Tạo nội dung học thật cho học viên sau khi kiểm tra quyền enrollment và scope
         * lớp học.
         */
        @Transactional(readOnly = true)
        public LearningContentResponse getLearningContent(UUID courseId, UUID classId) {
                UserAccount student = currentUserService.requireAuthenticatedUser();

                /*
                 * Khi client không gửi classId, tự động tìm class mà học viên đang theo học
                 * để học viên của lớp nhận đúng curriculum riêng của trainer, thay vì
                 * fallback về master curriculum như học online.
                 */
                UUID effectiveClassId = classId;
                if (effectiveClassId == null) {
                        List<UUID> activeClassIds = classEnrollmentRepository
                                        .findActiveClassIdsByCourseIdAndStudentId(courseId, student.getId());
                        if (!activeClassIds.isEmpty()) {
                                effectiveClassId = activeClassIds.get(0);
                        }
                }

                CurriculumResolution resolution;

                /*
                 * Học online:
                 * - Không thuộc class nào.
                 * - Kiểm tra CourseEnrollment.
                 * - Sử dụng master curriculum của course.
                 */
                if (effectiveClassId == null) {
                        resolution = curriculumResolutionService.resolveOnlineLearning(courseId, student.getId());
                }

                /*
                 * Học theo lớp offline:
                 * - Có classId (từ client hoặc tự resolve ở trên).
                 * - Kiểm tra ClassEnrollment.
                 * - Sử dụng curriculum hiệu lực của class.
                 */
                else {
                        resolution = curriculumResolutionService.resolveClassLearning(courseId, effectiveClassId,
                                        student.getId());
                }

                Course course = courseRepository
                                .findByIdAndDeletedAtIsNull(courseId)
                                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                                                "Course not found"));

                Set<UUID> completedLessonIdentityIds;

                /*
                 * effectiveClassId null nghĩa là học online.
                 * Tạm thời không gọi query progress theo class với giá trị null.
                 */
                if (effectiveClassId == null) {
                        completedLessonIdentityIds = lessonProgressRepository
                                        .findByStudentIdAndCourseIdAndClassIdIsNull(student.getId(), courseId)
                                        .stream()
                                        .filter(LessonProgress::isCompleted)
                                        .map(LessonProgress::getLessonIdentityId)
                                        .filter(Objects::nonNull)
                                        .collect(Collectors.toSet());
                } else {
                        completedLessonIdentityIds = lessonProgressRepository
                                        .findByStudentIdAndClassIdAndCourseId(student.getId(), effectiveClassId,
                                                        courseId)
                                        .stream()
                                        .filter(LessonProgress::isCompleted)
                                        .map(LessonProgress::getLessonIdentityId)
                                        .filter(Objects::nonNull)
                                        .collect(Collectors.toSet());
                }

                CurriculumMetadataResponse metadata = curriculumDtoMapper.toMetadata(
                                resolution.version(),
                                resolution.classId(),
                                resolution.source());
                return curriculumDtoMapper.toLearningContentResponse(
                                resolution.version(),
                                course.getTitle(),
                                course.getThumbnailUrl(),
                                completedLessonIdentityIds,
                                metadata);
        }

        /** Tạo nội dung xem trước công khai chỉ với curriculum đã xuất bản. */
        // @Transactional(readOnly = true)
        // public LearningContentResponse getPreviewContent(UUID courseId) {
        // Course course = courseRepository.findByIdAndDeletedAtIsNull(courseId)
        // .orElseThrow(() -> new RuntimeException("Course not found"));
        // CurriculumResolution resolution =
        // curriculumResolutionService.resolvePublicMaster(courseId);
        // CurriculumMetadataResponse metadata = curriculumDtoMapper.toMetadata(
        // resolution.version(),
        // resolution.classId(),
        // resolution.source());
        // return curriculumDtoMapper.toPreviewLearningContentResponse(
        // resolution.version(),
        // course.getTitle(),
        // course.getThumbnailUrl(),
        // metadata);
        // }

        /**
         * Trả curriculum preview công khai của course hoặc class.
         *
         * Nếu có classId:
         * - Dùng curriculum CLASS đã PUBLISHED nếu trainer đã tùy chỉnh và publish.
         * - Nếu chưa có bản class đã publish thì dùng master curriculum hiện tại.
         *
         * Nếu không có classId:
         * - Dùng published master curriculum của course.
         */
        @Transactional(readOnly = true)
        public LearningContentResponse getPreviewContent(
                        UUID courseId,
                        UUID classId) {

                Course course = courseRepository.findByIdAndDeletedAtIsNull(courseId)
                                .orElseThrow(() -> new BusinessException(
                                                ErrorCode.RESOURCE_NOT_FOUND,
                                                "Course was not found"));

                CurriculumResolution resolution = classId == null
                                ? curriculumResolutionService.resolvePublicMaster(courseId)
                                : curriculumResolutionService.resolveClassEffectivePublished(
                                                courseId,
                                                classId);

                CurriculumMetadataResponse metadata = curriculumDtoMapper.toMetadata(
                                resolution.version(),
                                resolution.classId(),
                                resolution.source());

                return curriculumDtoMapper.toCatalogPreviewLearningContentResponse(
                                resolution.version(),
                                course.getTitle(),
                                course.getThumbnailUrl(),
                                metadata);
        }

        /**
         * Trả danh sách câu hỏi chỉ đọc của một lesson QUIZ được phép preview.
         *
         * Endpoint public chỉ chấp nhận lesson:
         * - Thuộc curriculum published hiệu lực của course/class.
         * - Có status PUBLISHED.
         * - Có isPreview = true.
         * - Có type QUIZ.
         * - Có testId hợp lệ.
         *
         * Response không chứa testId, questionId, answerId hoặc đáp án đúng.
         */
        @Transactional
        public List<PreviewTestQuestionResponse> getPreviewTestQuestions(
                        UUID courseId,
                        UUID classId,
                        UUID lessonId) {

                if (courseId == null || lessonId == null) {
                        throw new BusinessException(
                                        ErrorCode.INVALID_REQUEST,
                                        "Course ID and lesson ID are required");
                }

                Course course = courseRepository
                                .findByIdAndDeletedAtIsNull(courseId)
                                .orElseThrow(() -> new BusinessException(
                                                ErrorCode.RESOURCE_NOT_FOUND,
                                                "Course was not found"));

                if (course.getStatus() != CourseStatus.PUBLISHED) {
                        throw new BusinessException(
                                        ErrorCode.RESOURCE_NOT_FOUND,
                                        "Course was not found");
                }

                CurriculumResolution resolution = classId == null
                                ? curriculumResolutionService.resolvePublicMaster(courseId)
                                : curriculumResolutionService.resolveClassEffectivePublished(
                                                courseId,
                                                classId);

                CurriculumLesson lesson = resolution.version()
                                .getSections()
                                .stream()
                                .flatMap(section -> effectiveLessons(section).stream())
                                .filter(candidate -> lessonMatches(candidate, lessonId))
                                .findFirst()
                                .orElseThrow(() -> new BusinessException(
                                                ErrorCode.RESOURCE_NOT_FOUND,
                                                "Preview test lesson was not found"));

                boolean validPreviewTest = lesson.getStatus() == LessonStatus.PUBLISHED
                                && lesson.getType() == LessonType.QUIZ
                                && Boolean.TRUE.equals(
                                                lesson.getPreview())
                                && lesson.getTestId() != null;

                if (!validPreviewTest) {
                        throw new BusinessException(
                                        ErrorCode.RESOURCE_NOT_FOUND,
                                        "Preview test lesson was not found");
                }

                List<TestQuestionModel.LearnerResponse> learnerQuestions = testQuestionService
                                .getLearnerQuestionsByTest(
                                                lesson.getTestId());

                return learnerQuestions.stream()
                                .map(this::toPreviewTestQuestionResponse)
                                .toList();
        }

        /**
         * Trả câu hỏi chỉ đọc của quiz trong staff preview.
         *
         * Staff được xem curriculum authoring của course draft sau khi quyền đọc course
         * được xác thực; response vẫn loại bỏ ID nội bộ và đáp án đúng như public preview.
         */
        @Transactional
        public List<PreviewTestQuestionResponse> getAdminPreviewTestQuestions(
                        UUID courseId,
                        UUID classId,
                        UUID lessonId) {
                if (courseId == null || lessonId == null) {
                        throw new BusinessException(
                                        ErrorCode.INVALID_REQUEST,
                                        "Course ID and lesson ID are required");
                }

                courseAccessService.requireReadableCourse(courseId);
                Course course = courseRepository
                                .findByIdAndDeletedAtIsNull(courseId)
                                .orElseThrow(() -> new BusinessException(
                                                ErrorCode.RESOURCE_NOT_FOUND,
                                                "Course was not found"));

                CurriculumResolution resolution;
                if (classId != null) {
                        resolution = curriculumResolutionService.resolveClassEffectivePublished(courseId, classId);
                } else if (course.getStatus() == CourseStatus.PUBLISHED) {
                        resolution = curriculumResolutionService.resolvePublicMaster(courseId);
                } else {
                        resolution = curriculumResolutionService.resolveMasterAuthoring(courseId);
                }

                CurriculumLesson lesson = resolution.version()
                                .getSections()
                                .stream()
                                .flatMap(section -> effectiveLessons(section).stream())
                                .filter(candidate -> lessonMatches(candidate, lessonId))
                                .findFirst()
                                .orElseThrow(() -> new BusinessException(
                                                ErrorCode.RESOURCE_NOT_FOUND,
                                                "Preview test lesson was not found"));

                if (lesson.getType() != LessonType.QUIZ || lesson.getTestId() == null) {
                        throw new BusinessException(
                                        ErrorCode.RESOURCE_NOT_FOUND,
                                        "Preview test lesson was not found");
                }

                return testQuestionService.getLearnerQuestionsByTest(lesson.getTestId())
                                .stream()
                                .map(this::toPreviewTestQuestionResponse)
                                .toList();
        }

        /**
         * Trả bộ flashcard chỉ đọc của lesson được phép preview công khai.
         *
         * Không yêu cầu đăng nhập hoặc enrollment.
         * Không trả progress của người dùng.
         */
        @Transactional(readOnly = true)
        public FlashcardPracticeSetResponse getPreviewFlashcards(
                        UUID courseId,
                        UUID classId,
                        UUID lessonId) {

                if (courseId == null || lessonId == null) {
                        throw new BusinessException(
                                        ErrorCode.INVALID_REQUEST,
                                        "Course ID and lesson ID are required");
                }

                Course course = courseRepository
                                .findByIdAndDeletedAtIsNull(courseId)
                                .orElseThrow(() -> new BusinessException(
                                                ErrorCode.RESOURCE_NOT_FOUND,
                                                "Course was not found"));

                if (course.getStatus() != CourseStatus.PUBLISHED) {
                        throw new BusinessException(
                                        ErrorCode.RESOURCE_NOT_FOUND,
                                        "Course was not found");
                }

                CurriculumResolution resolution = classId == null
                                ? curriculumResolutionService.resolvePublicMaster(
                                                courseId)
                                : curriculumResolutionService
                                                .resolveClassEffectivePublished(
                                                                courseId,
                                                                classId);

                CurriculumLesson lesson = resolution.version()
                                .getSections()
                                .stream()
                                .flatMap(section -> effectiveLessons(section).stream())
                                .filter(candidate -> lessonMatches(candidate, lessonId))
                                .findFirst()
                                .orElseThrow(() -> new BusinessException(
                                                ErrorCode.RESOURCE_NOT_FOUND,
                                                "Preview flashcard lesson was not found"));

                boolean validPreviewFlashcard = lesson.getStatus() == LessonStatus.PUBLISHED
                                && lesson.getType() == LessonType.FLASHCARD
                                && Boolean.TRUE.equals(
                                                lesson.getPreview());

                if (!validPreviewFlashcard) {
                        throw new BusinessException(
                                        ErrorCode.RESOURCE_NOT_FOUND,
                                        "Preview flashcard lesson was not found");
                }

                FlashcardSet flashcardSet = resolveFlashcardSet(lesson)
                                .orElseThrow(() -> new BusinessException(
                                                ErrorCode.RESOURCE_NOT_FOUND,
                                                "Preview flashcard set was not found"));

                List<FlashcardCard> cards = flashcardCardRepository
                                .findActiveBySetIdOrderByOrderIndex(
                                                flashcardSet.getId());

                return toPracticeSetResponse(
                                lesson,
                                flashcardSet,
                                cards,

                                // Guest không có progress.
                                null);
        }

        /**
         * Tạo nội dung xem trước cho nhân sự, đúng với phạm vi course hoặc lớp được
         * chọn.
         */
        @Transactional(readOnly = true)
        public LearningContentResponse getAdminPreviewContent(UUID courseId, UUID classId) {
                courseAccessService.requireReadableCourse(courseId);

                Course course = courseRepository
                                .findByIdAndDeletedAtIsNull(courseId)
                                .orElseThrow(() -> new BusinessException(
                                                ErrorCode.RESOURCE_NOT_FOUND,
                                                "Course was not found"));

                CurriculumResolution resolution;
                if (classId != null) {
                        // A class preview must use the same effective published curriculum that
                        // enrolled trainees receive, without requiring an enrollment for staff.
                        resolution = curriculumResolutionService.resolveClassEffectivePublished(courseId, classId);
                } else if (course.getStatus() == CourseStatus.PUBLISHED) {
                        // Keep "View as trainee" truthful for published courses.
                        resolution = curriculumResolutionService.resolvePublicMaster(courseId);
                } else {
                        // Draft courses have no learner-facing published version yet, so retain an
                        // authoring preview and expose that distinction through curriculum.source.
                        resolution = curriculumResolutionService.resolveMasterAuthoring(courseId);
                }

                CurriculumMetadataResponse metadata = curriculumDtoMapper.toMetadata(
                                resolution.version(),
                                resolution.classId(),
                                resolution.source());
                return curriculumDtoMapper.toLearningContentResponse(
                                resolution.version(),
                                course.getTitle(),
                                course.getThumbnailUrl(),
                                Set.of(),
                                metadata);
        }

        @Transactional(readOnly = true)
        public FlashcardPracticeSetResponse getLearningFlashcards(UUID courseId, UUID classId, UUID lessonId) {
                UserAccount student = currentUserService.requireAuthenticatedUser();
                UUID effectiveClassId = resolveEffectiveClassId(courseId, classId, student.getId());
                CurriculumResolution resolution = effectiveClassId == null
                                ? curriculumResolutionService.resolveOnlineLearning(courseId, student.getId())
                                : curriculumResolutionService.resolveClassLearning(courseId, effectiveClassId,
                                                student.getId());

                CurriculumLesson lesson = resolution.version().getSections().stream()
                                .flatMap(section -> effectiveLessons(section).stream())
                                .filter(candidate -> lessonMatches(candidate, lessonId))
                                .findFirst()
                                .orElseThrow(() -> new BusinessException(
                                                ErrorCode.RESOURCE_NOT_FOUND,
                                                "Flashcard lesson was not found"));

                if (lesson.getStatus() != LessonStatus.PUBLISHED || lesson.getType() != LessonType.FLASHCARD) {
                        throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Flashcard lesson was not found");
                }

                FlashcardSet flashcardSet = resolveFlashcardSet(lesson)
                                .orElseThrow(() -> new BusinessException(
                                                ErrorCode.RESOURCE_NOT_FOUND,
                                                "Flashcard set was not found"));
                List<FlashcardCard> cards = flashcardCardRepository
                                .findActiveBySetIdOrderByOrderIndex(flashcardSet.getId());
                return toPracticeSetResponse(lesson, flashcardSet, cards, student.getId());
        }

        /**
         * Lưu kết quả ôn thẻ sau khi xác minh card thuộc curriculum học viên đang được
         * phép học.
         * Học viên lớp dùng class enrollment; học viên online tiếp tục dùng course
         * enrollment.
         */
        @Transactional
        public FlashcardProgressResponse submitFlashcardProgress(
                        UUID cardId,
                        UUID classId,
                        FlashcardProgressRequest request) {
                UserAccount student = currentUserService.requireAuthenticatedUser();
                FlashcardCard card = flashcardCardRepository.findByIdAndDeletedAtIsNull(cardId)
                                .orElseThrow(() -> new BusinessException(
                                                ErrorCode.RESOURCE_NOT_FOUND,
                                                "Flashcard card was not found"));
                FlashcardSet flashcardSet = card.getFlashcardSet();
                if (flashcardSet == null || flashcardSet.getDeletedAt() != null) {
                        throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Flashcard card was not found");
                }

                UUID courseId = resolveFlashcardCourseId(flashcardSet)
                                .orElseThrow(() -> new BusinessException(
                                                ErrorCode.RESOURCE_NOT_FOUND,
                                                "Flashcard set was not found"));
                CurriculumResolution resolution;
                UUID studentId;
                if (classId == null) {
                        CourseEnrollment enrollment = enrollmentAccessService.requireCourseAccess(courseId);
                        studentId = enrollment.getStudentId();
                        resolution = curriculumResolutionService.resolveOnlineLearning(courseId, studentId);
                } else {
                        studentId = student.getId();
                        resolution = curriculumResolutionService.resolveClassLearning(courseId, classId, studentId);
                }
                CurriculumLesson flashcardLesson = requireFlashcardLessonInCurriculum(
                                resolution.version(),
                                flashcardSet.getId());
                String result = normalizeResult(request.result());
                flashcardSetRepository.findByIdAndDeletedAtIsNullForUpdate(flashcardSet.getId())
                                .orElseThrow(() -> new BusinessException(
                                                ErrorCode.RESOURCE_NOT_FOUND,
                                                "Flashcard card was not found"));

                FlashcardProgress progress = flashcardProgressRepository
                                .findByStudentIdAndCardId(studentId, cardId)
                                .orElseGet(() -> newProgress(studentId, card));

                Instant now = Instant.now();
                if (RESULT_KNOWN.equals(result)) {
                        int nextInterval = nextKnownInterval(progress.getIntervalDays());
                        progress.setLearningStatus(STATUS_KNOWN);
                        progress.setLastReviewResult(RESULT_KNOWN);
                        progress.setRepetitions(defaultInt(progress.getRepetitions()) + 1);
                        progress.setIntervalDays(nextInterval);
                        progress.setLastReviewedAt(now);
                        progress.setNextReviewAt(now.plus(nextInterval, ChronoUnit.DAYS));
                } else {
                        progress.setLearningStatus(STATUS_LEARNING);
                        progress.setLastReviewResult(RESULT_STILL_LEARNING);
                        progress.setIntervalDays(1);
                        progress.setLastReviewedAt(now);
                        progress.setNextReviewAt(now.plus(1, ChronoUnit.DAYS));
                }
                progress.setUpdatedAt(now);

                FlashcardProgress savedProgress = flashcardProgressRepository.saveAndFlush(progress);
                boolean lessonCompleted = isFlashcardLessonComplete(studentId, flashcardSet.getId());
                if (lessonCompleted) {
                        traineeProgressService.completeResolvedLesson(studentId, courseId, classId, flashcardLesson);
                } else {
                        lessonCompleted = traineeProgressService.isResolvedLessonCompleted(
                                        studentId,
                                        courseId,
                                        classId,
                                        flashcardLesson);
                }

                return toProgressResponse(savedProgress, lessonCompleted);
        }

        /**
         * Chuyển LearnerResponse sang public preview response.
         * Loại bỏ toàn bộ ID có thể dùng để tạo hoặc nộp attempt.
         */
        private PreviewTestQuestionResponse toPreviewTestQuestionResponse(
                        TestQuestionModel.LearnerResponse question) {

                List<PreviewTestAnswerResponse> answers = question.getAnswers() == null
                                ? List.of()
                                : question.getAnswers()
                                                .stream()
                                                .map(answer -> new PreviewTestAnswerResponse(
                                                                answer.getAnswerText(),
                                                                answer.getDisplayOrder(),
                                                                answer.getMedia() == null
                                                                                ? List.of()
                                                                                : answer.getMedia()))
                                                .toList();

                return new PreviewTestQuestionResponse(
                                question.getOrderIndex(),
                                question.getQuestionText(),
                                question.getImageUrl(),
                                question.getAudioUrl(),
                                question.getQuestionType(),
                                answers);
        }

        /**
         * Chặn việc ghi tiến độ cho set không thuộc curriculum publish hiện hành của
         * học viên.
         */
        private CurriculumLesson requireFlashcardLessonInCurriculum(CurriculumVersion version, UUID flashcardSetId) {
                return version.getSections().stream()
                                .flatMap(section -> effectiveLessons(section).stream())
                                .filter(lesson -> lesson.getStatus() == LessonStatus.PUBLISHED)
                                .filter(lesson -> lesson.getType() == LessonType.FLASHCARD)
                                .filter(lesson -> resolveFlashcardSet(lesson)
                                                .map(set -> flashcardSetId.equals(set.getId()))
                                                .orElse(false))
                                .findFirst()
                                .orElseThrow(() -> new BusinessException(
                                                ErrorCode.RESOURCE_NOT_FOUND,
                                                "Flashcard card was not found"));
        }

        private boolean isFlashcardLessonComplete(UUID studentId, UUID flashcardSetId) {
                long activeCardCount = flashcardCardRepository.countActiveBySetId(flashcardSetId);
                if (activeCardCount <= 0) {
                        return false;
                }
                long progressedActiveCardCount = flashcardProgressRepository
                                .countDistinctProgressedActiveCardsByStudentIdAndSetId(studentId, flashcardSetId);
                return progressedActiveCardCount >= activeCardCount;
        }

        private UUID resolveEffectiveClassId(UUID courseId, UUID classId, UUID studentId) {
                if (classId != null) {
                        return classId;
                }
                List<UUID> activeClassIds = classEnrollmentRepository
                                .findActiveClassIdsByCourseIdAndStudentId(courseId, studentId);
                return activeClassIds.isEmpty() ? null : activeClassIds.get(0);
        }

        private List<CurriculumLesson> effectiveLessons(CurriculumSection section) {
                if (compositionService.isCompositionVersion(section.getCurriculumVersion())) {
                        return compositionService.effectiveLessons(section);
                }
                return section.getLessons();
        }

        private boolean lessonMatches(CurriculumLesson lesson, UUID lessonId) {
                return Objects.equals(lesson.getId(), lessonId)
                                || Objects.equals(lesson.getLessonIdentityId(), lessonId)
                                || Objects.equals(lesson.getSourceCurriculumLessonId(), lessonId)
                                || Objects.equals(lesson.getSourceLessonId(), lessonId);
        }

        /** Tìm bộ thẻ của lesson hiện tại hoặc của lesson nguồn có cùng identity. */
        private Optional<FlashcardSet> resolveFlashcardSet(CurriculumLesson lesson) {
                Optional<FlashcardSet> direct = flashcardSetRepository
                                .findByCurriculumLessonIdAndDeletedAtIsNull(lesson.getId());
                if (direct.isPresent()) {
                        return direct;
                }
                if (lesson.getSourceCurriculumLessonId() != null) {
                        Optional<FlashcardSet> bySourceCurriculumLesson = flashcardSetRepository
                                        .findByCurriculumLessonIdAndDeletedAtIsNull(
                                                        lesson.getSourceCurriculumLessonId());
                        if (bySourceCurriculumLesson.isPresent()) {
                                return bySourceCurriculumLesson;
                        }
                }
                if (lesson.getSourceLessonId() != null) {
                        Optional<FlashcardSet> bySourceLesson = flashcardSetRepository
                                        .findByLessonIdAndDeletedAtIsNull(lesson.getSourceLessonId());
                        if (bySourceLesson.isPresent()) {
                                return bySourceLesson;
                        }
                }
                if (lesson.getLessonIdentityId() != null) {
                        return flashcardSetRepository
                                        .findActiveByLessonIdentityIdAndCurriculumStateOrderByUpdatedAtDesc(
                                                        lesson.getLessonIdentityId(),
                                                        CurriculumScope.MASTER,
                                                        CurriculumStatus.PUBLISHED)
                                        .stream()
                                        .findFirst();
                }
                return Optional.empty();
        }

        private FlashcardPracticeSetResponse toPracticeSetResponse(
                        CurriculumLesson lesson,
                        FlashcardSet flashcardSet,
                        List<FlashcardCard> cards,
                        UUID studentId) {
                CurriculumSection section = lesson.getSection();
                Map<UUID, FlashcardProgress> progressByCardId = studentId == null
                                ? Collections.emptyMap()
                                : findProgressByCardId(
                                                studentId,
                                                cards);
                return new FlashcardPracticeSetResponse(
                                flashcardSet.getId(),
                                lesson.getId(),
                                section == null || section.getCurriculumVersion() == null
                                                ? null
                                                : section.getCurriculumVersion().getCourseId(),
                                section == null ? null : section.getId(),
                                flashcardSet.getTitle(),
                                flashcardSet.getDescription(),
                                cards.stream()
                                                .map(card -> toPracticeCardResponse(card,
                                                                progressByCardId.get(card.getId())))
                                                .toList());
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

        private Map<UUID, FlashcardProgress> findProgressByCardId(UUID studentId, List<FlashcardCard> cards) {
                if (cards.isEmpty()) {
                        return Collections.emptyMap();
                }
                List<UUID> cardIds = cards.stream().map(FlashcardCard::getId).toList();
                return flashcardProgressRepository.findByStudentIdAndCardIds(studentId, cardIds)
                                .stream()
                                .collect(Collectors.toMap(progress -> progress.getFlashcard().getId(),
                                                Function.identity()));
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

        private FlashcardProgressResponse toProgressResponse(FlashcardProgress progress, boolean lessonCompleted) {
                return new FlashcardProgressResponse(
                                progress.getFlashcard().getId(),
                                progress.getLearningStatus(),
                                progress.getLastReviewResult(),
                                progress.getRepetitions(),
                                progress.getIntervalDays(),
                                progress.getLastReviewedAt(),
                                progress.getNextReviewAt(),
                                lessonCompleted);
        }

        private FlashcardProgress newProgress(UUID studentId, FlashcardCard card) {
                FlashcardProgress progress = new FlashcardProgress();
                progress.setStudentId(studentId);
                progress.setFlashcard(card);
                progress.setRepetitions(0);
                progress.setIntervalDays(0);
                return progress;
        }

        private Optional<UUID> resolveFlashcardCourseId(FlashcardSet flashcardSet) {
                if (flashcardSet.getCourse() != null) {
                        return Optional.of(flashcardSet.getCourse().getId());
                }
                if (flashcardSet.getCurriculumLessonId() != null) {
                        return curriculumLessonRepository.findById(flashcardSet.getCurriculumLessonId())
                                        .map(CurriculumLesson::getSection)
                                        .filter(Objects::nonNull)
                                        .map(CurriculumSection::getCurriculumVersion)
                                        .filter(Objects::nonNull)
                                        .map(version -> version.getCourseId());
                }
                if (flashcardSet.getLesson() != null
                                && flashcardSet.getLesson().getCourse() != null
                                && flashcardSet.getLesson().getType() == LessonType.FLASHCARD
                                && flashcardSet.getLesson().getStatus() != LessonStatus.INACTIVE) {
                        return Optional.of(flashcardSet.getLesson().getCourse().getId());
                }
                return Optional.empty();
        }

        private String normalizeResult(String value) {
                if (value == null || value.isBlank()) {
                        throw new BusinessException(ErrorCode.INVALID_REQUEST,
                                        "Review result must be known or still_learning");
                }
                String normalized = value.trim().toLowerCase(Locale.ROOT);
                if (!RESULT_KNOWN.equals(normalized) && !RESULT_STILL_LEARNING.equals(normalized)) {
                        throw new BusinessException(ErrorCode.INVALID_REQUEST,
                                        "Review result must be known or still_learning");
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

}
