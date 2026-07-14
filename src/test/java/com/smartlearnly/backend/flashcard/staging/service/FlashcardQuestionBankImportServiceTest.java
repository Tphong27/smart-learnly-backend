package com.smartlearnly.backend.flashcard.staging.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.course.entity.Course;
import com.smartlearnly.backend.flashcard.entity.FlashcardSet;
import com.smartlearnly.backend.flashcard.repository.FlashcardSetRepository;
import com.smartlearnly.backend.flashcard.staging.dto.AdminFlashcardStagingDtos.ImportQuestionBankRequest;
import com.smartlearnly.backend.flashcard.staging.repository.FlashcardStagingCardRepository;
import com.smartlearnly.backend.learning.lesson.entity.Lesson;
import com.smartlearnly.backend.learning.lesson.entity.LessonType;
import com.smartlearnly.backend.question.entity.Question;
import com.smartlearnly.backend.question.entity.QuestionStatus;
import com.smartlearnly.backend.question.entity.QuestionType;
import com.smartlearnly.backend.question.repository.QuestionRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FlashcardQuestionBankImportServiceTest {
    @Mock
    private FlashcardSetRepository flashcardSetRepository;
    @Mock
    private QuestionRepository questionRepository;
    @Mock
    private FlashcardStagingCardRepository stagingCardRepository;
    @Mock
    private AdminFlashcardStagingService adminFlashcardStagingService;

    private FlashcardQuestionBankImportService importService;

    @BeforeEach
    void setUp() {
        importService = new FlashcardQuestionBankImportService(
                flashcardSetRepository,
                questionRepository,
                stagingCardRepository,
                adminFlashcardStagingService
        );
    }

    @Test
    void importQuestionBankShouldRejectDuplicateIds() {
        FlashcardSet set = flashcardSet();
        UUID questionId = UUID.randomUUID();
        when(flashcardSetRepository.findByIdAndDeletedAtIsNull(set.getId())).thenReturn(Optional.of(set));

        assertThatThrownBy(() -> importService.importQuestionBank(
                set.getId(),
                new ImportQuestionBankRequest(List.of(questionId, questionId))
        ))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
        verify(adminFlashcardStagingService, never()).importQuestionBank(any(), any());
    }

    @Test
    void importQuestionBankShouldRejectMissingQuestionIds() {
        FlashcardSet set = flashcardSet();
        UUID questionId = UUID.randomUUID();
        when(flashcardSetRepository.findByIdAndDeletedAtIsNull(set.getId())).thenReturn(Optional.of(set));
        when(questionRepository.findAllById(List.of(questionId))).thenReturn(List.of());

        assertThatThrownBy(() -> importService.importQuestionBank(
                set.getId(),
                new ImportQuestionBankRequest(List.of(questionId))
        ))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
        verify(adminFlashcardStagingService, never()).importQuestionBank(any(), any());
    }

    @Test
    void importQuestionBankShouldRejectNonApprovedQuestion() {
        FlashcardSet set = flashcardSet();
        Question draftQuestion = question(set.getLesson().getCourse().getId(), QuestionStatus.DRAFT);
        when(flashcardSetRepository.findByIdAndDeletedAtIsNull(set.getId())).thenReturn(Optional.of(set));
        when(questionRepository.findAllById(List.of(draftQuestion.getId()))).thenReturn(List.of(draftQuestion));
        when(stagingCardRepository.findImportedSourceQuestionIds(any(), any(), any())).thenReturn(List.of());

        assertThatThrownBy(() -> importService.importQuestionBank(
                set.getId(),
                new ImportQuestionBankRequest(List.of(draftQuestion.getId()))
        ))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
        verify(adminFlashcardStagingService, never()).importQuestionBank(any(), any());
    }

    @Test
    void importQuestionBankShouldRejectAlreadyImportedQuestionForSet() {
        FlashcardSet set = flashcardSet();
        Question approvedQuestion = question(set.getLesson().getCourse().getId(), QuestionStatus.APPROVED);
        when(flashcardSetRepository.findByIdAndDeletedAtIsNull(set.getId())).thenReturn(Optional.of(set));
        when(questionRepository.findAllById(List.of(approvedQuestion.getId()))).thenReturn(List.of(approvedQuestion));
        when(stagingCardRepository.findImportedSourceQuestionIds(any(), any(), any()))
                .thenReturn(List.of(approvedQuestion.getId()));

        assertThatThrownBy(() -> importService.importQuestionBank(
                set.getId(),
                new ImportQuestionBankRequest(List.of(approvedQuestion.getId()))
        ))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT));
        verify(adminFlashcardStagingService, never()).importQuestionBank(any(), any());
    }

    @Test
    void importQuestionBankShouldDelegateApprovedQuestionInCourse() {
        FlashcardSet set = flashcardSet();
        Question approvedQuestion = question(set.getLesson().getCourse().getId(), QuestionStatus.APPROVED);
        when(flashcardSetRepository.findByIdAndDeletedAtIsNull(set.getId())).thenReturn(Optional.of(set));
        when(questionRepository.findAllById(List.of(approvedQuestion.getId()))).thenReturn(List.of(approvedQuestion));
        when(stagingCardRepository.findImportedSourceQuestionIds(any(), any(), any())).thenReturn(List.of());

        importService.importQuestionBank(
                set.getId(),
                new ImportQuestionBankRequest(List.of(approvedQuestion.getId()))
        );

        verify(adminFlashcardStagingService).importQuestionBank(set.getId(), new ImportQuestionBankRequest(List.of(approvedQuestion.getId())));
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
        question.setQuestionBankId(UUID.randomUUID());
        question.setQuestionText("Question?");
        question.setQuestionType(QuestionType.MULTIPLE_CHOICE);
        question.setStatus(status);
        return question;
    }
}
