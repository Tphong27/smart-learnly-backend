package com.smartlearnly.backend.learning.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.course.access.service.CourseAccessService;
import com.smartlearnly.backend.course.repository.CourseRepository;
import com.smartlearnly.backend.curriculum.entity.CurriculumLesson;
import com.smartlearnly.backend.curriculum.entity.CurriculumScope;
import com.smartlearnly.backend.curriculum.entity.CurriculumSection;
import com.smartlearnly.backend.curriculum.entity.CurriculumStatus;
import com.smartlearnly.backend.curriculum.entity.CurriculumVersion;
import com.smartlearnly.backend.curriculum.repository.CurriculumLessonRepository;
import com.smartlearnly.backend.curriculum.service.ClassCurriculumCompositionService;
import com.smartlearnly.backend.curriculum.service.CurriculumDtoMapper;
import com.smartlearnly.backend.curriculum.service.CurriculumResolution;
import com.smartlearnly.backend.curriculum.service.CurriculumResolutionService;
import com.smartlearnly.backend.enrollment.repository.ClassEnrollmentRepository;
import com.smartlearnly.backend.enrollment.service.EnrollmentAccessService;
import com.smartlearnly.backend.flashcard.entity.FlashcardCard;
import com.smartlearnly.backend.flashcard.entity.FlashcardProgress;
import com.smartlearnly.backend.flashcard.entity.FlashcardSet;
import com.smartlearnly.backend.flashcard.repository.FlashcardCardRepository;
import com.smartlearnly.backend.flashcard.repository.FlashcardProgressRepository;
import com.smartlearnly.backend.flashcard.repository.FlashcardSetRepository;
import com.smartlearnly.backend.learning.lesson.entity.LessonStatus;
import com.smartlearnly.backend.learning.lesson.entity.LessonType;
import com.smartlearnly.backend.lessonprogress.repository.LessonProgressRepository;
import com.smartlearnly.backend.user.entity.UserAccount;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LearningContentServiceTest {
    @Mock private CourseRepository courseRepository;
    @Mock private CurrentUserService currentUserService;
    @Mock private LessonProgressRepository lessonProgressRepository;
    @Mock private CurriculumResolutionService curriculumResolutionService;
    @Mock private CurriculumDtoMapper curriculumDtoMapper;
    @Mock private CourseAccessService courseAccessService;
    @Mock private EnrollmentAccessService enrollmentAccessService;
    @Mock private CurriculumLessonRepository curriculumLessonRepository;
    @Mock private ClassEnrollmentRepository classEnrollmentRepository;
    @Mock private ClassCurriculumCompositionService compositionService;
    @Mock private FlashcardSetRepository flashcardSetRepository;
    @Mock private FlashcardCardRepository flashcardCardRepository;
    @Mock private FlashcardProgressRepository flashcardProgressRepository;

    private LearningContentService service;

    @BeforeEach
    void setUp() {
        service = new LearningContentService(
                courseRepository,
                currentUserService,
                lessonProgressRepository,
                curriculumResolutionService,
                curriculumDtoMapper,
                courseAccessService,
                enrollmentAccessService,
                curriculumLessonRepository,
                classEnrollmentRepository,
                compositionService,
                flashcardSetRepository,
                flashcardCardRepository,
                flashcardProgressRepository);
    }

    @Test
    void getLearningFlashcardsResolvesSourceLessonSetWithoutClassDuplicate() {
        UUID courseId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID lessonId = UUID.randomUUID();
        UUID sourceLessonId = UUID.randomUUID();
        UUID setId = UUID.randomUUID();

        UserAccount student = new UserAccount();
        student.setId(studentId);
        when(currentUserService.requireAuthenticatedUser()).thenReturn(student);
        when(classEnrollmentRepository.findActiveClassIdsByCourseIdAndStudentId(courseId, studentId))
                .thenReturn(List.of());

        CurriculumVersion version = new CurriculumVersion();
        version.setId(UUID.randomUUID());
        version.setCourseId(courseId);
        version.setScope(CurriculumScope.MASTER);
        version.setStatus(CurriculumStatus.PUBLISHED);
        CurriculumSection section = new CurriculumSection();
        section.setId(UUID.randomUUID());
        version.addSection(section);
        CurriculumLesson lesson = new CurriculumLesson();
        lesson.setId(lessonId);
        lesson.setLessonIdentityId(UUID.randomUUID());
        lesson.setSourceCurriculumLessonId(sourceLessonId);
        lesson.setType(LessonType.FLASHCARD);
        lesson.setStatus(LessonStatus.PUBLISHED);
        section.addLesson(lesson);

        when(curriculumResolutionService.resolveOnlineLearning(courseId, studentId))
                .thenReturn(new CurriculumResolution(version, null, null, false, "master_inherited"));
        when(compositionService.isCompositionVersion(version)).thenReturn(false);
        when(flashcardSetRepository.findByCurriculumLessonIdAndDeletedAtIsNull(lessonId))
                .thenReturn(Optional.empty());

        FlashcardSet sourceSet = new FlashcardSet();
        sourceSet.setId(setId);
        sourceSet.setCurriculumLessonId(sourceLessonId);
        sourceSet.setTitle("Lesson flashcards");
        when(flashcardSetRepository.findByCurriculumLessonIdAndDeletedAtIsNull(sourceLessonId))
                .thenReturn(Optional.of(sourceSet));

        FlashcardCard card = new FlashcardCard();
        card.setId(UUID.randomUUID());
        card.setFlashcardSet(sourceSet);
        card.setFrontText("front");
        card.setBackText("back");
        card.setOrderIndex(0);
        when(flashcardCardRepository.findActiveBySetIdOrderByOrderIndex(setId))
                .thenReturn(List.of(card));
        FlashcardProgress progress = new FlashcardProgress();
        progress.setStudentId(studentId);
        progress.setFlashcard(card);
        progress.setLearningStatus("known");
        progress.setLastReviewResult("known");
        progress.setRepetitions(1);
        progress.setIntervalDays(1);
        when(flashcardProgressRepository.findByStudentIdAndCardIds(studentId, List.of(card.getId())))
                .thenReturn(List.of(progress));

        var response = service.getLearningFlashcards(courseId, null, lessonId);

        assertThat(response.id()).isEqualTo(setId);
        assertThat(response.lessonId()).isEqualTo(lessonId);
        assertThat(response.cards()).hasSize(1);
        assertThat(response.cards().get(0).frontText()).isEqualTo("front");
        assertThat(response.cards().get(0).progress().learningStatus()).isEqualTo("known");
    }
}
