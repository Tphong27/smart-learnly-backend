package com.smartlearnly.backend.flashcard.staging.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.course.entity.Course;
import com.smartlearnly.backend.flashcard.repository.FlashcardCardRepository;
import com.smartlearnly.backend.flashcard.entity.FlashcardSet;
import com.smartlearnly.backend.flashcard.repository.FlashcardSetRepository;
import com.smartlearnly.backend.flashcard.staging.dto.AdminFlashcardStagingDtos.ImportCourseQuestionsRequest;
import com.smartlearnly.backend.flashcard.staging.entity.FlashcardStagingBatch;
import com.smartlearnly.backend.flashcard.staging.entity.FlashcardStagingCard;
import com.smartlearnly.backend.flashcard.staging.repository.FlashcardStagingBatchRepository;
import com.smartlearnly.backend.flashcard.staging.repository.FlashcardStagingCardRepository;
import com.smartlearnly.backend.learning.lesson.entity.Lesson;
import com.smartlearnly.backend.learning.lesson.entity.LessonType;
import com.smartlearnly.backend.question.entity.Question;
import com.smartlearnly.backend.question.entity.QuestionAnswer;
import com.smartlearnly.backend.question.entity.QuestionStatus;
import com.smartlearnly.backend.question.entity.QuestionType;
import com.smartlearnly.backend.question.repository.QuestionRepository;
import com.smartlearnly.backend.question.repository.QuestionAnswerRepository;
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
class FlashcardCourseQuestionImportServiceTest {
    @Mock
    private FlashcardSetRepository flashcardSetRepository;
    @Mock
    private FlashcardCardRepository flashcardCardRepository;
    @Mock
    private FlashcardStagingBatchRepository stagingBatchRepository;
    @Mock
    private QuestionRepository questionRepository;
    @Mock
    private FlashcardStagingCardRepository stagingCardRepository;
    @Mock
    private QuestionAnswerRepository questionAnswerRepository;
    @Mock
    private CurrentUserService currentUserService;

    private FlashcardCourseQuestionImportService importService;

    @BeforeEach
    void setUp() {
        importService = new FlashcardCourseQuestionImportService(
                flashcardSetRepository,
                flashcardCardRepository,
                stagingBatchRepository,
                stagingCardRepository,
                questionRepository,
                questionAnswerRepository,
                currentUserService
        );
    }

    @Test
    void importCourseQuestionsShouldRejectDuplicateIds() {
        FlashcardSet set = flashcardSet();
        UUID questionId = UUID.randomUUID();
        when(flashcardSetRepository.findByIdAndDeletedAtIsNull(set.getId())).thenReturn(Optional.of(set));

        assertThatThrownBy(() -> importService.importCourseQuestions(
                set.getId(),
                new ImportCourseQuestionsRequest(List.of(questionId, questionId))
        ))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
    }

    @Test
    void importCourseQuestionsShouldRejectMissingQuestionIds() {
        FlashcardSet set = flashcardSet();
        UUID questionId = UUID.randomUUID();
        when(flashcardSetRepository.findByIdAndDeletedAtIsNull(set.getId())).thenReturn(Optional.of(set));
        when(questionRepository.findAllById(List.of(questionId))).thenReturn(List.of());

        assertThatThrownBy(() -> importService.importCourseQuestions(
                set.getId(),
                new ImportCourseQuestionsRequest(List.of(questionId))
        ))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
    }

    @Test
    void importCourseQuestionsShouldRejectNonApprovedQuestion() {
        FlashcardSet set = flashcardSet();
        Question draftQuestion = question(set.getLesson().getCourse().getId(), QuestionStatus.DRAFT);
        when(flashcardSetRepository.findByIdAndDeletedAtIsNull(set.getId())).thenReturn(Optional.of(set));
        when(questionRepository.findAllById(List.of(draftQuestion.getId()))).thenReturn(List.of(draftQuestion));
        when(stagingCardRepository.findImportedSourceQuestionIds(any(), any(), any())).thenReturn(List.of());

        assertThatThrownBy(() -> importService.importCourseQuestions(
                set.getId(),
                new ImportCourseQuestionsRequest(List.of(draftQuestion.getId()))
        ))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
    }

    @Test
    void importCourseQuestionsShouldRejectAlreadyImportedQuestionForSet() {
        FlashcardSet set = flashcardSet();
        Question approvedQuestion = question(set.getLesson().getCourse().getId(), QuestionStatus.APPROVED);
        when(flashcardSetRepository.findByIdAndDeletedAtIsNull(set.getId())).thenReturn(Optional.of(set));
        when(questionRepository.findAllById(List.of(approvedQuestion.getId()))).thenReturn(List.of(approvedQuestion));
        when(stagingCardRepository.findImportedSourceQuestionIds(any(), any(), any()))
                .thenReturn(List.of(approvedQuestion.getId()));

        assertThatThrownBy(() -> importService.importCourseQuestions(
                set.getId(),
                new ImportCourseQuestionsRequest(List.of(approvedQuestion.getId()))
        ))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT));
    }

    @Test
    void importCourseQuestionsShouldPersistApprovedQuestionInCourse() {
        FlashcardSet set = flashcardSet();
        Question approvedQuestion = question(set.getLesson().getCourse().getId(), QuestionStatus.APPROVED);
        when(flashcardSetRepository.findByIdAndDeletedAtIsNull(set.getId())).thenReturn(Optional.of(set));
        when(questionRepository.findAllById(List.of(approvedQuestion.getId()))).thenReturn(List.of(approvedQuestion));
        when(stagingCardRepository.findImportedSourceQuestionIds(any(), any(), any())).thenReturn(List.of());
        QuestionAnswer correctAnswer = new QuestionAnswer();
        correctAnswer.setId(UUID.randomUUID());
        correctAnswer.setQuestionId(approvedQuestion.getId());
        correctAnswer.setAnswerText("Correct");
        correctAnswer.setIsCorrect(true);
        correctAnswer.setOrderIndex(0);
        when(questionAnswerRepository.findByQuestionIdInOrderByQuestionIdAscOrderIndexAsc(any()))
                .thenReturn(List.of(correctAnswer));
        when(currentUserService.requireAuthenticatedUser()).thenReturn(new UserAccount());
        when(stagingBatchRepository.save(any())).thenAnswer(invocation -> {
            FlashcardStagingBatch batch = invocation.getArgument(0);
            batch.setId(UUID.randomUUID());
            return batch;
        });
        when(stagingCardRepository.saveAll(any())).thenAnswer(invocation -> {
            List<FlashcardStagingCard> cards = invocation.getArgument(0);
            cards.forEach(card -> card.setId(UUID.randomUUID()));
            return cards;
        });

        var response = importService.importCourseQuestions(
                set.getId(),
                new ImportCourseQuestionsRequest(List.of(approvedQuestion.getId()))
        );

        assertThat(response.cards()).hasSize(1);
        verify(stagingBatchRepository).save(any());
        verify(stagingCardRepository).saveAll(any());
    }

    private FlashcardSet flashcardSet() {
        Course course = new Course();
        course.setId(UUID.randomUUID());
        course.setTitle("Course");
        course.setSlug("course");

        Lesson lesson = new Lesson();
        lesson.setId(UUID.randomUUID());
        lesson.setCourse(course);
        lesson.setTitle("Flashcards");
        lesson.setType(LessonType.FLASHCARD);

        FlashcardSet set = new FlashcardSet();
        set.setId(UUID.randomUUID());
        set.setLesson(lesson);
        return set;
    }

    private Question question(UUID courseId, QuestionStatus status) {
        Question question = new Question();
        question.setId(UUID.randomUUID());
        question.setCourseId(courseId);
        question.setModuleId(UUID.randomUUID());
        question.setQuestionText("Question?");
        question.setQuestionType(QuestionType.MULTIPLE_CHOICE);
        question.setStatus(status);
        return question;
    }
}
