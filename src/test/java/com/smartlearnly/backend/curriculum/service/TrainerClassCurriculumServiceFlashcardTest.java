package com.smartlearnly.backend.curriculum.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.classroom.entity.ClassOffering;
import com.smartlearnly.backend.classroom.repository.ClassOfferingRepository;
import com.smartlearnly.backend.common.security.AuthenticatedUserResolver;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.course.entity.Course;
import com.smartlearnly.backend.course.repository.CourseRepository;
import com.smartlearnly.backend.curriculum.dto.LessonRequest;
import com.smartlearnly.backend.curriculum.entity.ClassCurriculumBinding;
import com.smartlearnly.backend.curriculum.entity.ClassCurriculumEntry;
import com.smartlearnly.backend.curriculum.entity.CurriculumLesson;
import com.smartlearnly.backend.curriculum.entity.CurriculumScope;
import com.smartlearnly.backend.curriculum.entity.CurriculumSection;
import com.smartlearnly.backend.curriculum.entity.CurriculumStatus;
import com.smartlearnly.backend.curriculum.entity.CurriculumVersion;
import com.smartlearnly.backend.curriculum.repository.ClassCurriculumBindingRepository;
import com.smartlearnly.backend.curriculum.repository.ClassCurriculumEntryRepository;
import com.smartlearnly.backend.curriculum.repository.CurriculumLessonRepository;
import com.smartlearnly.backend.curriculum.repository.CurriculumSectionRepository;
import com.smartlearnly.backend.curriculum.repository.CurriculumVersionRepository;
import com.smartlearnly.backend.flashcard.entity.FlashcardSet;
import com.smartlearnly.backend.flashcard.repository.FlashcardSetRepository;
import com.smartlearnly.backend.learning.lesson.entity.LessonType;
import com.smartlearnly.backend.learning.lesson.service.QuizContentValidator;
import com.smartlearnly.backend.user.entity.UserAccount;
import com.smartlearnly.backend.videoai.service.VideoSummaryService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TrainerClassCurriculumServiceFlashcardTest {
    @Mock private ClassOfferingRepository classOfferingRepository;
    @Mock private ClassCurriculumBindingRepository bindingRepository;
    @Mock private CurriculumVersionRepository curriculumVersionRepository;
    @Mock private CurriculumSectionRepository sectionRepository;
    @Mock private CurriculumLessonRepository lessonRepository;
    @Mock private ClassCurriculumEntryRepository entryRepository;
    @Mock private CurriculumResolutionService resolutionService;
    @Mock private ClassCurriculumBindingProvisioningService bindingProvisioningService;
    @Mock private ClassCurriculumCompositionService compositionService;
    @Mock private CurriculumDtoMapper mapper;
    @Mock private CurrentUserService currentUserService;
    @Mock private AuthenticatedUserResolver authenticatedUserResolver;
    @Mock private QuizContentValidator quizContentValidator;
    @Mock private VideoSummaryService videoSummaryService;
    @Mock private CurriculumLessonTestLinkService lessonTestLinkService;
    @Mock private CourseRepository courseRepository;
    @Mock private FlashcardSetRepository flashcardSetRepository;

    @InjectMocks
    private TrainerClassCurriculumService service;

    @Test
    void createFlashcardLessonShouldCreateItsSetInTheSameServiceCall() {
        UUID classId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID trainerId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        UUID sectionId = UUID.randomUUID();
        UUID lessonId = UUID.randomUUID();

        UserAccount trainer = new UserAccount();
        trainer.setId(trainerId);
        trainer.setRole("TRAINER");
        ClassOffering classOffering = new ClassOffering();
        classOffering.setId(classId);
        classOffering.setCourseId(courseId);
        classOffering.setTrainerId(trainerId);
        ClassCurriculumBinding binding = new ClassCurriculumBinding();
        binding.setClassId(classId);
        binding.setCourseId(courseId);
        binding.setDraftVersionId(versionId);
        CurriculumVersion draft = new CurriculumVersion();
        draft.setId(versionId);
        draft.setCourseId(courseId);
        draft.setClassId(classId);
        draft.setScope(CurriculumScope.CLASS);
        draft.setStatus(CurriculumStatus.DRAFT);
        CurriculumSection section = new CurriculumSection();
        section.setId(sectionId);
        section.setCurriculumVersion(draft);
        Course course = new Course();
        course.setId(courseId);

        when(currentUserService.requireAuthenticatedUser()).thenReturn(trainer);
        when(classOfferingRepository.findByIdAndDeletedAtIsNull(classId)).thenReturn(Optional.of(classOffering));
        when(authenticatedUserResolver.resolve()).thenReturn(Optional.empty());
        when(bindingRepository.findByClassIdForUpdate(classId)).thenReturn(Optional.of(binding));
        when(curriculumVersionRepository.findById(versionId)).thenReturn(Optional.of(draft));
        when(sectionRepository.findByIdAndCurriculumVersionId(sectionId, versionId)).thenReturn(Optional.of(section));
        when(entryRepository.save(any(ClassCurriculumEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(lessonRepository.save(any(CurriculumLesson.class))).thenAnswer(invocation -> {
            CurriculumLesson lesson = invocation.getArgument(0);
            lesson.setId(lessonId);
            return lesson;
        });
        when(flashcardSetRepository.findByCurriculumLessonIdAndDeletedAtIsNull(lessonId))
                .thenReturn(Optional.empty());
        when(courseRepository.findByIdAndDeletedAtIsNull(courseId)).thenReturn(Optional.of(course));
        when(flashcardSetRepository.save(any(FlashcardSet.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.createLesson(classId, sectionId, new LessonRequest(
                "Class cards",
                "flashcard",
                "flashcard",
                null,
                null,
                null,
                0,
                false,
                "draft",
                List.of(),
                0));

        ArgumentCaptor<FlashcardSet> setCaptor = ArgumentCaptor.forClass(FlashcardSet.class);
        verify(flashcardSetRepository).save(setCaptor.capture());
        assertThat(setCaptor.getValue().getCurriculumLessonId()).isEqualTo(lessonId);
        assertThat(setCaptor.getValue().getCourse()).isSameAs(course);
        assertThat(setCaptor.getValue().getCreatedBy()).isSameAs(trainer);
        assertThat(setCaptor.getValue().getTitle()).isEqualTo("Class cards");
        assertThat(setCaptor.getValue().getIsPublic()).isFalse();
        assertThat(setCaptor.getValue().getIsOfficial()).isFalse();
        verify(lessonTestLinkService).ensureQuizTest(any(CurriculumLesson.class));
        assertThat(setCaptor.getValue().getCurriculumLessonId()).isNotNull();
    }
}
