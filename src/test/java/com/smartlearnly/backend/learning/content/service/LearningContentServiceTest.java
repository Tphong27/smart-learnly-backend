package com.smartlearnly.backend.learning.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.course.access.service.CourseAccessService;
import com.smartlearnly.backend.course.entity.Course;
import com.smartlearnly.backend.course.entity.CourseStatus;
import com.smartlearnly.backend.course.repository.CourseRepository;
import com.smartlearnly.backend.curriculum.dto.CurriculumMetadataResponse;
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
import com.smartlearnly.backend.learning.content.dto.LearningStats;
import com.smartlearnly.backend.learning.content.dto.LearningFlashcardPracticeDtos.FlashcardProgressRequest;
import com.smartlearnly.backend.learning.lesson.entity.LessonStatus;
import com.smartlearnly.backend.learning.lesson.entity.LessonType;
import com.smartlearnly.backend.lessonprogress.entity.LessonProgress;
import com.smartlearnly.backend.lessonprogress.repository.LessonProgressRepository;
import com.smartlearnly.backend.user.entity.UserAccount;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LearningContentServiceTest {
        @Mock
        private CourseRepository courseRepository;
        @Mock
        private CurrentUserService currentUserService;
        @Mock
        private LessonProgressRepository lessonProgressRepository;
        @Mock
        private CurriculumResolutionService curriculumResolutionService;
        @Mock
        private CurriculumDtoMapper curriculumDtoMapper;
        @Mock
        private CourseAccessService courseAccessService;
        @Mock
        private EnrollmentAccessService enrollmentAccessService;
        @Mock
        private CurriculumLessonRepository curriculumLessonRepository;
        @Mock
        private ClassEnrollmentRepository classEnrollmentRepository;
        @Mock
        private ClassCurriculumCompositionService compositionService;
        @Mock
        private FlashcardSetRepository flashcardSetRepository;
        @Mock
        private FlashcardCardRepository flashcardCardRepository;
        @Mock
        private FlashcardProgressRepository flashcardProgressRepository;

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
        void getLearningContentForOnlineStudentFiltersCompletedLessonIdentities() {
                UUID courseId = UUID.randomUUID();
                UUID studentId = UUID.randomUUID();
                UUID completedIdentityId = UUID.randomUUID();
                UUID incompleteIdentityId = UUID.randomUUID();
                CurriculumVersion version = publishedVersion(courseId);
                CurriculumMetadataResponse metadata = metadata(version, null, "master_inherited");
                LearningContentResponse mappedResponse = learningContentResponse(courseId, metadata);

                when(currentUserService.requireAuthenticatedUser()).thenReturn(authenticatedStudent(studentId));
                when(classEnrollmentRepository.findActiveClassIdsByCourseIdAndStudentId(courseId, studentId))
                                .thenReturn(List.of());
                when(curriculumResolutionService.resolveOnlineLearning(courseId, studentId))
                                .thenReturn(new CurriculumResolution(version, null, null, false, "master_inherited"));
                when(courseRepository.findByIdAndDeletedAtIsNull(courseId))
                                .thenReturn(Optional.of(
                                                course(courseId, "Course title", "thumb.png", CourseStatus.PUBLISHED)));
                when(lessonProgressRepository.findByStudentIdAndCourseIdAndClassIdIsNull(studentId, courseId))
                                .thenReturn(List.of(
                                                lessonProgress(completedIdentityId, true),
                                                lessonProgress(incompleteIdentityId, false),
                                                lessonProgress(null, true)));
                when(curriculumDtoMapper.toMetadata(version, null, "master_inherited")).thenReturn(metadata);
                when(curriculumDtoMapper.toLearningContentResponse(
                                eq(version),
                                eq("Course title"),
                                eq("thumb.png"),
                                any(),
                                eq(metadata)))
                                .thenReturn(mappedResponse);

                LearningContentResponse response = service.getLearningContent(courseId, null);

                assertThat(response).isSameAs(mappedResponse);
                @SuppressWarnings("unchecked")
                ArgumentCaptor<Set<UUID>> completedCaptor = ArgumentCaptor.forClass(Set.class);
                verify(curriculumResolutionService).resolveOnlineLearning(courseId, studentId);
                verify(lessonProgressRepository).findByStudentIdAndCourseIdAndClassIdIsNull(studentId, courseId);
                verify(curriculumDtoMapper).toLearningContentResponse(
                                eq(version),
                                eq("Course title"),
                                eq("thumb.png"),
                                completedCaptor.capture(),
                                eq(metadata));
                assertThat(completedCaptor.getValue()).containsExactly(completedIdentityId);
        }

        @Test
        void getLearningContentAutoResolvesActiveClassAndUsesClassProgress() {
                UUID courseId = UUID.randomUUID();
                UUID studentId = UUID.randomUUID();
                UUID classId = UUID.randomUUID();
                UUID completedIdentityId = UUID.randomUUID();
                CurriculumVersion version = publishedVersion(courseId);
                CurriculumMetadataResponse metadata = metadata(version, classId, "class_effective");
                LearningContentResponse mappedResponse = learningContentResponse(courseId, metadata);

                when(currentUserService.requireAuthenticatedUser()).thenReturn(authenticatedStudent(studentId));
                when(classEnrollmentRepository.findActiveClassIdsByCourseIdAndStudentId(courseId, studentId))
                                .thenReturn(List.of(classId));
                when(curriculumResolutionService.resolveClassLearning(courseId, classId, studentId))
                                .thenReturn(new CurriculumResolution(version, null, classId, true, "class_effective"));
                when(courseRepository.findByIdAndDeletedAtIsNull(courseId))
                                .thenReturn(Optional.of(
                                                course(courseId, "Class course", "class.png", CourseStatus.PUBLISHED)));
                when(lessonProgressRepository.findByStudentIdAndClassIdAndCourseId(studentId, classId, courseId))
                                .thenReturn(List.of(lessonProgress(completedIdentityId, true)));
                when(curriculumDtoMapper.toMetadata(version, classId, "class_effective")).thenReturn(metadata);
                when(curriculumDtoMapper.toLearningContentResponse(
                                eq(version),
                                eq("Class course"),
                                eq("class.png"),
                                any(),
                                eq(metadata)))
                                .thenReturn(mappedResponse);

                LearningContentResponse response = service.getLearningContent(courseId, null);

                assertThat(response).isSameAs(mappedResponse);
                @SuppressWarnings("unchecked")
                ArgumentCaptor<Set<UUID>> completedCaptor = ArgumentCaptor.forClass(Set.class);
                verify(curriculumResolutionService).resolveClassLearning(courseId, classId, studentId);
                verify(lessonProgressRepository).findByStudentIdAndClassIdAndCourseId(studentId, classId, courseId);
                verify(lessonProgressRepository, never()).findByStudentIdAndCourseIdAndClassIdIsNull(studentId,
                                courseId);
                verify(curriculumDtoMapper).toLearningContentResponse(
                                eq(version),
                                eq("Class course"),
                                eq("class.png"),
                                completedCaptor.capture(),
                                eq(metadata));
                assertThat(completedCaptor.getValue()).containsExactly(completedIdentityId);
        }

        @Test
        void getLearningContentThrowsWhenCourseIsNotFound() {
                UUID courseId = UUID.randomUUID();
                UUID studentId = UUID.randomUUID();
                CurriculumVersion version = publishedVersion(courseId);

                when(currentUserService.requireAuthenticatedUser()).thenReturn(authenticatedStudent(studentId));
                when(classEnrollmentRepository.findActiveClassIdsByCourseIdAndStudentId(courseId, studentId))
                                .thenReturn(List.of());
                when(curriculumResolutionService.resolveOnlineLearning(courseId, studentId))
                                .thenReturn(new CurriculumResolution(version, null, null, false, "master_inherited"));
                when(courseRepository.findByIdAndDeletedAtIsNull(courseId)).thenReturn(Optional.empty());

                assertThatThrownBy(() -> service.getLearningContent(courseId, null))
                                .isInstanceOfSatisfying(BusinessException.class,
                                                exception -> assertThat(exception.errorCode())
                                                                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND))
                                .hasMessage("Course not found");

                verifyNoInteractions(lessonProgressRepository, curriculumDtoMapper);
        }

        @Test
        void getPreviewContentUsesPublicMasterCurriculum() {
                UUID courseId = UUID.randomUUID();
                CurriculumVersion version = publishedVersion(courseId);
                CurriculumMetadataResponse metadata = metadata(version, null, "public_master");
                LearningContentResponse mappedResponse = learningContentResponse(courseId, metadata);

                when(courseRepository.findByIdAndDeletedAtIsNull(courseId))
                                .thenReturn(Optional.of(course(courseId, "Preview course", "preview.png",
                                                CourseStatus.PUBLISHED)));
                when(curriculumResolutionService.resolvePublicMaster(courseId))
                                .thenReturn(new CurriculumResolution(version, null, null, false, "public_master"));
                when(curriculumDtoMapper.toMetadata(version, null, "public_master")).thenReturn(metadata);
                when(curriculumDtoMapper.toPreviewLearningContentResponse(version, "Preview course", "preview.png",
                                metadata))
                                .thenReturn(mappedResponse);

                LearningContentResponse response = service.getPreviewContent(courseId);

                assertThat(response).isSameAs(mappedResponse);
                verify(curriculumResolutionService).resolvePublicMaster(courseId);
                verify(curriculumDtoMapper).toPreviewLearningContentResponse(version, "Preview course", "preview.png",
                                metadata);
        }

        @Test
        void getAdminPreviewContentForClassUsesEffectivePublishedCurriculumAfterAccessCheck() {
                UUID courseId = UUID.randomUUID();
                UUID classId = UUID.randomUUID();
                CurriculumVersion version = publishedVersion(courseId);
                CurriculumMetadataResponse metadata = metadata(version, classId, "class_effective");
                LearningContentResponse mappedResponse = learningContentResponse(courseId, metadata);

                when(courseRepository.findByIdAndDeletedAtIsNull(courseId))
                                .thenReturn(Optional.of(course(courseId, "Class preview", "class-preview.png",
                                                CourseStatus.DRAFT)));
                when(curriculumResolutionService.resolveClassEffectivePublished(courseId, classId))
                                .thenReturn(new CurriculumResolution(version, null, classId, true, "class_effective"));
                when(curriculumDtoMapper.toMetadata(version, classId, "class_effective")).thenReturn(metadata);
                when(curriculumDtoMapper.toLearningContentResponse(version, "Class preview", "class-preview.png",
                                Set.of(), metadata))
                                .thenReturn(mappedResponse);

                LearningContentResponse response = service.getAdminPreviewContent(courseId, classId);

                assertThat(response).isSameAs(mappedResponse);
                verify(courseAccessService).requireReadableCourse(courseId);
                verify(curriculumResolutionService).resolveClassEffectivePublished(courseId, classId);
                verify(curriculumResolutionService, never()).resolvePublicMaster(courseId);
                verify(curriculumResolutionService, never()).resolveMasterAuthoring(courseId);
        }

        @Test
        void getAdminPreviewContentForDraftCourseUsesAuthoringCurriculum() {
                UUID courseId = UUID.randomUUID();
                CurriculumVersion version = publishedVersion(courseId);
                CurriculumMetadataResponse metadata = metadata(version, null, "master_authoring");
                LearningContentResponse mappedResponse = learningContentResponse(courseId, metadata);

                when(courseRepository.findByIdAndDeletedAtIsNull(courseId))
                                .thenReturn(Optional.of(
                                                course(courseId, "Draft preview", "draft.png", CourseStatus.DRAFT)));
                when(curriculumResolutionService.resolveMasterAuthoring(courseId))
                                .thenReturn(new CurriculumResolution(version, null, null, false, "master_authoring"));
                when(curriculumDtoMapper.toMetadata(version, null, "master_authoring")).thenReturn(metadata);
                when(curriculumDtoMapper.toLearningContentResponse(version, "Draft preview", "draft.png", Set.of(),
                                metadata))
                                .thenReturn(mappedResponse);

                LearningContentResponse response = service.getAdminPreviewContent(courseId, null);

                assertThat(response).isSameAs(mappedResponse);
                verify(courseAccessService).requireReadableCourse(courseId);
                verify(curriculumResolutionService).resolveMasterAuthoring(courseId);
                verify(curriculumResolutionService, never()).resolveClassEffectivePublished(any(), any());
                verify(curriculumResolutionService, never()).resolvePublicMaster(courseId);
        }

        @Test
        void getLearningFlashcardsRejectsNonPublishedFlashcardLesson() {
                UUID courseId = UUID.randomUUID();
                UUID studentId = UUID.randomUUID();
                UUID lessonId = UUID.randomUUID();
                CurriculumVersion version = publishedVersion(courseId);
                CurriculumSection section = sectionIn(version);
                CurriculumLesson lesson = flashcardLessonIn(section, lessonId);
                lesson.setStatus(LessonStatus.DRAFT);

                when(currentUserService.requireAuthenticatedUser()).thenReturn(authenticatedStudent(studentId));
                when(classEnrollmentRepository.findActiveClassIdsByCourseIdAndStudentId(courseId, studentId))
                                .thenReturn(List.of());
                when(curriculumResolutionService.resolveOnlineLearning(courseId, studentId))
                                .thenReturn(new CurriculumResolution(version, null, null, false, "master_inherited"));
                when(compositionService.isCompositionVersion(version)).thenReturn(false);

                assertThatThrownBy(() -> service.getLearningFlashcards(courseId, null, lessonId))
                                .isInstanceOfSatisfying(BusinessException.class,
                                                exception -> assertThat(exception.errorCode())
                                                                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND))
                                .hasMessage("Flashcard lesson was not found");

                verifyNoInteractions(flashcardSetRepository, flashcardCardRepository, flashcardProgressRepository);
        }

        @Test
        void getLearningFlashcardsUsesCompositionEffectiveLessonsAndExplicitClass() {
                UUID courseId = UUID.randomUUID();
                UUID studentId = UUID.randomUUID();
                UUID classId = UUID.randomUUID();
                UUID searchedIdentityId = UUID.randomUUID();
                UUID lessonId = UUID.randomUUID();
                UUID setId = UUID.randomUUID();
                CurriculumVersion version = publishedVersion(courseId);
                CurriculumSection section = sectionIn(version);
                CurriculumLesson storedLesson = flashcardLessonIn(section, UUID.randomUUID());
                CurriculumLesson effectiveLesson = flashcardLessonIn(section, lessonId);
                effectiveLesson.setLessonIdentityId(searchedIdentityId);

                when(currentUserService.requireAuthenticatedUser()).thenReturn(authenticatedStudent(studentId));
                when(curriculumResolutionService.resolveClassLearning(courseId, classId, studentId))
                                .thenReturn(new CurriculumResolution(version, null, classId, true, "class_effective"));
                when(compositionService.isCompositionVersion(version)).thenReturn(true);
                when(compositionService.effectiveLessons(section)).thenReturn(List.of(effectiveLesson));
                FlashcardSet flashcardSet = flashcardSet(setId, lessonId, "Class set");
                when(flashcardSetRepository.findByCurriculumLessonIdAndDeletedAtIsNull(lessonId))
                                .thenReturn(Optional.of(flashcardSet));
                when(flashcardCardRepository.findActiveBySetIdOrderByOrderIndex(setId)).thenReturn(List.of());

                var response = service.getLearningFlashcards(courseId, classId, searchedIdentityId);

                assertThat(response.id()).isEqualTo(setId);
                assertThat(response.lessonId()).isEqualTo(lessonId);
                assertThat(response.cards()).isEmpty();
                verify(curriculumResolutionService).resolveClassLearning(courseId, classId, studentId);
                verify(classEnrollmentRepository, never()).findActiveClassIdsByCourseIdAndStudentId(courseId,
                                studentId);
                verify(compositionService).effectiveLessons(section);
                verify(flashcardSetRepository, never())
                                .findByCurriculumLessonIdAndDeletedAtIsNull(storedLesson.getId());
        }

        @Test
        void submitFlashcardProgressResolvesCourseFromCurriculumLesson() {
                UUID courseId = UUID.randomUUID();
                UUID studentId = UUID.randomUUID();
                UUID cardId = UUID.randomUUID();
                UUID curriculumLessonId = UUID.randomUUID();
                CurriculumVersion version = publishedVersion(courseId);
                CurriculumSection section = sectionIn(version);
                CurriculumLesson lesson = flashcardLessonIn(section, curriculumLessonId);
                FlashcardSet flashcardSet = flashcardSet(UUID.randomUUID(), curriculumLessonId, "Curriculum set");
                FlashcardCard card = flashcardCard(cardId, flashcardSet, "front", "back", 0);

                when(flashcardCardRepository.findByIdAndDeletedAtIsNull(cardId)).thenReturn(Optional.of(card));
                when(curriculumLessonRepository.findById(curriculumLessonId)).thenReturn(Optional.of(lesson));
                when(enrollmentAccessService.requireCourseAccess(courseId)).thenReturn(enrollment(courseId, studentId));
                when(flashcardProgressRepository.findByStudentIdAndCardId(studentId, cardId))
                                .thenReturn(Optional.empty());
                when(flashcardProgressRepository.save(any(FlashcardProgress.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));

                var response = service.submitFlashcardProgress(cardId, null, new FlashcardProgressRequest("known"));

                assertThat(response.cardId()).isEqualTo(cardId);
                assertThat(response.learningStatus()).isEqualTo("known");
                assertThat(response.repetitions()).isEqualTo(1);
                assertThat(response.intervalDays()).isEqualTo(1);
                verify(curriculumLessonRepository).findById(curriculumLessonId);
                verify(enrollmentAccessService).requireCourseAccess(courseId);
        }

        @Test
        void submitFlashcardProgressRejectsCardWithDeletedSet() {
                UUID cardId = UUID.randomUUID();
                FlashcardSet flashcardSet = flashcardSet(UUID.randomUUID(), UUID.randomUUID(), "Deleted set");
                flashcardSet.setDeletedAt(java.time.Instant.now());
                FlashcardCard card = flashcardCard(cardId, flashcardSet, "front", "back", 0);

                when(flashcardCardRepository.findByIdAndDeletedAtIsNull(cardId)).thenReturn(Optional.of(card));

                assertThatThrownBy(() -> service.submitFlashcardProgress(cardId, null,
                                new FlashcardProgressRequest("known")))
                                .isInstanceOfSatisfying(BusinessException.class,
                                                exception -> assertThat(exception.errorCode())
                                                                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND))
                                .hasMessage("Flashcard card was not found");

                verifyNoInteractions(enrollmentAccessService, flashcardProgressRepository);
        }

        @Test
        void submitFlashcardProgressRejectsSetWithoutResolvableCourse() {
                UUID cardId = UUID.randomUUID();
                UUID curriculumLessonId = UUID.randomUUID();
                FlashcardSet flashcardSet = flashcardSet(UUID.randomUUID(), curriculumLessonId, "Orphan set");
                FlashcardCard card = flashcardCard(cardId, flashcardSet, "front", "back", 0);

                when(flashcardCardRepository.findByIdAndDeletedAtIsNull(cardId)).thenReturn(Optional.of(card));
                when(curriculumLessonRepository.findById(curriculumLessonId)).thenReturn(Optional.empty());

                assertThatThrownBy(() -> service.submitFlashcardProgress(cardId, null,
                                new FlashcardProgressRequest("known")))
                                .isInstanceOfSatisfying(BusinessException.class,
                                                exception -> assertThat(exception.errorCode())
                                                                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND))
                                .hasMessage("Flashcard set was not found");

                verify(curriculumLessonRepository).findById(curriculumLessonId);
                verifyNoInteractions(enrollmentAccessService, flashcardProgressRepository);
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

        @Test
        void getLearningFlashcardsUsesDirectLessonFlashcardSet() {
                UUID courseId = UUID.randomUUID();
                UUID studentId = UUID.randomUUID();
                UUID lessonId = UUID.randomUUID();
                UUID sourceCurriculumLessonId = UUID.randomUUID();
                UUID sourceLessonId = UUID.randomUUID();
                UUID setId = UUID.randomUUID();

                UserAccount student = authenticatedStudent(studentId);
                when(currentUserService.requireAuthenticatedUser()).thenReturn(student);
                when(classEnrollmentRepository.findActiveClassIdsByCourseIdAndStudentId(courseId, studentId))
                                .thenReturn(List.of());

                CurriculumVersion version = publishedVersion(courseId);
                CurriculumSection section = sectionIn(version);
                CurriculumLesson lesson = flashcardLessonIn(section, lessonId);
                lesson.setSourceCurriculumLessonId(sourceCurriculumLessonId);
                lesson.setSourceLessonId(sourceLessonId);

                when(curriculumResolutionService.resolveOnlineLearning(courseId, studentId))
                                .thenReturn(new CurriculumResolution(version, null, null, false, "master_inherited"));
                when(compositionService.isCompositionVersion(version)).thenReturn(false);

                FlashcardSet directSet = flashcardSet(setId, lessonId, "Direct flashcards");
                when(flashcardSetRepository.findByCurriculumLessonIdAndDeletedAtIsNull(lessonId))
                                .thenReturn(Optional.of(directSet));

                FlashcardCard card = flashcardCard(UUID.randomUUID(), directSet, "Question", "Answer", 2);
                when(flashcardCardRepository.findActiveBySetIdOrderByOrderIndex(setId))
                                .thenReturn(List.of(card));
                when(flashcardProgressRepository.findByStudentIdAndCardIds(studentId, List.of(card.getId())))
                                .thenReturn(List.of());

                var response = service.getLearningFlashcards(courseId, null, lessonId);

                assertThat(response.id()).isEqualTo(setId);
                assertThat(response.lessonId()).isEqualTo(lessonId);
                assertThat(response.courseId()).isEqualTo(courseId);
                assertThat(response.sectionId()).isEqualTo(section.getId());
                assertThat(response.title()).isEqualTo("Direct flashcards");
                assertThat(response.cards()).hasSize(1);
                assertThat(response.cards().get(0).id()).isEqualTo(card.getId());
                assertThat(response.cards().get(0).setId()).isEqualTo(setId);
                assertThat(response.cards().get(0).frontText()).isEqualTo("Question");
                assertThat(response.cards().get(0).backText()).isEqualTo("Answer");
                assertThat(response.cards().get(0).orderIndex()).isEqualTo(2);
                assertThat(response.cards().get(0).progress()).isNull();

                verify(flashcardSetRepository).findByCurriculumLessonIdAndDeletedAtIsNull(lessonId);
                verify(flashcardSetRepository, never())
                                .findByCurriculumLessonIdAndDeletedAtIsNull(sourceCurriculumLessonId);
                verify(flashcardSetRepository, never()).findByLessonIdAndDeletedAtIsNull(sourceLessonId);
                verify(flashcardCardRepository).findActiveBySetIdOrderByOrderIndex(setId);
                verify(flashcardProgressRepository).findByStudentIdAndCardIds(studentId, List.of(card.getId()));
        }

        @Test
        void getLearningFlashcardsWithEmptyCardSetReturnsEmptyCardsAndDoesNotQueryProgress() {
                UUID courseId = UUID.randomUUID();
                UUID studentId = UUID.randomUUID();
                UUID lessonId = UUID.randomUUID();
                UUID setId = UUID.randomUUID();

                UserAccount student = authenticatedStudent(studentId);
                when(currentUserService.requireAuthenticatedUser()).thenReturn(student);
                when(classEnrollmentRepository.findActiveClassIdsByCourseIdAndStudentId(courseId, studentId))
                                .thenReturn(List.of());

                CurriculumVersion version = publishedVersion(courseId);
                CurriculumSection section = sectionIn(version);
                flashcardLessonIn(section, lessonId);

                when(curriculumResolutionService.resolveOnlineLearning(courseId, studentId))
                                .thenReturn(new CurriculumResolution(version, null, null, false, "master_inherited"));
                when(compositionService.isCompositionVersion(version)).thenReturn(false);

                FlashcardSet flashcardSet = flashcardSet(setId, lessonId, "Empty set");
                when(flashcardSetRepository.findByCurriculumLessonIdAndDeletedAtIsNull(lessonId))
                                .thenReturn(Optional.of(flashcardSet));
                when(flashcardCardRepository.findActiveBySetIdOrderByOrderIndex(setId))
                                .thenReturn(List.of());

                var response = service.getLearningFlashcards(courseId, null, lessonId);

                assertThat(response.id()).isEqualTo(setId);
                assertThat(response.lessonId()).isEqualTo(lessonId);
                assertThat(response.courseId()).isEqualTo(courseId);
                assertThat(response.sectionId()).isEqualTo(section.getId());
                assertThat(response.title()).isEqualTo("Empty set");
                assertThat(response.cards()).isEmpty();

                verify(flashcardCardRepository).findActiveBySetIdOrderByOrderIndex(setId);
                verifyNoInteractions(flashcardProgressRepository);
        }

        @Test
        void submitFlashcardProgressWithKnownUpdatesExistingProgress() {
                UUID courseId = UUID.randomUUID();
                UUID studentId = UUID.randomUUID();
                UUID cardId = UUID.randomUUID();
                FlashcardSet flashcardSet = flashcardSet(UUID.randomUUID(), UUID.randomUUID(), "Course set");
                flashcardSet.setCourse(course(courseId));
                FlashcardCard card = flashcardCard(cardId, flashcardSet, "front", "back", 0);
                FlashcardProgress progress = progress(studentId, card, "learning", "still_learning", 3, 2);
                CourseEnrollment enrollment = enrollment(courseId, studentId);

                when(flashcardCardRepository.findByIdAndDeletedAtIsNull(cardId)).thenReturn(Optional.of(card));
                when(enrollmentAccessService.requireCourseAccess(courseId)).thenReturn(enrollment);
                when(flashcardProgressRepository.findByStudentIdAndCardId(studentId, cardId))
                                .thenReturn(Optional.of(progress));
                when(flashcardProgressRepository.save(any(FlashcardProgress.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));

                var response = service.submitFlashcardProgress(cardId, null, new FlashcardProgressRequest(" known "));

                assertThat(response.cardId()).isEqualTo(cardId);
                assertThat(response.learningStatus()).isEqualTo("known");
                assertThat(response.lastReviewResult()).isEqualTo("known");
                assertThat(response.repetitions()).isEqualTo(4);
                assertThat(response.intervalDays()).isEqualTo(4);
                assertThat(response.lastReviewedAt()).isNotNull();
                assertThat(response.nextReviewAt()).isEqualTo(response.lastReviewedAt().plus(4, ChronoUnit.DAYS));

                ArgumentCaptor<FlashcardProgress> captor = ArgumentCaptor.forClass(FlashcardProgress.class);
                verify(enrollmentAccessService).requireCourseAccess(courseId);
                verify(flashcardProgressRepository).findByStudentIdAndCardId(studentId, cardId);
                verify(flashcardProgressRepository).save(captor.capture());
                assertThat(captor.getValue()).isSameAs(progress);
                assertThat(captor.getValue().getUpdatedAt()).isEqualTo(response.lastReviewedAt());
        }

        @Test
        void submitFlashcardProgressWithStillLearningCreatesLearningProgress() {
                UUID courseId = UUID.randomUUID();
                UUID studentId = UUID.randomUUID();
                UUID cardId = UUID.randomUUID();
                FlashcardSet flashcardSet = flashcardSet(UUID.randomUUID(), UUID.randomUUID(), "Course set");
                flashcardSet.setCourse(course(courseId));
                FlashcardCard card = flashcardCard(cardId, flashcardSet, "front", "back", 0);
                CourseEnrollment enrollment = enrollment(courseId, studentId);

                when(flashcardCardRepository.findByIdAndDeletedAtIsNull(cardId)).thenReturn(Optional.of(card));
                when(enrollmentAccessService.requireCourseAccess(courseId)).thenReturn(enrollment);
                when(flashcardProgressRepository.findByStudentIdAndCardId(studentId, cardId))
                                .thenReturn(Optional.empty());
                when(flashcardProgressRepository.save(any(FlashcardProgress.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));

                var response = service.submitFlashcardProgress(cardId, null,
                                new FlashcardProgressRequest("still_learning"));

                assertThat(response.cardId()).isEqualTo(cardId);
                assertThat(response.learningStatus()).isEqualTo("learning");
                assertThat(response.lastReviewResult()).isEqualTo("still_learning");
                assertThat(response.repetitions()).isZero();
                assertThat(response.intervalDays()).isEqualTo(1);
                assertThat(response.lastReviewedAt()).isNotNull();
                assertThat(response.nextReviewAt()).isEqualTo(response.lastReviewedAt().plus(1, ChronoUnit.DAYS));

                ArgumentCaptor<FlashcardProgress> captor = ArgumentCaptor.forClass(FlashcardProgress.class);
                verify(enrollmentAccessService).requireCourseAccess(courseId);
                verify(flashcardProgressRepository).findByStudentIdAndCardId(studentId, cardId);
                verify(flashcardProgressRepository).save(captor.capture());
                assertThat(captor.getValue().getStudentId()).isEqualTo(studentId);
                assertThat(captor.getValue().getFlashcard()).isSameAs(card);
        }

        @Test
        void submitFlashcardProgressRejectsInvalidResult() {
                UUID courseId = UUID.randomUUID();
                UUID studentId = UUID.randomUUID();
                UUID cardId = UUID.randomUUID();
                FlashcardSet flashcardSet = flashcardSet(UUID.randomUUID(), UUID.randomUUID(), "Course set");
                flashcardSet.setCourse(course(courseId));
                FlashcardCard card = flashcardCard(cardId, flashcardSet, "front", "back", 0);
                CourseEnrollment enrollment = enrollment(courseId, studentId);

                when(flashcardCardRepository.findByIdAndDeletedAtIsNull(cardId)).thenReturn(Optional.of(card));
                when(enrollmentAccessService.requireCourseAccess(courseId)).thenReturn(enrollment);

                assertThatThrownBy(
                                () -> service.submitFlashcardProgress(cardId, null,
                                                new FlashcardProgressRequest("forgot")))
                                .isInstanceOfSatisfying(BusinessException.class,
                                                exception -> assertThat(exception.errorCode())
                                                                .isEqualTo(ErrorCode.INVALID_REQUEST))
                                .hasMessage("Review result must be known or still_learning");

                verify(enrollmentAccessService).requireCourseAccess(courseId);
                verify(flashcardProgressRepository, never()).findByStudentIdAndCardId(studentId, cardId);
                verify(flashcardProgressRepository, never()).save(any());
        }

        private UserAccount authenticatedStudent(UUID studentId) {
                UserAccount student = new UserAccount();
                student.setId(studentId);
                return student;
        }

        private CurriculumVersion publishedVersion(UUID courseId) {
                CurriculumVersion version = new CurriculumVersion();
                version.setId(UUID.randomUUID());
                version.setCourseId(courseId);
                version.setScope(CurriculumScope.MASTER);
                version.setStatus(CurriculumStatus.PUBLISHED);
                return version;
        }

        private CurriculumSection sectionIn(CurriculumVersion version) {
                CurriculumSection section = new CurriculumSection();
                section.setId(UUID.randomUUID());
                version.addSection(section);
                return section;
        }

        private CurriculumLesson flashcardLessonIn(CurriculumSection section, UUID lessonId) {
                CurriculumLesson lesson = new CurriculumLesson();
                lesson.setId(lessonId);
                lesson.setLessonIdentityId(UUID.randomUUID());
                lesson.setType(LessonType.FLASHCARD);
                lesson.setStatus(LessonStatus.PUBLISHED);
                section.addLesson(lesson);
                return lesson;
        }

        private FlashcardSet flashcardSet(UUID setId, UUID curriculumLessonId, String title) {
                FlashcardSet flashcardSet = new FlashcardSet();
                flashcardSet.setId(setId);
                flashcardSet.setCurriculumLessonId(curriculumLessonId);
                flashcardSet.setTitle(title);
                return flashcardSet;
        }

        private FlashcardCard flashcardCard(
                        UUID cardId,
                        FlashcardSet flashcardSet,
                        String frontText,
                        String backText,
                        int orderIndex) {
                FlashcardCard card = new FlashcardCard();
                card.setId(cardId);
                card.setFlashcardSet(flashcardSet);
                card.setFrontText(frontText);
                card.setBackText(backText);
                card.setOrderIndex(orderIndex);
                return card;
        }

        private FlashcardProgress progress(
                        UUID studentId,
                        FlashcardCard card,
                        String learningStatus,
                        String lastReviewResult,
                        int repetitions,
                        int intervalDays) {
                FlashcardProgress progress = new FlashcardProgress();
                progress.setStudentId(studentId);
                progress.setFlashcard(card);
                progress.setLearningStatus(learningStatus);
                progress.setLastReviewResult(lastReviewResult);
                progress.setRepetitions(repetitions);
                progress.setIntervalDays(intervalDays);
                return progress;
        }

        private Course course(UUID courseId) {
                Course course = new Course();
                course.setId(courseId);
                return course;
        }

        private Course course(UUID courseId, String title, String thumbnailUrl, CourseStatus status) {
                Course course = course(courseId);
                course.setTitle(title);
                course.setThumbnailUrl(thumbnailUrl);
                course.setStatus(status);
                return course;
        }

        private CourseEnrollment enrollment(UUID courseId, UUID studentId) {
                CourseEnrollment enrollment = new CourseEnrollment();
                enrollment.setCourseId(courseId);
                enrollment.setStudentId(studentId);
                return enrollment;
        }

        private LessonProgress lessonProgress(UUID lessonIdentityId, boolean completed) {
                LessonProgress progress = new LessonProgress();
                progress.setLessonIdentityId(lessonIdentityId);
                progress.setCompleted(completed);
                return progress;
        }

        private CurriculumMetadataResponse metadata(CurriculumVersion version, UUID classId, String source) {
                return new CurriculumMetadataResponse(
                                version.getId(),
                                version.getScope().name(),
                                version.getCourseId(),
                                classId,
                                classId != null,
                                source);
        }

        private LearningContentResponse learningContentResponse(UUID courseId, CurriculumMetadataResponse metadata) {
                return new LearningContentResponse(
                                courseId,
                                "mapped course",
                                "mapped.png",
                                List.of(),
                                new LearningStats(0, 0, 0, 0, 0, 0),
                                metadata);
        }

        @Test
        void getLearningFlashcardsResolvesSetFromEquivalentCurriculumLessonIdentity() {
                UUID courseId = UUID.randomUUID();
                UUID studentId = UUID.randomUUID();
                UUID classLessonId = UUID.randomUUID();
                UUID lessonIdentityId = UUID.randomUUID();
                UUID setId = UUID.randomUUID();

                UserAccount student = new UserAccount();
                student.setId(studentId);
                when(currentUserService.requireAuthenticatedUser()).thenReturn(student);
                when(classEnrollmentRepository.findActiveClassIdsByCourseIdAndStudentId(courseId, studentId))
                                .thenReturn(List.of());

                CurriculumVersion version = new CurriculumVersion();
                version.setId(UUID.randomUUID());
                version.setCourseId(courseId);
                version.setScope(CurriculumScope.CLASS);
                version.setStatus(CurriculumStatus.PUBLISHED);

                CurriculumSection section = new CurriculumSection();
                section.setId(UUID.randomUUID());
                version.addSection(section);

                CurriculumLesson classLesson = new CurriculumLesson();
                classLesson.setId(classLessonId);
                classLesson.setLessonIdentityId(lessonIdentityId);
                classLesson.setType(LessonType.FLASHCARD);
                classLesson.setStatus(LessonStatus.PUBLISHED);
                section.addLesson(classLesson);

                when(curriculumResolutionService.resolveOnlineLearning(courseId, studentId))
                                .thenReturn(new CurriculumResolution(
                                                version,
                                                null,
                                                null,
                                                false,
                                                "class_customized"));

                when(compositionService.isCompositionVersion(version)).thenReturn(false);

                when(flashcardSetRepository.findByCurriculumLessonIdAndDeletedAtIsNull(classLessonId))
                                .thenReturn(Optional.empty());

                FlashcardSet sourceSet = new FlashcardSet();
                sourceSet.setId(setId);
                sourceSet.setTitle("Inherited flashcards");

                when(flashcardSetRepository.findActiveByLessonIdentityIdAndCurriculumStateOrderByUpdatedAtDesc(
                                lessonIdentityId,
                                CurriculumScope.MASTER,
                                CurriculumStatus.PUBLISHED))
                                .thenReturn(List.of(sourceSet));

                when(flashcardCardRepository.findActiveBySetIdOrderByOrderIndex(setId))
                                .thenReturn(List.of());

                var response = service.getLearningFlashcards(courseId, null, classLessonId);

                assertThat(response.id()).isEqualTo(setId);
                assertThat(response.lessonId()).isEqualTo(classLessonId);
        }

        @Test
        void submitFlashcardProgressShouldUseClassEnrollmentScopeWhenClassIdIsProvided() {
                UUID courseId = UUID.randomUUID();
                UUID classId = UUID.randomUUID();
                UUID studentId = UUID.randomUUID();
                UUID lessonId = UUID.randomUUID();
                UUID setId = UUID.randomUUID();
                UUID cardId = UUID.randomUUID();

                UserAccount student = new UserAccount();
                student.setId(studentId);
                when(currentUserService.requireAuthenticatedUser()).thenReturn(student);

                Course course = new Course();
                course.setId(courseId);
                FlashcardSet flashcardSet = new FlashcardSet();
                flashcardSet.setId(setId);
                flashcardSet.setCourse(course);
                flashcardSet.setCurriculumLessonId(lessonId);
                flashcardSet.setTitle("Class cards");
                FlashcardCard card = new FlashcardCard();
                card.setId(cardId);
                card.setFlashcardSet(flashcardSet);
                when(flashcardCardRepository.findByIdAndDeletedAtIsNull(cardId)).thenReturn(Optional.of(card));

                CurriculumVersion version = new CurriculumVersion();
                version.setId(UUID.randomUUID());
                version.setCourseId(courseId);
                version.setClassId(classId);
                version.setScope(CurriculumScope.CLASS);
                version.setStatus(CurriculumStatus.PUBLISHED);
                CurriculumSection section = new CurriculumSection();
                section.setId(UUID.randomUUID());
                version.addSection(section);
                CurriculumLesson lesson = new CurriculumLesson();
                lesson.setId(lessonId);
                lesson.setType(LessonType.FLASHCARD);
                lesson.setStatus(LessonStatus.PUBLISHED);
                section.addLesson(lesson);
                when(curriculumResolutionService.resolveClassLearning(courseId, classId, studentId))
                                .thenReturn(new CurriculumResolution(version, null, classId, true, "class_published"));
                when(compositionService.isCompositionVersion(version)).thenReturn(true);
                when(compositionService.effectiveLessons(section)).thenReturn(List.of(lesson));
                when(flashcardSetRepository.findByCurriculumLessonIdAndDeletedAtIsNull(lessonId))
                                .thenReturn(Optional.of(flashcardSet));
                when(flashcardProgressRepository.findByStudentIdAndCardId(studentId, cardId))
                                .thenReturn(Optional.empty());
                when(flashcardProgressRepository.save(any(FlashcardProgress.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));

                var response = service.submitFlashcardProgress(
                                cardId,
                                classId,
                                new FlashcardProgressRequest("known"));

                assertThat(response.cardId()).isEqualTo(cardId);
                assertThat(response.learningStatus()).isEqualTo("known");
                verify(curriculumResolutionService).resolveClassLearning(courseId, classId, studentId);
                verifyNoInteractions(enrollmentAccessService);
        }
}
