package com.smartlearnly.backend.flashcard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.classroom.entity.ClassOffering;
import com.smartlearnly.backend.classroom.repository.ClassOfferingRepository;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.curriculum.entity.CurriculumLesson;
import com.smartlearnly.backend.curriculum.entity.CurriculumScope;
import com.smartlearnly.backend.curriculum.entity.CurriculumSection;
import com.smartlearnly.backend.curriculum.entity.CurriculumStatus;
import com.smartlearnly.backend.curriculum.entity.CurriculumVersion;
import com.smartlearnly.backend.curriculum.repository.CurriculumLessonRepository;
import com.smartlearnly.backend.curriculum.service.CurriculumResolution;
import com.smartlearnly.backend.curriculum.service.CurriculumResolutionService;
import com.smartlearnly.backend.enrollment.entity.CourseEnrollment;
import com.smartlearnly.backend.enrollment.entity.EnrollmentStatus;
import com.smartlearnly.backend.enrollment.service.EnrollmentAccessService;
import com.smartlearnly.backend.flashcard.dto.FlashcardLearningDtos.FlashcardPracticeSetResponse;
import com.smartlearnly.backend.flashcard.dto.FlashcardLearningDtos.LearningFlashcardSetResponse;
import com.smartlearnly.backend.flashcard.dto.FlashcardLearningDtos.FlashcardProgressRequest;
import com.smartlearnly.backend.flashcard.dto.FlashcardLearningDtos.FlashcardProgressResponse;
import com.smartlearnly.backend.flashcard.entity.FlashcardCard;
import com.smartlearnly.backend.flashcard.entity.FlashcardProgress;
import com.smartlearnly.backend.flashcard.entity.FlashcardSet;
import com.smartlearnly.backend.flashcard.repository.FlashcardCardRepository;
import com.smartlearnly.backend.flashcard.repository.FlashcardProgressRepository;
import com.smartlearnly.backend.flashcard.repository.FlashcardSetRepository;
import com.smartlearnly.backend.flashcard.repository.FlashcardSetRepository.LearningFlashcardSetProjection;
import com.smartlearnly.backend.course.entity.Course;
import com.smartlearnly.backend.learning.lesson.entity.Lesson;
import com.smartlearnly.backend.learning.lesson.entity.LessonStatus;
import com.smartlearnly.backend.learning.lesson.entity.LessonType;
import com.smartlearnly.backend.learning.lesson.repository.LessonRepository;
import com.smartlearnly.backend.learning.module.entity.CourseSection;
import com.smartlearnly.backend.user.entity.UserAccount;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FlashcardLearningServiceTest {
    @Mock
    private LessonRepository lessonRepository;
    @Mock
    private FlashcardSetRepository flashcardSetRepository;
    @Mock
    private FlashcardCardRepository flashcardCardRepository;
    @Mock
    private FlashcardProgressRepository flashcardProgressRepository;
    @Mock
    private EnrollmentAccessService enrollmentAccessService;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private CurriculumLessonRepository curriculumLessonRepository;
    @Mock
    private ClassOfferingRepository classOfferingRepository;
    @Mock
    private CurriculumResolutionService curriculumResolutionService;

    private FlashcardLearningService flashcardLearningService;

    @BeforeEach
    void setUp() {
        flashcardLearningService = new FlashcardLearningService(
                lessonRepository,
                flashcardSetRepository,
                flashcardCardRepository,
                flashcardProgressRepository,
                enrollmentAccessService,
                currentUserService,
                curriculumLessonRepository,
                classOfferingRepository,
                curriculumResolutionService
        );
    }

    @Test
    void listLearningFlashcardsShouldReturnAvailableSetsWithProgressSummary() {
        UUID studentId = UUID.randomUUID();
        UserAccount student = new UserAccount();
        student.setId(studentId);
        LearningFlashcardSetProjection projection = org.mockito.Mockito.mock(LearningFlashcardSetProjection.class);
        UUID courseId = UUID.randomUUID();
        UUID sectionId = UUID.randomUUID();
        UUID lessonId = UUID.randomUUID();
        UUID setId = UUID.randomUUID();
        Instant lastReviewedAt = Instant.now();
        when(projection.getCourseId()).thenReturn(courseId);
        when(projection.getCourseTitle()).thenReturn("Java Foundations");
        when(projection.getCourseSlug()).thenReturn("java-foundations");
        when(projection.getSectionId()).thenReturn(sectionId);
        when(projection.getSectionTitle()).thenReturn("Basics");
        when(projection.getSectionSortOrder()).thenReturn(1);
        when(projection.getLessonId()).thenReturn(lessonId);
        when(projection.getLessonTitle()).thenReturn("Vocabulary");
        when(projection.getLessonSortOrder()).thenReturn(2);
        when(projection.getSetId()).thenReturn(setId);
        when(projection.getSetTitle()).thenReturn("Core Terms");
        when(projection.getCardCount()).thenReturn(5L);
        when(projection.getKnownCount()).thenReturn(2L);
        when(projection.getStillLearningCount()).thenReturn(1L);
        when(projection.getNotStartedCount()).thenReturn(2L);
        when(projection.getLastReviewedAt()).thenReturn(lastReviewedAt);
        when(currentUserService.requireAuthenticatedUser()).thenReturn(student);
        when(flashcardSetRepository.findLearningFlashcardsForStudent(studentId)).thenReturn(List.of(projection));

        List<LearningFlashcardSetResponse> response = flashcardLearningService.listLearningFlashcards();

        assertThat(response).hasSize(1);
        LearningFlashcardSetResponse item = response.get(0);
        assertThat(item.courseId()).isEqualTo(courseId);
        assertThat(item.courseTitle()).isEqualTo("Java Foundations");
        assertThat(item.lessonId()).isEqualTo(lessonId);
        assertThat(item.setId()).isEqualTo(setId);
        assertThat(item.cardCount()).isEqualTo(5);
        assertThat(item.knownCount()).isEqualTo(2);
        assertThat(item.stillLearningCount()).isEqualTo(1);
        assertThat(item.notStartedCount()).isEqualTo(2);
        assertThat(item.lastReviewedAt()).isEqualTo(lastReviewedAt);
    }

    @Test
    void getLessonFlashcardsShouldReturnCardsWithStudentProgress() {
        UUID studentId = UUID.randomUUID();
        UUID classId = UUID.randomUUID();
        UUID lessonReferenceId = UUID.randomUUID();
        UserAccount student = new UserAccount();
        student.setId(studentId);
        FlashcardSet flashcardSet = flashcardSet();
        Course course = flashcardSet.getCourse();
        ClassOffering classOffering = classOffering(classId, course.getId());
        CurriculumVersion version = version(course.getId());
        CurriculumSection section = curriculumSection(version);
        CurriculumLesson lesson = curriculumLesson(section, lessonReferenceId);
        flashcardSet.setCurriculumLessonId(lesson.getId());
        FlashcardCard first = card(flashcardSet, 0);
        FlashcardCard second = card(flashcardSet, 1);
        FlashcardProgress progress = progress(studentId, first);
        progress.setLearningStatus("known");
        progress.setLastReviewResult("known");
        progress.setRepetitions(2);
        progress.setIntervalDays(4);
        when(currentUserService.requireAuthenticatedUser()).thenReturn(student);
        when(classOfferingRepository.findByIdAndDeletedAtIsNull(classId)).thenReturn(Optional.of(classOffering));
        when(curriculumResolutionService.resolveTraineeLearning(course.getId(), classId, studentId))
                .thenReturn(new CurriculumResolution(version, null, classId, false,
                        CurriculumResolutionService.SOURCE_MASTER_INHERITED));
        when(curriculumLessonRepository.findEffectiveLessonReference(version.getId(), lessonReferenceId))
                .thenReturn(Optional.of(lesson));
        when(flashcardSetRepository.findByCurriculumLessonIdAndDeletedAtIsNull(lesson.getId()))
                .thenReturn(Optional.of(flashcardSet));
        when(flashcardCardRepository.findActiveBySetIdOrderByOrderIndex(flashcardSet.getId()))
                .thenReturn(List.of(first, second));
        when(flashcardProgressRepository.findByStudentIdAndCardIds(studentId, List.of(first.getId(), second.getId())))
                .thenReturn(List.of(progress));

        FlashcardPracticeSetResponse response = flashcardLearningService.getLessonFlashcards(lessonReferenceId, classId);

        assertThat(response.id()).isEqualTo(flashcardSet.getId());
        assertThat(response.cards()).hasSize(2);
        assertThat(response.cards().get(0).progress()).isNotNull();
        assertThat(response.cards().get(0).progress().learningStatus()).isEqualTo("known");
        assertThat(response.cards().get(1).progress()).isNull();
    }

    @Test
    void getLessonFlashcardsShouldSupportOnlineCourseWithoutClassId() {
        UUID studentId = UUID.randomUUID();
        UUID lessonReferenceId = UUID.randomUUID();
        UserAccount student = new UserAccount();
        student.setId(studentId);
        FlashcardSet flashcardSet = flashcardSet();
        Course course = flashcardSet.getCourse();
        CurriculumVersion version = version(course.getId());
        CurriculumSection section = curriculumSection(version);
        CurriculumLesson lesson = curriculumLesson(section, lessonReferenceId);
        lesson.setId(lessonReferenceId);
        flashcardSet.setCurriculumLessonId(lesson.getId());
        FlashcardCard card = card(flashcardSet, 0);

        when(currentUserService.requireAuthenticatedUser()).thenReturn(student);
        when(curriculumLessonRepository.findById(lessonReferenceId)).thenReturn(Optional.of(lesson));
        when(curriculumResolutionService.resolveOnlineLearning(course.getId(), studentId))
                .thenReturn(new CurriculumResolution(version, null, null, false,
                        CurriculumResolutionService.SOURCE_MASTER_PUBLIC));
        when(curriculumLessonRepository.findEffectiveLessonReference(version.getId(), lessonReferenceId))
                .thenReturn(Optional.of(lesson));
        when(flashcardSetRepository.findByCurriculumLessonIdAndDeletedAtIsNull(lesson.getId()))
                .thenReturn(Optional.of(flashcardSet));
        when(flashcardCardRepository.findActiveBySetIdOrderByOrderIndex(flashcardSet.getId()))
                .thenReturn(List.of(card));
        when(flashcardProgressRepository.findByStudentIdAndCardIds(studentId, List.of(card.getId())))
                .thenReturn(List.of());

        FlashcardPracticeSetResponse response = flashcardLearningService
                .getLessonFlashcards(lessonReferenceId, null);

        assertThat(response.lessonId()).isEqualTo(lessonReferenceId);
        assertThat(response.courseId()).isEqualTo(course.getId());
        assertThat(response.cards()).hasSize(1);
    }

    @Test
    void submitKnownProgressShouldUpdateExistingProgress() {
        UUID studentId = UUID.randomUUID();
        FlashcardCard card = card(flashcardSet(), 0);
        Lesson lesson = card.getFlashcardSet().getLesson();
        FlashcardProgress progress = progress(studentId, card);
        progress.setRepetitions(1);
        progress.setIntervalDays(2);
        when(flashcardCardRepository.findByIdAndDeletedAtIsNull(card.getId())).thenReturn(Optional.of(card));
        when(enrollmentAccessService.requireCourseAccess(lesson.getCourse().getId())).thenReturn(enrollment(studentId, lesson.getCourse().getId()));
        when(flashcardProgressRepository.findByStudentIdAndCardId(studentId, card.getId())).thenReturn(Optional.of(progress));
        when(flashcardProgressRepository.save(progress)).thenReturn(progress);

        FlashcardProgressResponse response = flashcardLearningService.submitProgress(
                card.getId(),
                new FlashcardProgressRequest("known")
        );

        assertThat(response.cardId()).isEqualTo(card.getId());
        assertThat(response.learningStatus()).isEqualTo("known");
        assertThat(response.lastReviewResult()).isEqualTo("known");
        assertThat(response.repetitions()).isEqualTo(2);
        assertThat(response.intervalDays()).isEqualTo(4);
        assertThat(response.lastReviewedAt()).isNotNull();
        assertThat(response.nextReviewAt()).isAfter(response.lastReviewedAt());
        verify(flashcardProgressRepository).save(progress);
    }

    @Test
    void submitStillLearningProgressShouldCreateProgressWhenMissing() {
        UUID studentId = UUID.randomUUID();
        FlashcardCard card = card(flashcardSet(), 0);
        Lesson lesson = card.getFlashcardSet().getLesson();
        when(flashcardCardRepository.findByIdAndDeletedAtIsNull(card.getId())).thenReturn(Optional.of(card));
        when(enrollmentAccessService.requireCourseAccess(lesson.getCourse().getId())).thenReturn(enrollment(studentId, lesson.getCourse().getId()));
        when(flashcardProgressRepository.findByStudentIdAndCardId(studentId, card.getId())).thenReturn(Optional.empty());
        when(flashcardProgressRepository.save(any(FlashcardProgress.class))).thenAnswer(invocation -> {
            FlashcardProgress saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        FlashcardProgressResponse response = flashcardLearningService.submitProgress(
                card.getId(),
                new FlashcardProgressRequest("still_learning")
        );

        assertThat(response.learningStatus()).isEqualTo("learning");
        assertThat(response.lastReviewResult()).isEqualTo("still_learning");
        assertThat(response.repetitions()).isZero();
        assertThat(response.intervalDays()).isEqualTo(1);
        assertThat(response.lastReviewedAt()).isNotNull();
        assertThat(response.nextReviewAt()).isAfter(response.lastReviewedAt());
    }

    @Test
    void progressUpsertShouldNotCreateDuplicateWhenProgressExists() {
        UUID studentId = UUID.randomUUID();
        FlashcardCard card = card(flashcardSet(), 0);
        Lesson lesson = card.getFlashcardSet().getLesson();
        FlashcardProgress existing = progress(studentId, card);
        existing.setId(UUID.randomUUID());
        when(flashcardCardRepository.findByIdAndDeletedAtIsNull(card.getId())).thenReturn(Optional.of(card));
        when(enrollmentAccessService.requireCourseAccess(lesson.getCourse().getId())).thenReturn(enrollment(studentId, lesson.getCourse().getId()));
        when(flashcardProgressRepository.findByStudentIdAndCardId(studentId, card.getId())).thenReturn(Optional.of(existing));
        when(flashcardProgressRepository.save(existing)).thenReturn(existing);

        flashcardLearningService.submitProgress(card.getId(), new FlashcardProgressRequest("known"));

        verify(flashcardProgressRepository).save(existing);
    }

    private Course course() {
        Course course = new Course();
        course.setId(UUID.randomUUID());
        course.setTitle("Course");
        course.setSlug("course");
        return course;
    }

    private CourseSection section(Course course) {
        CourseSection section = new CourseSection();
        section.setId(UUID.randomUUID());
        section.setCourse(course);
        section.setTitle("Section");
        section.setSortOrder(0);
        return section;
    }

    private CurriculumVersion version(UUID courseId) {
        CurriculumVersion version = new CurriculumVersion();
        version.setId(UUID.randomUUID());
        version.setCourseId(courseId);
        version.setScope(CurriculumScope.MASTER);
        version.setStatus(CurriculumStatus.PUBLISHED);
        version.setVersionNumber(1);
        return version;
    }

    private CurriculumSection curriculumSection(CurriculumVersion version) {
        CurriculumSection section = new CurriculumSection();
        section.setId(UUID.randomUUID());
        section.setCurriculumVersion(version);
        section.setTitle("Section");
        section.setSortOrder(0);
        return section;
    }

    private CurriculumLesson curriculumLesson(CurriculumSection section, UUID lessonReferenceId) {
        CurriculumLesson lesson = new CurriculumLesson();
        lesson.setId(UUID.randomUUID());
        lesson.setSection(section);
        lesson.setLessonIdentityId(lessonReferenceId);
        lesson.setTitle("Flashcards");
        lesson.setType(LessonType.FLASHCARD);
        lesson.setStatus(LessonStatus.PUBLISHED);
        lesson.setPreview(false);
        lesson.setSortOrder(0);
        return lesson;
    }

    private Lesson lesson(Course course, CourseSection section) {
        Lesson lesson = new Lesson();
        lesson.setId(UUID.randomUUID());
        lesson.setCourse(course);
        lesson.setSection(section);
        lesson.setTitle("Flashcards");
        lesson.setType(LessonType.FLASHCARD);
        lesson.setStatus(LessonStatus.PUBLISHED);
        lesson.setPreview(false);
        lesson.setSortOrder(0);
        return lesson;
    }

    private FlashcardSet flashcardSet() {
        Course course = course();
        CourseSection section = section(course);
        FlashcardSet flashcardSet = new FlashcardSet();
        flashcardSet.setId(UUID.randomUUID());
        flashcardSet.setCourse(course);
        flashcardSet.setLesson(lesson(course, section));
        flashcardSet.setTitle("Flashcards");
        flashcardSet.setCreatedAt(Instant.now());
        flashcardSet.setUpdatedAt(Instant.now());
        return flashcardSet;
    }

    private FlashcardCard card(FlashcardSet flashcardSet, int orderIndex) {
        FlashcardCard card = new FlashcardCard();
        card.setId(UUID.randomUUID());
        card.setFlashcardSet(flashcardSet);
        card.setFrontText("Front " + orderIndex);
        card.setBackText("Back " + orderIndex);
        card.setOrderIndex(orderIndex);
        card.setCreatedAt(Instant.now());
        card.setUpdatedAt(Instant.now());
        return card;
    }

    private FlashcardProgress progress(UUID studentId, FlashcardCard card) {
        FlashcardProgress progress = new FlashcardProgress();
        progress.setId(UUID.randomUUID());
        progress.setStudentId(studentId);
        progress.setFlashcard(card);
        progress.setRepetitions(0);
        progress.setIntervalDays(0);
        return progress;
    }

    private CourseEnrollment enrollment(UUID studentId, UUID courseId) {
        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setId(UUID.randomUUID());
        enrollment.setStudentId(studentId);
        enrollment.setCourseId(courseId);
        enrollment.setStatus(EnrollmentStatus.ACTIVE);
        return enrollment;
    }

    private ClassOffering classOffering(UUID classId, UUID courseId) {
        ClassOffering classOffering = new ClassOffering();
        classOffering.setId(classId);
        classOffering.setCourseId(courseId);
        return classOffering;
    }
}
