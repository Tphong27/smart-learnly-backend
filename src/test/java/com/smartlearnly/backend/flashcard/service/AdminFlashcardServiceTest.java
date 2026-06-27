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
import com.smartlearnly.backend.flashcard.dto.AdminFlashcardDtos.CreateFlashcardCardRequest;
import com.smartlearnly.backend.flashcard.dto.AdminFlashcardDtos.CreateFlashcardLessonRequest;
import com.smartlearnly.backend.flashcard.dto.AdminFlashcardDtos.FlashcardCardResponse;
import com.smartlearnly.backend.flashcard.dto.AdminFlashcardDtos.FlashcardLessonCreatedResponse;
import com.smartlearnly.backend.flashcard.dto.AdminFlashcardDtos.FlashcardSetResponse;
import com.smartlearnly.backend.flashcard.dto.AdminFlashcardDtos.ReorderFlashcardCardsRequest;
import com.smartlearnly.backend.flashcard.dto.AdminFlashcardDtos.UpdateFlashcardCardRequest;
import com.smartlearnly.backend.flashcard.entity.FlashcardCard;
import com.smartlearnly.backend.flashcard.entity.FlashcardSet;
import com.smartlearnly.backend.flashcard.repository.FlashcardCardRepository;
import com.smartlearnly.backend.flashcard.repository.FlashcardSetRepository;
import com.smartlearnly.backend.learning.lesson.entity.Lesson;
import com.smartlearnly.backend.learning.lesson.entity.LessonStatus;
import com.smartlearnly.backend.learning.lesson.entity.LessonType;
import com.smartlearnly.backend.learning.lesson.repository.LessonRepository;
import com.smartlearnly.backend.learning.module.entity.CourseSection;
import com.smartlearnly.backend.learning.module.repository.CourseSectionRepository;
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
    private CourseSectionRepository courseSectionRepository;
    @Mock
    private LessonRepository lessonRepository;
    @Mock
    private FlashcardSetRepository flashcardSetRepository;
    @Mock
    private FlashcardCardRepository flashcardCardRepository;
    @Mock
    private CurrentUserService currentUserService;

    private AdminFlashcardService adminFlashcardService;

    @BeforeEach
    void setUp() {
        adminFlashcardService = new AdminFlashcardService(
                courseRepository,
                courseSectionRepository,
                lessonRepository,
                flashcardSetRepository,
                flashcardCardRepository,
                currentUserService
        );
    }

    @Test
    void createFlashcardLessonShouldCreateLessonAndLinkedSet() {
        Course course = course();
        CourseSection section = section(course);
        UserAccount actor = actor();
        UUID lessonId = UUID.randomUUID();
        UUID setId = UUID.randomUUID();
        when(courseRepository.findByIdAndDeletedAtIsNull(course.getId())).thenReturn(Optional.of(course));
        when(courseSectionRepository.findByIdAndCourseId(section.getId(), course.getId())).thenReturn(Optional.of(section));
        when(currentUserService.requireAuthenticatedUser()).thenReturn(actor);
        when(lessonRepository.findMaxSortOrderBySectionId(section.getId())).thenReturn(4);
        when(lessonRepository.save(any(Lesson.class))).thenAnswer(invocation -> {
            Lesson lesson = invocation.getArgument(0);
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
                new CreateFlashcardLessonRequest("  Terms  ", "Basics", null, true, "published")
        );

        assertThat(response.lessonId()).isEqualTo(lessonId);
        assertThat(response.setId()).isEqualTo(setId);
        ArgumentCaptor<Lesson> lessonCaptor = ArgumentCaptor.forClass(Lesson.class);
        ArgumentCaptor<FlashcardSet> setCaptor = ArgumentCaptor.forClass(FlashcardSet.class);
        verify(lessonRepository).save(lessonCaptor.capture());
        verify(flashcardSetRepository).save(setCaptor.capture());
        assertThat(lessonCaptor.getValue().getType()).isEqualTo(LessonType.FLASHCARD);
        assertThat(lessonCaptor.getValue().getTitle()).isEqualTo("Terms");
        assertThat(lessonCaptor.getValue().getStatus()).isEqualTo(LessonStatus.PUBLISHED);
        assertThat(lessonCaptor.getValue().getSortOrder()).isEqualTo(5);
        assertThat(setCaptor.getValue().getLesson().getId()).isEqualTo(lessonId);
        assertThat(setCaptor.getValue().getCourse()).isSameAs(course);
        assertThat(setCaptor.getValue().getCreatedBy()).isSameAs(actor);
        assertThat(setCaptor.getValue().getIsPublic()).isFalse();
        assertThat(setCaptor.getValue().getIsOfficial()).isFalse();
    }

    @Test
    void addCardShouldAcceptTextOnlyCard() {
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
                new CreateFlashcardCardRequest("  Front  ", null, "  Back  ", null, null, null, null)
        );

        assertThat(response.frontText()).isEqualTo("Front");
        assertThat(response.backText()).isEqualTo("Back");
        assertThat(response.orderIndex()).isZero();
    }

    @Test
    void addCardShouldAcceptImageOnlyCard() {
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
                new CreateFlashcardCardRequest(null, "https://cdn.test/front.png", null, "https://cdn.test/back.png", null, null, null)
        );

        assertThat(response.frontImageUrl()).isEqualTo("https://cdn.test/front.png");
        assertThat(response.backImageUrl()).isEqualTo("https://cdn.test/back.png");
        assertThat(response.orderIndex()).isEqualTo(2);
    }

    @Test
    void addCardShouldRejectEmptyFrontSide() {
        FlashcardSet flashcardSet = flashcardSet();
        when(flashcardSetRepository.findByIdAndDeletedAtIsNull(flashcardSet.getId())).thenReturn(Optional.of(flashcardSet));

        assertThatThrownBy(() -> adminFlashcardService.addCard(
                flashcardSet.getId(),
                new CreateFlashcardCardRequest(" ", null, "Back", null, null, null, null)
        ))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));

        verify(flashcardCardRepository, never()).save(any());
    }

    @Test
    void addCardShouldRejectEmptyBackSide() {
        FlashcardSet flashcardSet = flashcardSet();
        when(flashcardSetRepository.findByIdAndDeletedAtIsNull(flashcardSet.getId())).thenReturn(Optional.of(flashcardSet));

        assertThatThrownBy(() -> adminFlashcardService.addCard(
                flashcardSet.getId(),
                new CreateFlashcardCardRequest("Front", null, " ", null, null, null, null)
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
                new UpdateFlashcardCardRequest("  Updated front  ", null, "  Updated back  ", null, " hint ", null, 3)
        );

        assertThat(response.frontText()).isEqualTo("Updated front");
        assertThat(response.backText()).isEqualTo("Updated back");
        assertThat(response.hint()).isEqualTo("hint");
        assertThat(response.orderIndex()).isEqualTo(3);
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
    void reorderCardsShouldRequireAndPersistAllActiveCards() {
        FlashcardSet flashcardSet = flashcardSet();
        FlashcardCard first = card(flashcardSet, 0);
        FlashcardCard second = card(flashcardSet, 1);
        when(flashcardSetRepository.findByIdAndDeletedAtIsNull(flashcardSet.getId())).thenReturn(Optional.of(flashcardSet));
        when(flashcardCardRepository.findActiveBySetIdOrderByOrderIndex(flashcardSet.getId()))
                .thenReturn(List.of(first, second));

        FlashcardSetResponse response = adminFlashcardService.reorderCards(
                flashcardSet.getId(),
                new ReorderFlashcardCardsRequest(List.of(second.getId(), first.getId()))
        );

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

    private CourseSection section(Course course) {
        CourseSection section = new CourseSection();
        section.setId(UUID.randomUUID());
        section.setCourse(course);
        section.setTitle("Section");
        section.setSortOrder(0);
        return section;
    }

    private Lesson lesson(Course course, CourseSection section) {
        Lesson lesson = new Lesson();
        lesson.setId(UUID.randomUUID());
        lesson.setCourse(course);
        lesson.setSection(section);
        lesson.setTitle("Flashcards");
        lesson.setType(LessonType.FLASHCARD);
        lesson.setStatus(LessonStatus.DRAFT);
        lesson.setPreview(false);
        lesson.setSortOrder(0);
        return lesson;
    }

    private FlashcardSet flashcardSet() {
        Course course = course();
        CourseSection section = section(course);
        FlashcardSet flashcardSet = new FlashcardSet();
        flashcardSet.setId(UUID.randomUUID());
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
}
