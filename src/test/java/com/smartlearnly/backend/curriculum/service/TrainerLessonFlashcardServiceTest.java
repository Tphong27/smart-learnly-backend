package com.smartlearnly.backend.curriculum.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.course.repository.CourseRepository;
import com.smartlearnly.backend.curriculum.entity.CurriculumLesson;
import com.smartlearnly.backend.curriculum.entity.CurriculumSection;
import com.smartlearnly.backend.curriculum.entity.CurriculumVersion;
import com.smartlearnly.backend.curriculum.repository.CurriculumLessonRepository;
import com.smartlearnly.backend.flashcard.dto.AdminFlashcardDtos.CreateFlashcardCardRequest;
import com.smartlearnly.backend.flashcard.dto.AdminFlashcardDtos.CreateFlashcardLessonRequest;
import com.smartlearnly.backend.flashcard.dto.AdminFlashcardDtos.FlashcardCardResponse;
import com.smartlearnly.backend.flashcard.dto.AdminFlashcardDtos.FlashcardLessonCreatedResponse;
import com.smartlearnly.backend.flashcard.entity.FlashcardCard;
import com.smartlearnly.backend.flashcard.entity.FlashcardSet;
import com.smartlearnly.backend.flashcard.repository.FlashcardCardRepository;
import com.smartlearnly.backend.flashcard.repository.FlashcardSetRepository;
import com.smartlearnly.backend.learning.lesson.entity.LessonType;
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
class TrainerLessonFlashcardServiceTest {
    @Mock
    private TrainerClassCurriculumService trainerClassCurriculumService;
    @Mock
    private CurriculumLessonRepository curriculumLessonRepository;
    @Mock
    private CourseRepository courseRepository;
    @Mock
    private FlashcardSetRepository flashcardSetRepository;
    @Mock
    private FlashcardCardRepository flashcardCardRepository;
    @Mock
    private CurrentUserService currentUserService;

    private TrainerLessonFlashcardService service;

    private final UUID classId = UUID.randomUUID();
    private final UUID lessonId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new TrainerLessonFlashcardService(
                trainerClassCurriculumService,
                curriculumLessonRepository,
                courseRepository,
                flashcardSetRepository,
                flashcardCardRepository,
                currentUserService);
    }

    @Test
    void createFlashcardSetShouldMarkLessonAndPersistSet() {
        CurriculumLesson lesson = lesson();
        UserAccount actor = actor();
        UUID savedSetId = UUID.randomUUID();
        when(trainerClassCurriculumService.requireOwnedClassLessonForWrite(classId, lessonId)).thenReturn(lesson);
        when(flashcardSetRepository.findByCurriculumLessonIdAndDeletedAtIsNull(lesson.getId()))
                .thenReturn(Optional.empty());
        when(currentUserService.requireAuthenticatedUser()).thenReturn(actor);
        when(flashcardSetRepository.save(any(FlashcardSet.class))).thenAnswer(invocation -> {
            FlashcardSet arg = invocation.getArgument(0);
            arg.setId(savedSetId);
            return arg;
        });

        FlashcardLessonCreatedResponse response = service.createFlashcardSet(
                classId,
                lessonId,
                new CreateFlashcardLessonRequest("  Terms  ", "Basics", null, null, null));

        assertThat(response.lessonId()).isEqualTo(lesson.getId());
        assertThat(response.setId()).isEqualTo(savedSetId);
        assertThat(lesson.getType()).isEqualTo(LessonType.FLASHCARD);
        verify(curriculumLessonRepository).save(lesson);

        ArgumentCaptor<FlashcardSet> captor = ArgumentCaptor.forClass(FlashcardSet.class);
        verify(flashcardSetRepository).save(captor.capture());
        assertThat(captor.getValue().getCurriculumLessonId()).isEqualTo(lesson.getId());
        assertThat(captor.getValue().getTitle()).isEqualTo("Terms");
        assertThat(captor.getValue().getDescription()).isEqualTo("Basics");
        assertThat(captor.getValue().getCreatedBy()).isSameAs(actor);
    }

    @Test
    void createFlashcardSetShouldRejectDuplicate() {
        CurriculumLesson lesson = lesson();
        when(trainerClassCurriculumService.requireOwnedClassLessonForWrite(classId, lessonId)).thenReturn(lesson);
        FlashcardSet existing = new FlashcardSet();
        when(flashcardSetRepository.findByCurriculumLessonIdAndDeletedAtIsNull(lesson.getId()))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.createFlashcardSet(
                classId,
                lessonId,
                new CreateFlashcardLessonRequest("Terms", null, null, null, null)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT));
        verifyNoInteractions(currentUserService);
        verify(flashcardSetRepository, never()).save(any());
    }

    @Test
    void addCardShouldRejectWhenBothSidesEmpty() {
        UUID setId = UUID.randomUUID();
        FlashcardSet flashcardSet = new FlashcardSet();
        flashcardSet.setId(setId);
        flashcardSet.setCurriculumLessonId(UUID.randomUUID());
        when(flashcardSetRepository.findByIdAndDeletedAtIsNull(setId)).thenReturn(Optional.of(flashcardSet));

        assertThatThrownBy(() -> service.addCard(
                classId,
                setId,
                new CreateFlashcardCardRequest(" ", null, " ", null, null, null, null)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
        verify(flashcardCardRepository, never()).save(any());
    }

    @Test
    void addCardShouldPersistCardWhenOwnershipCheckPasses() {
        UUID setId = UUID.randomUUID();
        UUID linkedLessonId = UUID.randomUUID();
        FlashcardSet flashcardSet = new FlashcardSet();
        flashcardSet.setId(setId);
        flashcardSet.setCurriculumLessonId(linkedLessonId);
        when(flashcardSetRepository.findByIdAndDeletedAtIsNull(setId)).thenReturn(Optional.of(flashcardSet));
        when(trainerClassCurriculumService.requireOwnedClassLessonForWrite(classId, linkedLessonId))
                .thenReturn(lesson());
        when(flashcardCardRepository.findMaxOrderIndexBySetId(setId)).thenReturn(-1);
        when(flashcardCardRepository.save(any(FlashcardCard.class))).thenAnswer(invocation -> {
            FlashcardCard card = invocation.getArgument(0);
            card.setId(UUID.randomUUID());
            card.setCreatedAt(Instant.now());
            card.setUpdatedAt(Instant.now());
            return card;
        });

        FlashcardCardResponse response = service.addCard(
                classId,
                setId,
                new CreateFlashcardCardRequest("  Front  ", null, "  Back  ", null, null, null, null));

        assertThat(response.frontText()).isEqualTo("Front");
        assertThat(response.backText()).isEqualTo("Back");
        assertThat(response.orderIndex()).isZero();
    }

    @Test
    void deleteCardShouldSoftDelete() {
        UUID cardId = UUID.randomUUID();
        UUID linkedLessonId = UUID.randomUUID();
        FlashcardSet flashcardSet = new FlashcardSet();
        flashcardSet.setId(UUID.randomUUID());
        flashcardSet.setCurriculumLessonId(linkedLessonId);
        FlashcardCard card = new FlashcardCard();
        card.setId(cardId);
        card.setFlashcardSet(flashcardSet);
        when(flashcardCardRepository.findByIdAndDeletedAtIsNull(cardId)).thenReturn(Optional.of(card));
        when(trainerClassCurriculumService.requireOwnedClassLessonForWrite(classId, linkedLessonId))
                .thenReturn(lesson());

        service.deleteCard(classId, cardId);

        assertThat(card.getDeletedAt()).isNotNull();
        verify(flashcardCardRepository).save(card);
    }

    @Test
    void deleteCardShouldRejectSetFromDifferentLesson() {
        UUID cardId = UUID.randomUUID();
        UUID linkedLessonId = UUID.randomUUID();
        FlashcardSet flashcardSet = new FlashcardSet();
        flashcardSet.setId(UUID.randomUUID());
        flashcardSet.setCurriculumLessonId(linkedLessonId);
        FlashcardCard card = new FlashcardCard();
        card.setId(cardId);
        card.setFlashcardSet(flashcardSet);
        when(flashcardCardRepository.findByIdAndDeletedAtIsNull(cardId)).thenReturn(Optional.of(card));
        when(trainerClassCurriculumService.requireOwnedClassLessonForWrite(classId, linkedLessonId))
                .thenThrow(new BusinessException(ErrorCode.FORBIDDEN, "Not authorised"));

        assertThatThrownBy(() -> service.deleteCard(classId, cardId))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        verify(flashcardCardRepository, never()).save(any());
    }

    @Test
    void getSetByLessonShouldReturnMappedResponse() {
        CurriculumLesson lesson = lesson();
        FlashcardSet flashcardSet = new FlashcardSet();
        flashcardSet.setId(UUID.randomUUID());
        flashcardSet.setCurriculumLessonId(lesson.getId());
        flashcardSet.setTitle("Set");
        flashcardSet.setCreatedAt(Instant.now());
        flashcardSet.setUpdatedAt(Instant.now());
        when(trainerClassCurriculumService.requireOwnedClassLessonForRead(classId, lessonId)).thenReturn(lesson);
        when(flashcardSetRepository.findByCurriculumLessonIdAndDeletedAtIsNull(lesson.getId()))
                .thenReturn(Optional.of(flashcardSet));
        when(flashcardCardRepository.findActiveBySetIdOrderByOrderIndex(flashcardSet.getId()))
                .thenReturn(List.of());

        var response = service.getSetByLesson(classId, lessonId);

        assertThat(response.id()).isEqualTo(flashcardSet.getId());
        assertThat(response.lessonId()).isEqualTo(lesson.getId());
        assertThat(response.cards()).isEmpty();
    }

    private CurriculumLesson lesson() {
        CurriculumLesson lesson = new CurriculumLesson();
        lesson.setId(lessonId);
        lesson.setTitle("Sample lesson");
        CurriculumVersion version = new CurriculumVersion();
        version.setId(UUID.randomUUID());
        version.setCourseId(UUID.randomUUID());
        CurriculumSection section = new CurriculumSection();
        section.setCurriculumVersion(version);
        lesson.setSection(section);
        return lesson;
    }

    private UserAccount actor() {
        UserAccount actor = new UserAccount();
        actor.setId(UUID.randomUUID());
        actor.setEmail("trainer@example.com");
        actor.setFullName("Trainer");
        actor.setRole("TRAINER");
        return actor;
    }
}
