package com.smartlearnly.backend.flashcard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.course.entity.Course;
import com.smartlearnly.backend.course.repository.CourseRepository;
import com.smartlearnly.backend.course.access.service.CourseAccessService;
import com.smartlearnly.backend.curriculum.admin.service.MasterCurriculumAccessService;
import com.smartlearnly.backend.curriculum.entity.CurriculumLesson;
import com.smartlearnly.backend.curriculum.entity.CurriculumScope;
import com.smartlearnly.backend.curriculum.entity.CurriculumSection;
import com.smartlearnly.backend.curriculum.entity.CurriculumStatus;
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
import com.smartlearnly.backend.learning.lesson.entity.Lesson;
import com.smartlearnly.backend.learning.lesson.entity.LessonStatus;
import com.smartlearnly.backend.learning.lesson.entity.LessonType;
import com.smartlearnly.backend.learning.lesson.repository.LessonRepository;
import com.smartlearnly.backend.learning.module.entity.CourseModule;
import com.smartlearnly.backend.learning.module.repository.CourseModuleRepository;
import com.smartlearnly.backend.user.entity.UserAccount;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminFlashcardServiceTest {
    @Mock
    private CourseRepository courseRepository;
    @Mock
    private LessonRepository lessonRepository;
    @Mock
    private FlashcardSetRepository flashcardSetRepository;
    @Mock
    private FlashcardCardRepository flashcardCardRepository;
    @Mock
    private CurrentUserService currentUserService;

    private AdminFlashcardService adminFlashcardService;
    @Mock
    private CurriculumLessonRepository curriculumLessonRepository;

    @Mock
    private CourseAccessService courseAccessService;
    @Mock
    private MasterCurriculumAccessService masterCurriculumAccessService;

    @BeforeEach
    void setUp() {
        adminFlashcardService = new AdminFlashcardService(
                courseRepository,
                lessonRepository,
                flashcardSetRepository,
                flashcardCardRepository,
                currentUserService,
                curriculumLessonRepository,
                courseAccessService,
                masterCurriculumAccessService);
    }

    @Test
    void createFlashcardLessonShouldCreateLessonAndLinkedSet() {
        Course course = course();
        CurriculumVersion version = curriculumVersion(course.getId());
        CurriculumSection section = curriculumSection(version);
        UserAccount actor = actor();
        UUID lessonId = UUID.randomUUID();
        UUID setId = UUID.randomUUID();
        when(courseRepository.findByIdAndDeletedAtIsNull(course.getId())).thenReturn(Optional.of(course));
        when(masterCurriculumAccessService.findUpdatableSection(section.getId())).thenReturn(section);
        when(currentUserService.requireAuthenticatedUser()).thenReturn(actor);
        when(curriculumLessonRepository.findMaxSortOrderBySectionId(section.getId())).thenReturn(4);
        when(curriculumLessonRepository.save(any(CurriculumLesson.class))).thenAnswer(invocation -> {
            CurriculumLesson lesson = invocation.getArgument(0);
            lesson.setId(lessonId);
            return lesson;
        });
        when(flashcardSetRepository.save(any(FlashcardSet.class))).thenAnswer(invocation -> {
            FlashcardSet flashcardSet = invocation.getArgument(0);
            flashcardSet.setId(setId);
            return flashcardSet;
        });

        FlashcardLessonCreatedResponse response = adminFlashcardService.createFlashcardLesson(
                course.getId(),
                section.getId(),
                new CreateFlashcardLessonRequest("  Terms  ", "Basics", null, true, "published"));

        assertThat(response.lessonId()).isEqualTo(lessonId);
        assertThat(response.setId()).isEqualTo(setId);
        ArgumentCaptor<CurriculumLesson> lessonCaptor = ArgumentCaptor.forClass(CurriculumLesson.class);
        ArgumentCaptor<FlashcardSet> setCaptor = ArgumentCaptor.forClass(FlashcardSet.class);
        verify(curriculumLessonRepository).save(lessonCaptor.capture());
        verify(flashcardSetRepository).save(setCaptor.capture());
        assertThat(lessonCaptor.getValue().getType()).isEqualTo(LessonType.FLASHCARD);
        assertThat(lessonCaptor.getValue().getTitle()).isEqualTo("Terms");
        assertThat(lessonCaptor.getValue().getStatus()).isEqualTo(LessonStatus.PUBLISHED);
        assertThat(lessonCaptor.getValue().getSortOrder()).isEqualTo(5);
        assertThat(setCaptor.getValue().getCurriculumLessonId()).isEqualTo(lessonId);
        assertThat(setCaptor.getValue().getCourse()).isSameAs(course);
        assertThat(setCaptor.getValue().getCreatedBy()).isSameAs(actor);
        assertThat(setCaptor.getValue().getIsPublic()).isFalse();
        assertThat(setCaptor.getValue().getIsOfficial()).isFalse();
    }

    @Test
    void getSetByLessonShouldResolveFlashcardsFromEquivalentCurriculumLesson() {
        UUID classLessonId = UUID.randomUUID();
        UUID lessonIdentityId = UUID.randomUUID();
        Course course = course();
        CurriculumLesson classLesson = new CurriculumLesson();
        classLesson.setId(classLessonId);
        classLesson.setLessonIdentityId(lessonIdentityId);
        FlashcardSet sourceSet = flashcardSet();
        sourceSet.setCourse(course);

        when(flashcardSetRepository.findByLessonIdAndDeletedAtIsNull(classLessonId))
                .thenReturn(Optional.empty());
        when(flashcardSetRepository.findByCurriculumLessonIdAndDeletedAtIsNull(classLessonId))
                .thenReturn(Optional.empty());
        when(curriculumLessonRepository.findById(classLessonId)).thenReturn(Optional.of(classLesson));
        when(flashcardSetRepository.findActiveByLessonIdentityIdAndCurriculumStateOrderByUpdatedAtDesc(
                lessonIdentityId,
                CurriculumScope.MASTER,
                CurriculumStatus.PUBLISHED))
                .thenReturn(List.of(sourceSet));
        when(flashcardCardRepository.findActiveBySetIdOrderByOrderIndex(sourceSet.getId()))
                .thenReturn(List.of());

        FlashcardSetResponse response = adminFlashcardService.getSetByLesson(classLessonId);

        assertThat(response.id()).isEqualTo(sourceSet.getId());
        verify(courseAccessService).requireReadableCourse(course.getId());
    }

    @Test
    void addCardShouldAcceptFrontAndBackTextCard() {
        FlashcardSet flashcardSet = flashcardSet();
        when(flashcardSetRepository.findByIdAndDeletedAtIsNull(flashcardSet.getId()))
                .thenReturn(Optional.of(flashcardSet));
        when(flashcardCardRepository.findMaxOrderIndexBySetId(flashcardSet.getId())).thenReturn(-1);
        when(flashcardCardRepository.save(any(FlashcardCard.class))).thenAnswer(invocation -> {
            FlashcardCard card = invocation.getArgument(0);
            card.setId(UUID.randomUUID());
            return card;
        });

        FlashcardCardResponse response = adminFlashcardService.addCard(
                flashcardSet.getId(),
                new CreateFlashcardCardRequest("  Front  ", null, "  Back  ", null, null, null, null));

        assertThat(response.frontText()).isEqualTo("Front");
        assertThat(response.backText()).isEqualTo("Back");
        assertThat(response.orderIndex()).isZero();
    }

    @Test
    void getSetShouldReturnActiveSetAndEnforceReadableAccess() {
        FlashcardSet flashcardSet = flashcardSet();
        FlashcardCard card = card(flashcardSet, 0);
        when(flashcardSetRepository.findByIdAndDeletedAtIsNull(flashcardSet.getId()))
                .thenReturn(Optional.of(flashcardSet));
        when(flashcardCardRepository.findActiveBySetIdOrderByOrderIndex(flashcardSet.getId()))
                .thenReturn(List.of(card));

        FlashcardSetResponse response = adminFlashcardService.getSet(flashcardSet.getId());

        assertThat(response.id()).isEqualTo(flashcardSet.getId());
        assertThat(response.lessonId()).isEqualTo(flashcardSet.getLesson().getId());
        assertThat(response.courseId()).isEqualTo(flashcardSet.getCourse().getId());
        assertThat(response.sectionId()).isEqualTo(flashcardSet.getLesson().getModule().getId());
        assertThat(response.title()).isEqualTo("Flashcards");
        assertThat(response.cards()).hasSize(1);
        verify(courseAccessService).requireReadableCourse(flashcardSet.getCourse().getId());
    }

    @Test
    void getSetShouldRejectMissingSetAndInvalidLinkedLesson() {
        UUID missingSetId = UUID.randomUUID();
        when(flashcardSetRepository.findByIdAndDeletedAtIsNull(missingSetId)).thenReturn(Optional.empty());

        assertBusinessException(
                () -> adminFlashcardService.getSet(missingSetId),
                ErrorCode.RESOURCE_NOT_FOUND,
                "Flashcard set was not found"
        );

        FlashcardSet videoLessonSet = flashcardSet();
        videoLessonSet.getLesson().setType(LessonType.VIDEO);
        when(flashcardSetRepository.findByIdAndDeletedAtIsNull(videoLessonSet.getId()))
                .thenReturn(Optional.of(videoLessonSet));

        assertBusinessException(
                () -> adminFlashcardService.getSet(videoLessonSet.getId()),
                ErrorCode.INVALID_REQUEST,
                "Flashcard set is not linked to a flashcard lesson"
        );

        verify(courseAccessService, never()).requireReadableCourse(any());
    }

    @Test
    void getSetShouldRejectSetWithDeletedCourseBeforeAccessCheck() {
        FlashcardSet flashcardSet = flashcardSet();
        flashcardSet.getCourse().setDeletedAt(Instant.now());
        flashcardSet.getLesson().getCourse().setDeletedAt(Instant.now());
        when(flashcardSetRepository.findByIdAndDeletedAtIsNull(flashcardSet.getId()))
                .thenReturn(Optional.of(flashcardSet));

        assertBusinessException(
                () -> adminFlashcardService.getSet(flashcardSet.getId()),
                ErrorCode.RESOURCE_NOT_FOUND,
                "Flashcard lesson was not found"
        );

        verify(courseAccessService, never()).requireReadableCourse(any());
    }

    @Test
    void getSetByLessonShouldResolveLegacyLessonReference() {
        FlashcardSet flashcardSet = flashcardSet();
        FlashcardCard card = card(flashcardSet, 0);
        UUID lessonId = flashcardSet.getLesson().getId();
        when(flashcardSetRepository.findByLessonIdAndDeletedAtIsNull(lessonId))
                .thenReturn(Optional.of(flashcardSet));
        when(flashcardCardRepository.findActiveBySetIdOrderByOrderIndex(flashcardSet.getId()))
                .thenReturn(List.of(card));

        FlashcardSetResponse response = adminFlashcardService.getSetByLesson(lessonId);

        assertThat(response.id()).isEqualTo(flashcardSet.getId());
        assertThat(response.lessonId()).isEqualTo(lessonId);
        assertThat(response.cards()).hasSize(1);
        verify(courseAccessService).requireReadableCourse(flashcardSet.getCourse().getId());
        verify(flashcardSetRepository, never()).findByCurriculumLessonIdAndDeletedAtIsNull(lessonId);
    }

    @Test
    void getSetByLessonShouldResolveCurriculumLessonReference() {
        Course course = course();
        CurriculumVersion version = curriculumVersion(course.getId());
        CurriculumSection section = curriculumSection(version);
        CurriculumLesson lesson = curriculumLesson(section, LessonType.FLASHCARD);
        FlashcardSet flashcardSet = curriculumFlashcardSet(course, lesson);
        when(flashcardSetRepository.findByLessonIdAndDeletedAtIsNull(lesson.getId())).thenReturn(Optional.empty());
        when(flashcardSetRepository.findByCurriculumLessonIdAndDeletedAtIsNull(lesson.getId()))
                .thenReturn(Optional.of(flashcardSet));
        when(curriculumLessonRepository.findById(lesson.getId())).thenReturn(Optional.of(lesson));
        when(flashcardCardRepository.findActiveBySetIdOrderByOrderIndex(flashcardSet.getId()))
                .thenReturn(List.of());

        FlashcardSetResponse response = adminFlashcardService.getSetByLesson(lesson.getId());

        assertThat(response.id()).isEqualTo(flashcardSet.getId());
        assertThat(response.lessonId()).isEqualTo(lesson.getId());
        assertThat(response.courseId()).isEqualTo(course.getId());
        assertThat(response.sectionId()).isEqualTo(section.getId());
        verify(courseAccessService).requireReadableCourse(course.getId());
    }

    @Test
    void getSetByLessonShouldResolveViaSourceLessonAndSourceCurriculumLesson() {
        FlashcardSet sourceLessonSet = flashcardSet();
        UUID curriculumLessonId = UUID.randomUUID();
        CurriculumLesson sourceLessonLink = curriculumLesson(curriculumSection(curriculumVersion(sourceLessonSet.getCourse().getId())),
                LessonType.FLASHCARD);
        sourceLessonLink.setId(curriculumLessonId);
        sourceLessonLink.setSourceLessonId(sourceLessonSet.getLesson().getId());
        when(flashcardSetRepository.findByLessonIdAndDeletedAtIsNull(curriculumLessonId)).thenReturn(Optional.empty());
        when(flashcardSetRepository.findByCurriculumLessonIdAndDeletedAtIsNull(curriculumLessonId)).thenReturn(Optional.empty());
        when(curriculumLessonRepository.findById(curriculumLessonId)).thenReturn(Optional.of(sourceLessonLink));
        when(flashcardSetRepository.findByLessonIdAndDeletedAtIsNull(sourceLessonSet.getLesson().getId()))
                .thenReturn(Optional.of(sourceLessonSet));
        when(flashcardCardRepository.findActiveBySetIdOrderByOrderIndex(sourceLessonSet.getId()))
                .thenReturn(List.of());

        FlashcardSetResponse sourceLessonResponse = adminFlashcardService.getSetByLesson(curriculumLessonId);

        assertThat(sourceLessonResponse.id()).isEqualTo(sourceLessonSet.getId());

        Course course = course();
        CurriculumVersion version = curriculumVersion(course.getId());
        CurriculumSection section = curriculumSection(version);
        CurriculumLesson sourceCurriculumLesson = curriculumLesson(section, LessonType.FLASHCARD);
        FlashcardSet sourceCurriculumSet = curriculumFlashcardSet(course, sourceCurriculumLesson);
        CurriculumLesson inheritedLesson = curriculumLesson(section, LessonType.FLASHCARD);
        inheritedLesson.setSourceCurriculumLessonId(sourceCurriculumLesson.getId());
        when(flashcardSetRepository.findByLessonIdAndDeletedAtIsNull(inheritedLesson.getId())).thenReturn(Optional.empty());
        when(flashcardSetRepository.findByCurriculumLessonIdAndDeletedAtIsNull(inheritedLesson.getId()))
                .thenReturn(Optional.empty());
        when(curriculumLessonRepository.findById(inheritedLesson.getId())).thenReturn(Optional.of(inheritedLesson));
        when(flashcardSetRepository.findByCurriculumLessonIdAndDeletedAtIsNull(sourceCurriculumLesson.getId()))
                .thenReturn(Optional.of(sourceCurriculumSet));
        when(curriculumLessonRepository.findById(sourceCurriculumLesson.getId())).thenReturn(Optional.of(sourceCurriculumLesson));
        when(flashcardCardRepository.findActiveBySetIdOrderByOrderIndex(sourceCurriculumSet.getId()))
                .thenReturn(List.of());

        FlashcardSetResponse sourceCurriculumResponse = adminFlashcardService.getSetByLesson(inheritedLesson.getId());

        assertThat(sourceCurriculumResponse.id()).isEqualTo(sourceCurriculumSet.getId());
        assertThat(sourceCurriculumResponse.lessonId()).isEqualTo(sourceCurriculumLesson.getId());
    }

    @Test
    void getSetByLessonShouldRejectMissingLessonReference() {
        UUID lessonId = UUID.randomUUID();
        when(flashcardSetRepository.findByLessonIdAndDeletedAtIsNull(lessonId)).thenReturn(Optional.empty());
        when(flashcardSetRepository.findByCurriculumLessonIdAndDeletedAtIsNull(lessonId)).thenReturn(Optional.empty());
        when(curriculumLessonRepository.findById(lessonId)).thenReturn(Optional.empty());

        assertBusinessException(
                () -> adminFlashcardService.getSetByLesson(lessonId),
                ErrorCode.RESOURCE_NOT_FOUND,
                "Flashcard set was not found"
        );
    }

    @Test
    void getSetByLessonShouldRejectCurriculumReferenceWithoutLinkedSet() {
        UUID lessonId = UUID.randomUUID();
        CurriculumLesson lesson = curriculumLesson(curriculumSection(curriculumVersion(UUID.randomUUID())), LessonType.FLASHCARD);
        lesson.setId(lessonId);
        when(flashcardSetRepository.findByLessonIdAndDeletedAtIsNull(lessonId)).thenReturn(Optional.empty());
        when(flashcardSetRepository.findByCurriculumLessonIdAndDeletedAtIsNull(lessonId)).thenReturn(Optional.empty());
        when(curriculumLessonRepository.findById(lessonId)).thenReturn(Optional.of(lesson));

        assertBusinessException(
                () -> adminFlashcardService.getSetByLesson(lessonId),
                ErrorCode.RESOURCE_NOT_FOUND,
                "Flashcard set was not found"
        );
    }

    @Test
    void addCardShouldAcceptFrontOnlyCard() {
        FlashcardSet flashcardSet = flashcardSet();
        when(flashcardSetRepository.findByIdAndDeletedAtIsNull(flashcardSet.getId())).thenReturn(Optional.of(flashcardSet));
        when(flashcardCardRepository.findMaxOrderIndexBySetId(flashcardSet.getId())).thenReturn(-1);
        when(flashcardCardRepository.save(any(FlashcardCard.class))).thenAnswer(invocation -> {
            FlashcardCard card = invocation.getArgument(0);
            card.setId(UUID.randomUUID());
            return card;
        });

        FlashcardCardResponse response = adminFlashcardService.addCard(
                flashcardSet.getId(),
                new CreateFlashcardCardRequest("Front only", null, null, null, null, null, null)
        );

        assertThat(response.frontText()).isEqualTo("Front only");
        assertThat(response.backText()).isNull();
        assertThat(response.orderIndex()).isZero();
    }

    @Test
    void addCardShouldAcceptBackOnlyCard() {
        FlashcardSet flashcardSet = flashcardSet();
        when(flashcardSetRepository.findByIdAndDeletedAtIsNull(flashcardSet.getId())).thenReturn(Optional.of(flashcardSet));
        when(flashcardCardRepository.findMaxOrderIndexBySetId(flashcardSet.getId())).thenReturn(-1);
        when(flashcardCardRepository.save(any(FlashcardCard.class))).thenAnswer(invocation -> {
            FlashcardCard card = invocation.getArgument(0);
            card.setId(UUID.randomUUID());
            return card;
        });

        FlashcardCardResponse response = adminFlashcardService.addCard(
                flashcardSet.getId(),
                new CreateFlashcardCardRequest(null, null, "Back only", null, null, null, null)
        );

        assertThat(response.frontText()).isNull();
        assertThat(response.backText()).isEqualTo("Back only");
    }

    @Test
    void addCardShouldAcceptFrontImageOnlyCard() {
        FlashcardSet flashcardSet = flashcardSet();
        when(flashcardSetRepository.findByIdAndDeletedAtIsNull(flashcardSet.getId()))
                .thenReturn(Optional.of(flashcardSet));
        when(flashcardCardRepository.findMaxOrderIndexBySetId(flashcardSet.getId())).thenReturn(1);
        when(flashcardCardRepository.save(any(FlashcardCard.class))).thenAnswer(invocation -> {
            FlashcardCard card = invocation.getArgument(0);
            card.setId(UUID.randomUUID());
            return card;
        });

        FlashcardCardResponse response = adminFlashcardService.addCard(
                flashcardSet.getId(),
                new CreateFlashcardCardRequest(null, "https://cdn.test/front.png", null, null, null, null, null)
        );

        assertThat(response.frontImageUrl()).isEqualTo("https://cdn.test/front.png");
        assertThat(response.backText()).isNull();
        assertThat(response.orderIndex()).isEqualTo(2);
    }

    @Test
    void addCardShouldAcceptBackImageOnlyCard() {
        FlashcardSet flashcardSet = flashcardSet();
        when(flashcardSetRepository.findByIdAndDeletedAtIsNull(flashcardSet.getId())).thenReturn(Optional.of(flashcardSet));
        when(flashcardCardRepository.findMaxOrderIndexBySetId(flashcardSet.getId())).thenReturn(1);
        when(flashcardCardRepository.save(any(FlashcardCard.class))).thenAnswer(invocation -> {
            FlashcardCard card = invocation.getArgument(0);
            card.setId(UUID.randomUUID());
            return card;
        });

        FlashcardCardResponse response = adminFlashcardService.addCard(
                flashcardSet.getId(),
                new CreateFlashcardCardRequest(null, null, null, "https://cdn.test/back.png", null, null, null)
        );

        assertThat(response.frontText()).isNull();
        assertThat(response.backImageUrl()).isEqualTo("https://cdn.test/back.png");
    }

    @Test
    void addCardShouldRejectHintOnlyCard() {
        FlashcardSet flashcardSet = flashcardSet();
        when(flashcardSetRepository.findByIdAndDeletedAtIsNull(flashcardSet.getId()))
                .thenReturn(Optional.of(flashcardSet));

        assertThatThrownBy(() -> adminFlashcardService.addCard(
                flashcardSet.getId(),
                new CreateFlashcardCardRequest(" ", null, " ", null, "Hint only", null, null)
        ))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));

        verify(flashcardCardRepository, never()).save(any());
    }

    @Test
    void addCardShouldRejectExplanationOnlyCard() {
        FlashcardSet flashcardSet = flashcardSet();
        when(flashcardSetRepository.findByIdAndDeletedAtIsNull(flashcardSet.getId()))
                .thenReturn(Optional.of(flashcardSet));

        assertThatThrownBy(() -> adminFlashcardService.addCard(
                flashcardSet.getId(),
                new CreateFlashcardCardRequest(null, null, null, null, null, "Explanation only", null)
        ))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));

        verify(flashcardCardRepository, never()).save(any());
    }

    @Test
    void addCardShouldRejectFullyEmptyCard() {
        FlashcardSet flashcardSet = flashcardSet();
        when(flashcardSetRepository.findByIdAndDeletedAtIsNull(flashcardSet.getId())).thenReturn(Optional.of(flashcardSet));

        assertThatThrownBy(() -> adminFlashcardService.addCard(
                flashcardSet.getId(),
                new CreateFlashcardCardRequest(" ", " ", " ", " ", null, null, null)
        ))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));

        verify(flashcardCardRepository, never()).save(any());
    }

    @Test
    void updateCardShouldNormalizeAndSaveCard() {
        FlashcardCard card = card(flashcardSet(), 0);
        when(flashcardCardRepository.findByIdAndDeletedAtIsNull(card.getId())).thenReturn(Optional.of(card));
        when(flashcardCardRepository.save(card)).thenReturn(card);

        FlashcardCardResponse response = adminFlashcardService.updateCard(
                card.getId(),
                new UpdateFlashcardCardRequest("  Updated front  ", null, "  Updated back  ", null, " hint ", null, 3));

        assertThat(response.frontText()).isEqualTo("Updated front");
        assertThat(response.backText()).isEqualTo("Updated back");
        assertThat(response.hint()).isEqualTo("hint");
        assertThat(response.orderIndex()).isEqualTo(3);
    }

    @Test
    void updateSetShouldUpdateLegacySetAndLinkedLessonTitle() {
        FlashcardSet flashcardSet = flashcardSet();
        FlashcardCard card = card(flashcardSet, 0);
        when(flashcardSetRepository.findByIdAndDeletedAtIsNull(flashcardSet.getId()))
                .thenReturn(Optional.of(flashcardSet));
        when(flashcardSetRepository.save(flashcardSet)).thenReturn(flashcardSet);
        when(flashcardCardRepository.findActiveBySetIdOrderByOrderIndex(flashcardSet.getId()))
                .thenReturn(List.of(card));

        FlashcardSetResponse response = adminFlashcardService.updateSet(
                flashcardSet.getId(),
                new UpdateFlashcardSetRequest("  Updated title  ", "  Updated description  "));

        assertThat(response.title()).isEqualTo("Updated title");
        assertThat(response.description()).isEqualTo("Updated description");
        assertThat(flashcardSet.getLesson().getTitle()).isEqualTo("Updated title");
        verify(courseAccessService).requireUpdatableCourse(flashcardSet.getCourse().getId());
        verify(lessonRepository).save(flashcardSet.getLesson());
        verify(flashcardSetRepository).save(flashcardSet);
    }

    @Test
    void updateSetShouldUpdateCurriculumLinkedLessonTitle() {
        Course course = course();
        CurriculumVersion version = curriculumVersion(course.getId());
        CurriculumSection section = curriculumSection(version);
        CurriculumLesson lesson = curriculumLesson(section, LessonType.FLASHCARD);
        FlashcardSet flashcardSet = curriculumFlashcardSet(course, lesson);
        when(flashcardSetRepository.findByIdAndDeletedAtIsNull(flashcardSet.getId()))
                .thenReturn(Optional.of(flashcardSet));
        when(curriculumLessonRepository.findById(lesson.getId())).thenReturn(Optional.of(lesson));
        when(flashcardSetRepository.save(flashcardSet)).thenReturn(flashcardSet);
        when(flashcardCardRepository.findActiveBySetIdOrderByOrderIndex(flashcardSet.getId()))
                .thenReturn(List.of());

        FlashcardSetResponse response = adminFlashcardService.updateSet(
                flashcardSet.getId(),
                new UpdateFlashcardSetRequest("Curriculum title", " "));

        assertThat(response.title()).isEqualTo("Curriculum title");
        assertThat(response.description()).isNull();
        assertThat(lesson.getTitle()).isEqualTo("Curriculum title");
        verify(courseAccessService).requireUpdatableCourse(course.getId());
        verify(curriculumLessonRepository).save(lesson);
    }

    @Test
    void updateSetShouldRejectBlankTitleAndMissingSet() {
        FlashcardSet flashcardSet = flashcardSet();
        UUID missingSetId = UUID.randomUUID();
        when(flashcardSetRepository.findByIdAndDeletedAtIsNull(flashcardSet.getId()))
                .thenReturn(Optional.of(flashcardSet));
        when(flashcardSetRepository.findByIdAndDeletedAtIsNull(missingSetId)).thenReturn(Optional.empty());

        assertBusinessException(
                () -> adminFlashcardService.updateSet(flashcardSet.getId(), new UpdateFlashcardSetRequest(" ", null)),
                ErrorCode.INVALID_REQUEST,
                "Flashcard set title is required"
        );
        assertBusinessException(
                () -> adminFlashcardService.updateSet(missingSetId, new UpdateFlashcardSetRequest("Title", null)),
                ErrorCode.RESOURCE_NOT_FOUND,
                "Flashcard set was not found"
        );

        verify(flashcardSetRepository, never()).save(any(FlashcardSet.class));
    }

    @Test
    void updateCardShouldRejectClearingExistingCardToEmpty() {
        FlashcardCard card = card(flashcardSet(), 0);
        when(flashcardCardRepository.findByIdAndDeletedAtIsNull(card.getId())).thenReturn(Optional.of(card));

        assertThatThrownBy(() -> adminFlashcardService.updateCard(
                card.getId(),
                new UpdateFlashcardCardRequest(" ", " ", " ", " ", null, null, null)
        ))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));

        verify(flashcardCardRepository, never()).save(any());
    }

    @Test
    void deleteCardShouldSoftDeleteCard() {
        FlashcardCard card = card(flashcardSet(), 0);
        when(flashcardCardRepository.findByIdAndDeletedAtIsNull(card.getId())).thenReturn(Optional.of(card));

        adminFlashcardService.deleteCard(card.getId());

        assertThat(card.getDeletedAt()).isNotNull();
        verify(flashcardCardRepository).save(card);
    }

    @Test
    void deleteSetShouldSoftDeleteSetCardsAndDeactivateLegacyLesson() {
        FlashcardSet flashcardSet = flashcardSet();
        FlashcardCard first = card(flashcardSet, 0);
        FlashcardCard second = card(flashcardSet, 1);
        when(flashcardSetRepository.findByIdAndDeletedAtIsNull(flashcardSet.getId()))
                .thenReturn(Optional.of(flashcardSet));
        when(flashcardCardRepository.findActiveBySetIdOrderByOrderIndex(flashcardSet.getId()))
                .thenReturn(List.of(first, second));

        adminFlashcardService.deleteSet(flashcardSet.getId());

        assertThat(flashcardSet.getDeletedAt()).isNotNull();
        assertThat(first.getDeletedAt()).isEqualTo(flashcardSet.getDeletedAt());
        assertThat(second.getDeletedAt()).isEqualTo(flashcardSet.getDeletedAt());
        assertThat(flashcardSet.getLesson().getStatus()).isEqualTo(LessonStatus.INACTIVE);
        verify(courseAccessService).requireUpdatableCourse(flashcardSet.getCourse().getId());
        verify(flashcardCardRepository).saveAll(List.of(first, second));
        verify(lessonRepository).save(flashcardSet.getLesson());
        verify(flashcardSetRepository).save(flashcardSet);
    }

    @Test
    void deleteSetShouldDeactivateCurriculumLessonAndRejectMissingSet() {
        Course course = course();
        CurriculumVersion version = curriculumVersion(course.getId());
        CurriculumSection section = curriculumSection(version);
        CurriculumLesson lesson = curriculumLesson(section, LessonType.FLASHCARD);
        FlashcardSet flashcardSet = curriculumFlashcardSet(course, lesson);
        UUID missingSetId = UUID.randomUUID();
        when(flashcardSetRepository.findByIdAndDeletedAtIsNull(flashcardSet.getId()))
                .thenReturn(Optional.of(flashcardSet));
        when(curriculumLessonRepository.findById(lesson.getId())).thenReturn(Optional.of(lesson));
        when(flashcardCardRepository.findActiveBySetIdOrderByOrderIndex(flashcardSet.getId()))
                .thenReturn(List.of());
        when(flashcardSetRepository.findByIdAndDeletedAtIsNull(missingSetId)).thenReturn(Optional.empty());

        adminFlashcardService.deleteSet(flashcardSet.getId());

        assertThat(flashcardSet.getDeletedAt()).isNotNull();
        assertThat(lesson.getStatus()).isEqualTo(LessonStatus.INACTIVE);
        assertThat(lesson.getDeletedAt()).isNotNull();
        verify(curriculumLessonRepository).save(lesson);

        assertBusinessException(
                () -> adminFlashcardService.deleteSet(missingSetId),
                ErrorCode.RESOURCE_NOT_FOUND,
                "Flashcard set was not found"
        );
    }

    @Test
    void reorderCardsShouldRequireAndPersistAllActiveCards() {
        FlashcardSet flashcardSet = flashcardSet();
        FlashcardCard first = card(flashcardSet, 0);
        FlashcardCard second = card(flashcardSet, 1);
        when(flashcardSetRepository.findByIdAndDeletedAtIsNull(flashcardSet.getId()))
                .thenReturn(Optional.of(flashcardSet));
        when(flashcardCardRepository.findActiveBySetIdOrderByOrderIndex(flashcardSet.getId()))
                .thenReturn(List.of(first, second));

        FlashcardSetResponse response = adminFlashcardService.reorderCards(
                flashcardSet.getId(),
                new ReorderFlashcardCardsRequest(List.of(second.getId(), first.getId())));

        assertThat(second.getOrderIndex()).isZero();
        assertThat(first.getOrderIndex()).isEqualTo(1);
        assertThat(response.cards()).extracting(FlashcardCardResponse::id)
                .containsExactly(second.getId(), first.getId());
        verify(flashcardCardRepository).saveAll(anyList());
    }

    private Course course() {
        Course course = new Course();
        course.setId(UUID.randomUUID());
        course.setTitle("Course");
        course.setSlug("course");
        return course;
    }

    private UserAccount actor() {
        UserAccount actor = new UserAccount();
        actor.setId(UUID.randomUUID());
        actor.setEmail("trainer@smartlearnly.dev");
        actor.setFullName("Trainer");
        actor.setRole("TRAINER");
        return actor;
    }

    private CourseModule module(Course course) {
        CourseModule module = new CourseModule();
        module.setId(UUID.randomUUID());
        module.setCourseId(course.getId());
        module.setTitle("Module");
        module.setOrderIndex(0);
        return module;
    }

    private CurriculumVersion curriculumVersion(UUID courseId) {
        CurriculumVersion version = new CurriculumVersion();
        version.setId(UUID.randomUUID());
        version.setCourseId(courseId);
        version.setScope(CurriculumScope.MASTER);
        version.setStatus(CurriculumStatus.DRAFT);
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

    private Lesson lesson(Course course, CourseModule module) {
        Lesson lesson = new Lesson();
        lesson.setId(UUID.randomUUID());
        lesson.setCourse(course);
        lesson.setModule(module);
        lesson.setTitle("Flashcards");
        lesson.setType(LessonType.FLASHCARD);
        lesson.setStatus(LessonStatus.DRAFT);
        lesson.setPreview(false);
        lesson.setSortOrder(0);
        return lesson;
    }

    private FlashcardSet flashcardSet() {
        Course course = course();
        CourseModule module = module(course);
        FlashcardSet flashcardSet = new FlashcardSet();
        flashcardSet.setId(UUID.randomUUID());
        flashcardSet.setCourse(course);
        flashcardSet.setLesson(lesson(course, module));
        flashcardSet.setCourse(course);
        flashcardSet.setTitle("Flashcards");
        flashcardSet.setCreatedAt(Instant.now());
        flashcardSet.setUpdatedAt(Instant.now());
        return flashcardSet;
    }

    private FlashcardSet curriculumFlashcardSet(Course course, CurriculumLesson lesson) {
        FlashcardSet flashcardSet = new FlashcardSet();
        flashcardSet.setId(UUID.randomUUID());
        flashcardSet.setCourse(course);
        flashcardSet.setCurriculumLessonId(lesson.getId());
        flashcardSet.setTitle("Curriculum flashcards");
        flashcardSet.setCreatedAt(Instant.now());
        flashcardSet.setUpdatedAt(Instant.now());
        return flashcardSet;
    }

    private CurriculumLesson curriculumLesson(CurriculumSection section, LessonType type) {
        CurriculumLesson lesson = new CurriculumLesson();
        lesson.setId(UUID.randomUUID());
        lesson.setSection(section);
        lesson.setLessonIdentityId(UUID.randomUUID());
        lesson.setTitle("Curriculum flashcards");
        lesson.setType(type);
        lesson.setStatus(LessonStatus.PUBLISHED);
        lesson.setPreview(false);
        lesson.setSortOrder(0);
        return lesson;
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

    private void assertBusinessException(Runnable action, ErrorCode errorCode, String message) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(errorCode))
                .hasMessage(message);
    }
}
