package com.smartlearnly.backend.flashcard.staging.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.course.access.service.CourseAccessService;
import com.smartlearnly.backend.course.entity.Course;
import com.smartlearnly.backend.curriculum.entity.CurriculumLesson;
import com.smartlearnly.backend.curriculum.entity.CurriculumScope;
import com.smartlearnly.backend.curriculum.entity.CurriculumSection;
import com.smartlearnly.backend.curriculum.entity.CurriculumStatus;
import com.smartlearnly.backend.curriculum.entity.CurriculumVersion;
import com.smartlearnly.backend.curriculum.repository.CurriculumLessonRepository;
import com.smartlearnly.backend.curriculum.service.TrainerClassCurriculumService;
import com.smartlearnly.backend.flashcard.entity.FlashcardCard;
import com.smartlearnly.backend.flashcard.repository.FlashcardCardRepository;
import com.smartlearnly.backend.flashcard.entity.FlashcardSet;
import com.smartlearnly.backend.flashcard.repository.FlashcardSetRepository;
import com.smartlearnly.backend.flashcard.staging.dto.AdminFlashcardStagingDtos.ImportCourseQuestionsRequest;
import com.smartlearnly.backend.flashcard.staging.dto.AdminFlashcardStagingDtos.SourceQuestionResponse;
import com.smartlearnly.backend.flashcard.staging.dto.AdminFlashcardStagingDtos.TemporaryFlashcardCandidateBatchResponse;
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
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.util.ReflectionTestUtils;

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
    @Mock
    private CurriculumLessonRepository curriculumLessonRepository;
    @Mock
    private CourseAccessService courseAccessService;
    @Mock
    private TrainerClassCurriculumService trainerClassCurriculumService;

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
        ReflectionTestUtils.setField(importService, "curriculumLessonRepository", curriculumLessonRepository);
        ReflectionTestUtils.setField(importService, "courseAccessService", courseAccessService);
        ReflectionTestUtils.setField(importService, "trainerClassCurriculumService", trainerClassCurriculumService);
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

    @Test
    void listSourceQuestionsReturnsSameCourseQuestionsWithAnswersAndImportedFlags() {
        FlashcardSet set = flashcardSet();
        UUID courseId = set.getLesson().getCourse().getId();
        UUID moduleId = UUID.randomUUID();
        Question importedQuestion = question(courseId, QuestionStatus.APPROVED, QuestionType.SINGLE_CHOICE, moduleId, "Imported?");
        Question availableQuestion = question(courseId, QuestionStatus.APPROVED, QuestionType.ESSAY, moduleId, "Available?");
        Question otherCourseQuestion = question(UUID.randomUUID(), QuestionStatus.APPROVED, QuestionType.MULTIPLE_CHOICE, moduleId, "Other?");
        QuestionAnswer distractor = answer(importedQuestion.getId(), "No", false, 1);
        QuestionAnswer correct = answer(importedQuestion.getId(), "Yes", true, 0);
        QuestionAnswer availableCorrect = answer(availableQuestion.getId(), "Ready", true, 0);
        when(flashcardSetRepository.findByIdAndDeletedAtIsNull(set.getId())).thenReturn(Optional.of(set));
        when(questionRepository.searchForAdmin(any(), any(), any(), any(), any(), anyBoolean(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(importedQuestion, otherCourseQuestion, availableQuestion)));
        when(questionAnswerRepository.findByQuestionIdInOrderByQuestionIdAscOrderIndexAsc(
                List.of(importedQuestion.getId(), availableQuestion.getId())))
                .thenReturn(List.of(distractor, correct, availableCorrect));
        when(stagingCardRepository.findImportedSourceQuestionIds(
                set.getId(),
                List.of(importedQuestion.getId(), availableQuestion.getId()),
                java.util.Set.of("draft", "approved")))
                .thenReturn(List.of(importedQuestion.getId()));
        when(flashcardCardRepository.findActiveBySetIdOrderByOrderIndex(set.getId())).thenReturn(List.of());

        List<SourceQuestionResponse> response = importService.listSourceQuestions(
                set.getId(),
                moduleId,
                "imported",
                (short) 2,
                "approved"
        );

        assertThat(response).hasSize(2);
        assertThat(response).extracting(SourceQuestionResponse::questionId)
                .containsExactly(importedQuestion.getId(), availableQuestion.getId());
        assertThat(response).extracting(SourceQuestionResponse::imported).containsExactly(true, false);
        assertThat(response).extracting(SourceQuestionResponse::eligibilityStatus)
                .containsExactly("ALREADY_IMPORTED", "AVAILABLE");
        assertThat(response.get(0).answers()).hasSize(2);
        assertThat(response.get(0).answers()).extracting(answer -> answer.answerText()).containsExactly("Yes", "No");
        assertThat(response.get(0).correctAnswers()).containsExactly("Yes");
        verify(courseAccessService).requireReadableCourse(courseId);
    }

    @Test
    void listSourceQuestionsClassifiesLinkedCurrentMatchesAndAvailableQuestions() {
        FlashcardSet set = flashcardSet();
        UUID courseId = set.getLesson().getCourse().getId();
        UUID moduleId = UUID.randomUUID();
        Question linked = question(courseId, QuestionStatus.APPROVED, QuestionType.ESSAY, moduleId, "Linked?");
        Question currentMatch = question(courseId, QuestionStatus.APPROVED, QuestionType.ESSAY, moduleId,
                "<p> Existing&nbsp;front? </p>");
        Question available = question(courseId, QuestionStatus.APPROVED, QuestionType.ESSAY, moduleId, "Fresh?");
        when(flashcardSetRepository.findByIdAndDeletedAtIsNull(set.getId())).thenReturn(Optional.of(set));
        when(questionRepository.searchForAdmin(any(), any(), any(), any(), any(), anyBoolean(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(linked, currentMatch, available)));
        when(questionAnswerRepository.findByQuestionIdInOrderByQuestionIdAscOrderIndexAsc(
                List.of(linked.getId(), currentMatch.getId(), available.getId())))
                .thenReturn(List.of(
                        answer(linked.getId(), "Linked answer", true, 0),
                        answer(currentMatch.getId(), "<strong>Existing answer</strong>", true, 0),
                        answer(available.getId(), "Fresh answer", true, 0)));
        when(stagingCardRepository.findImportedSourceQuestionIds(
                set.getId(),
                List.of(linked.getId(), currentMatch.getId(), available.getId()),
                java.util.Set.of("draft", "approved")))
                .thenReturn(List.of(linked.getId()));
        when(flashcardCardRepository.findActiveBySetIdOrderByOrderIndex(set.getId()))
                .thenReturn(List.of(flashcardCard(set, "Existing front?", "existing answer")));

        List<SourceQuestionResponse> response = importService.listSourceQuestions(
                set.getId(),
                moduleId,
                null,
                null,
                "approved"
        );

        assertThat(response).extracting(SourceQuestionResponse::eligibilityStatus)
                .containsExactly("ALREADY_IMPORTED", "MATCHES_CURRENT_FLASHCARDS", "AVAILABLE");
        assertThat(response).extracting(SourceQuestionResponse::eligibilityReason)
                .containsExactly("Already imported", "Matches Current Flashcards", null);
        assertThat(response).extracting(SourceQuestionResponse::imported)
                .containsExactly(true, false, false);
        verify(flashcardCardRepository, times(1)).findActiveBySetIdOrderByOrderIndex(set.getId());
    }

    @Test
    void listSourceQuestionsReturnsEmptyWithoutAnswerOrImportedLookups() {
        FlashcardSet set = flashcardSet();
        when(flashcardSetRepository.findByIdAndDeletedAtIsNull(set.getId())).thenReturn(Optional.of(set));
        when(questionRepository.searchForAdmin(any(), any(), any(), any(), any(), anyBoolean(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        List<SourceQuestionResponse> response = importService.listSourceQuestions(set.getId(), null, null, null, null);

        assertThat(response).isEmpty();
        verify(questionAnswerRepository, never()).findByQuestionIdInOrderByQuestionIdAscOrderIndexAsc(anyList());
        verify(stagingCardRepository, never()).findImportedSourceQuestionIds(any(), any(), any());
    }

    @Test
    void listSourceQuestionsRejectsInvalidStatusBeforeSearch() {
        FlashcardSet set = flashcardSet();
        when(flashcardSetRepository.findByIdAndDeletedAtIsNull(set.getId())).thenReturn(Optional.of(set));

        assertBusinessException(
                () -> importService.listSourceQuestions(set.getId(), null, null, null, "ready-ish"),
                ErrorCode.INVALID_REQUEST,
                "Question status is invalid"
        );

        verify(questionRepository, never()).searchForAdmin(any(), any(), any(), any(), any(), anyBoolean(), any(), any());
    }

    @Test
    void previewCourseQuestionsBuildsTemporaryCandidatesWithOptionsAndNormalizedContent() {
        FlashcardSet set = flashcardSet();
        UUID courseId = set.getLesson().getCourse().getId();
        Question single = question(courseId, QuestionStatus.APPROVED, QuestionType.SINGLE_CHOICE, UUID.randomUUID(),
                "<p>Pick&nbsp;<strong>one</strong>.</p>");
        single.setExplanation("<div>Use&nbsp;one.</div>");
        Question multi = question(courseId, QuestionStatus.APPROVED, QuestionType.MULTIPLE_CHOICE, UUID.randomUUID(),
                "Pick many.");
        Question trueFalse = question(courseId, QuestionStatus.APPROVED, QuestionType.TRUE_FALSE, UUID.randomUUID(),
                "True or false?");
        when(flashcardSetRepository.findByIdAndDeletedAtIsNull(set.getId())).thenReturn(Optional.of(set));
        when(questionRepository.findAllById(List.of(single.getId(), multi.getId(), trueFalse.getId())))
                .thenReturn(List.of(trueFalse, single, multi));
        when(questionAnswerRepository.findByQuestionIdInOrderByQuestionIdAscOrderIndexAsc(
                List.of(single.getId(), multi.getId(), trueFalse.getId())))
                .thenReturn(List.of(
                        answer(single.getId(), "<p>First&nbsp;choice</p>", true, 1),
                        answer(single.getId(), "Distractor", false, 0),
                        answer(multi.getId(), "A", true, 1),
                        answer(multi.getId(), "B", true, 0),
                        answer(trueFalse.getId(), "False", false, 1),
                        answer(trueFalse.getId(), "True", true, 0)));
        when(flashcardCardRepository.findActiveBySetIdOrderByOrderIndex(set.getId())).thenReturn(List.of());

        TemporaryFlashcardCandidateBatchResponse response = importService.previewCourseQuestions(
                set.getId(),
                new ImportCourseQuestionsRequest(List.of(single.getId(), multi.getId(), trueFalse.getId()))
        );

        assertThat(response.sourceType()).isEqualTo("COURSE_QUESTIONS");
        assertThat(response.requestedCount()).isEqualTo(3);
        assertThat(response.cards()).hasSize(3);
        assertThat(response.cards().get(0).frontText())
                .isEqualTo("Pick one.\n\nOptions:\n1. Distractor\n2. First choice");
        assertThat(response.cards().get(0).backText()).isEqualTo("First choice");
        assertThat(response.cards().get(0).explanation()).isEqualTo("Use one.");
        assertThat(response.cards().get(0).sourceExcerpt()).isEqualTo("Pick one.");
        assertThat(response.cards().get(1).frontText()).isEqualTo("Pick many.\n\nOptions:\n1. B\n2. A");
        assertThat(response.cards().get(1).backText()).isEqualTo("B\nA");
        assertThat(response.cards().get(2).frontText()).isEqualTo("True or false?\n\nOptions:\n1. True\n2. False");
        assertThat(response.cards()).extracting(card -> card.selected()).containsOnly(true);
    }

    @Test
    void previewCourseQuestionsMarksCurrentAndCandidateDuplicates() {
        FlashcardSet set = flashcardSet();
        UUID courseId = set.getLesson().getCourse().getId();
        Question duplicateCurrent = question(courseId, QuestionStatus.APPROVED, QuestionType.ESSAY, UUID.randomUUID(), "Current?");
        Question firstCandidate = question(courseId, QuestionStatus.APPROVED, QuestionType.ESSAY, UUID.randomUUID(), "Same?");
        Question secondCandidate = question(courseId, QuestionStatus.APPROVED, QuestionType.ESSAY, UUID.randomUUID(), " same? ");
        Question unique = question(courseId, QuestionStatus.APPROVED, QuestionType.ESSAY, UUID.randomUUID(), "Unique?");
        when(flashcardSetRepository.findByIdAndDeletedAtIsNull(set.getId())).thenReturn(Optional.of(set));
        when(questionRepository.findAllById(List.of(
                duplicateCurrent.getId(),
                firstCandidate.getId(),
                secondCandidate.getId(),
                unique.getId())))
                .thenReturn(List.of(duplicateCurrent, firstCandidate, secondCandidate, unique));
        when(questionAnswerRepository.findByQuestionIdInOrderByQuestionIdAscOrderIndexAsc(anyList()))
                .thenReturn(List.of(
                        answer(duplicateCurrent.getId(), "Answer", true, 0),
                        answer(firstCandidate.getId(), "Answer", true, 0),
                        answer(secondCandidate.getId(), " answer ", true, 0),
                        answer(unique.getId(), "Fresh", true, 0)));
        when(flashcardCardRepository.findActiveBySetIdOrderByOrderIndex(set.getId()))
                .thenReturn(List.of(flashcardCard(set, "<p>Current?</p>", "answer")));

        TemporaryFlashcardCandidateBatchResponse response = importService.previewCourseQuestions(
                set.getId(),
                new ImportCourseQuestionsRequest(List.of(
                        duplicateCurrent.getId(),
                        firstCandidate.getId(),
                        secondCandidate.getId(),
                        unique.getId()))
        );

        assertThat(response.cards()).hasSize(4);
        assertThat(response.cards().get(0).duplicate()).isTrue();
        assertThat(response.cards().get(0).selected()).isFalse();
        assertThat(response.cards().get(0).issues()).containsExactly("Matches Current Flashcards");
        assertThat(response.cards().get(1).duplicate()).isTrue();
        assertThat(response.cards().get(1).issues()).containsExactly("Duplicate in candidates");
        assertThat(response.cards().get(2).duplicate()).isTrue();
        assertThat(response.cards().get(3).duplicate()).isFalse();
        assertThat(response.cards().get(3).selected()).isTrue();
    }

    @Test
    void previewCourseQuestionsRejectsInvalidRequestsAndQuestions() {
        FlashcardSet set = flashcardSet();
        UUID missingQuestionId = UUID.randomUUID();
        Question otherCourseQuestion = question(UUID.randomUUID(), QuestionStatus.APPROVED);
        Question draftQuestion = question(set.getLesson().getCourse().getId(), QuestionStatus.DRAFT);
        Question noCorrectAnswer = question(set.getLesson().getCourse().getId(), QuestionStatus.APPROVED);
        when(flashcardSetRepository.findByIdAndDeletedAtIsNull(set.getId())).thenReturn(Optional.of(set));

        assertBusinessException(
                () -> importService.previewCourseQuestions(
                        set.getId(),
                        new ImportCourseQuestionsRequest(List.of(missingQuestionId, missingQuestionId))),
                ErrorCode.INVALID_REQUEST,
                "Question import contains duplicate ids"
        );

        when(questionRepository.findAllById(List.of(missingQuestionId))).thenReturn(List.of());
        assertBusinessException(
                () -> importService.previewCourseQuestions(
                        set.getId(),
                        new ImportCourseQuestionsRequest(List.of(missingQuestionId))),
                ErrorCode.RESOURCE_NOT_FOUND,
                "One or more questions were not found"
        );

        when(questionRepository.findAllById(List.of(otherCourseQuestion.getId()))).thenReturn(List.of(otherCourseQuestion));
        assertBusinessException(
                () -> importService.previewCourseQuestions(
                        set.getId(),
                        new ImportCourseQuestionsRequest(List.of(otherCourseQuestion.getId()))),
                ErrorCode.INVALID_REQUEST,
                "Question must belong to the same course as the flashcard set"
        );

        when(questionRepository.findAllById(List.of(draftQuestion.getId()))).thenReturn(List.of(draftQuestion));
        assertBusinessException(
                () -> importService.previewCourseQuestions(
                        set.getId(),
                        new ImportCourseQuestionsRequest(List.of(draftQuestion.getId()))),
                ErrorCode.INVALID_REQUEST,
                "Only approved questions can be imported"
        );

        when(questionRepository.findAllById(List.of(noCorrectAnswer.getId()))).thenReturn(List.of(noCorrectAnswer));
        when(questionAnswerRepository.findByQuestionIdInOrderByQuestionIdAscOrderIndexAsc(List.of(noCorrectAnswer.getId())))
                .thenReturn(List.of(answer(noCorrectAnswer.getId(), "Wrong", false, 0)));
        assertBusinessException(
                () -> importService.previewCourseQuestions(
                        set.getId(),
                        new ImportCourseQuestionsRequest(List.of(noCorrectAnswer.getId()))),
                ErrorCode.INVALID_REQUEST,
                "Question must have at least one correct answer"
        );
    }

    @Test
    void previewCourseQuestionsRejectsBlankNormalizedQuestionText() {
        FlashcardSet set = flashcardSet();
        Question blankQuestion = question(set.getLesson().getCourse().getId(), QuestionStatus.APPROVED);
        blankQuestion.setQuestionText("<p>&nbsp;</p>");
        when(flashcardSetRepository.findByIdAndDeletedAtIsNull(set.getId())).thenReturn(Optional.of(set));
        when(questionRepository.findAllById(List.of(blankQuestion.getId()))).thenReturn(List.of(blankQuestion));
        when(questionAnswerRepository.findByQuestionIdInOrderByQuestionIdAscOrderIndexAsc(List.of(blankQuestion.getId())))
                .thenReturn(List.of(answer(blankQuestion.getId(), "Correct", true, 0)));

        assertBusinessException(
                () -> importService.previewCourseQuestions(
                        set.getId(),
                        new ImportCourseQuestionsRequest(List.of(blankQuestion.getId()))),
                ErrorCode.INVALID_REQUEST,
                "Question text is required"
        );
    }

    @Test
    void importCourseQuestionsPersistsNormalizedCardsWithBatchTarget() {
        FlashcardSet set = flashcardSet();
        Question approvedQuestion = question(set.getLesson().getCourse().getId(), QuestionStatus.APPROVED);
        approvedQuestion.setQuestionText("<p>What&nbsp;is <strong>HTML</strong>?</p>");
        approvedQuestion.setExplanation("<div>Markup&nbsp;language.</div>");
        when(flashcardSetRepository.findByIdAndDeletedAtIsNull(set.getId())).thenReturn(Optional.of(set));
        when(questionRepository.findAllById(List.of(approvedQuestion.getId()))).thenReturn(List.of(approvedQuestion));
        when(stagingCardRepository.findImportedSourceQuestionIds(any(), any(), any())).thenReturn(List.of());
        when(questionAnswerRepository.findByQuestionIdInOrderByQuestionIdAscOrderIndexAsc(List.of(approvedQuestion.getId())))
                .thenReturn(List.of(
                        answer(approvedQuestion.getId(), "Wrong", false, 0),
                        answer(approvedQuestion.getId(), "<p>Correct&nbsp;answer</p>", true, 1)));
        when(currentUserService.requireAuthenticatedUser()).thenReturn(actor());
        when(stagingBatchRepository.save(any())).thenAnswer(invocation -> {
            FlashcardStagingBatch batch = invocation.getArgument(0);
            batch.setId(UUID.randomUUID());
            batch.setCreatedAt(Instant.now());
            batch.setUpdatedAt(Instant.now());
            return batch;
        });
        when(stagingCardRepository.saveAll(any())).thenAnswer(invocation -> {
            List<FlashcardStagingCard> cards = invocation.getArgument(0);
            cards.forEach(card -> {
                card.setId(UUID.randomUUID());
                card.setCreatedAt(Instant.now());
                card.setUpdatedAt(Instant.now());
            });
            return cards;
        });

        var response = importService.importCourseQuestions(
                set.getId(),
                new ImportCourseQuestionsRequest(List.of(approvedQuestion.getId()))
        );

        assertThat(response.lessonId()).isEqualTo(set.getLesson().getId());
        assertThat(response.curriculumLessonId()).isNull();
        assertThat(response.sourceName()).isEqualTo("Course - Course questions");
        assertThat(response.cards()).hasSize(1);
        assertThat(response.cards().get(0).frontText())
                .isEqualTo("What is HTML?\n\nOptions:\n1. Wrong\n2. Correct answer");
        assertThat(response.cards().get(0).backText()).isEqualTo("Correct answer");
        assertThat(response.cards().get(0).explanation()).isEqualTo("Markup language.");
        assertThat(response.cards().get(0).sourceExcerpt()).isEqualTo("What is HTML?");
    }

    @Test
    void listSourceQuestionsRejectsMissingFlashcardSet() {
        UUID setId = UUID.randomUUID();
        when(flashcardSetRepository.findByIdAndDeletedAtIsNull(setId)).thenReturn(Optional.empty());

        assertBusinessException(
                () -> importService.listSourceQuestions(setId, null, null, null, null),
                ErrorCode.RESOURCE_NOT_FOUND,
                "Flashcard set was not found"
        );

        verify(questionRepository, never()).searchForAdmin(any(), any(), any(), any(), any(), anyBoolean(), any(), any());
    }

    @Test
    void listSourceQuestionsRejectsInvalidLegacyLessonRelationships() {
        FlashcardSet noCourseSet = flashcardSet();
        noCourseSet.getLesson().setCourse(null);
        when(flashcardSetRepository.findByIdAndDeletedAtIsNull(noCourseSet.getId())).thenReturn(Optional.of(noCourseSet));
        assertBusinessException(
                () -> importService.listSourceQuestions(noCourseSet.getId(), null, null, null, null),
                ErrorCode.RESOURCE_NOT_FOUND,
                "Flashcard lesson was not found"
        );

        FlashcardSet deletedCourseSet = flashcardSet();
        deletedCourseSet.getLesson().getCourse().setDeletedAt(Instant.now());
        when(flashcardSetRepository.findByIdAndDeletedAtIsNull(deletedCourseSet.getId()))
                .thenReturn(Optional.of(deletedCourseSet));
        assertBusinessException(
                () -> importService.listSourceQuestions(deletedCourseSet.getId(), null, null, null, null),
                ErrorCode.RESOURCE_NOT_FOUND,
                "Flashcard lesson was not found"
        );

        FlashcardSet videoLessonSet = flashcardSet();
        videoLessonSet.getLesson().setType(LessonType.VIDEO);
        when(flashcardSetRepository.findByIdAndDeletedAtIsNull(videoLessonSet.getId())).thenReturn(Optional.of(videoLessonSet));
        assertBusinessException(
                () -> importService.listSourceQuestions(videoLessonSet.getId(), null, null, null, null),
                ErrorCode.INVALID_REQUEST,
                "Flashcard set is not linked to a flashcard lesson"
        );

        verify(courseAccessService, never()).requireReadableCourse(any());
    }

    @Test
    void listSourceQuestionsRequiresReadableCourseForValidLegacyLesson() {
        FlashcardSet set = flashcardSet();
        when(flashcardSetRepository.findByIdAndDeletedAtIsNull(set.getId())).thenReturn(Optional.of(set));
        when(questionRepository.searchForAdmin(any(), any(), any(), any(), any(), anyBoolean(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        importService.listSourceQuestions(set.getId(), null, null, null, null);

        verify(courseAccessService).requireReadableCourse(set.getLesson().getCourse().getId());
    }

    @Test
    void listSourceQuestionsRejectsInvalidCurriculumLessonRelationships() {
        FlashcardSet missingCurriculumIdSet = curriculumFlashcardSet(course());
        missingCurriculumIdSet.setCurriculumLessonId(null);
        when(flashcardSetRepository.findByIdAndDeletedAtIsNull(missingCurriculumIdSet.getId()))
                .thenReturn(Optional.of(missingCurriculumIdSet));
        assertBusinessException(
                () -> importService.listSourceQuestions(missingCurriculumIdSet.getId(), null, null, null, null),
                ErrorCode.RESOURCE_NOT_FOUND,
                "Flashcard lesson was not found"
        );

        FlashcardSet unresolvedSet = curriculumFlashcardSet(course());
        when(flashcardSetRepository.findByIdAndDeletedAtIsNull(unresolvedSet.getId())).thenReturn(Optional.of(unresolvedSet));
        when(curriculumLessonRepository.findById(unresolvedSet.getCurriculumLessonId())).thenReturn(Optional.empty());
        assertBusinessException(
                () -> importService.listSourceQuestions(unresolvedSet.getId(), null, null, null, null),
                ErrorCode.RESOURCE_NOT_FOUND,
                "Flashcard lesson was not found"
        );

        FlashcardSet videoCurriculumSet = curriculumFlashcardSet(course());
        when(flashcardSetRepository.findByIdAndDeletedAtIsNull(videoCurriculumSet.getId()))
                .thenReturn(Optional.of(videoCurriculumSet));
        when(curriculumLessonRepository.findById(videoCurriculumSet.getCurriculumLessonId()))
                .thenReturn(Optional.of(curriculumLesson(CurriculumScope.MASTER, null, LessonType.VIDEO)));
        assertBusinessException(
                () -> importService.listSourceQuestions(videoCurriculumSet.getId(), null, null, null, null),
                ErrorCode.RESOURCE_NOT_FOUND,
                "Flashcard lesson was not found"
        );

        verify(courseAccessService, never()).requireReadableCourse(any());
    }

    @Test
    void listSourceQuestionsUsesCourseScopedCurriculumAccess() {
        Course course = course();
        FlashcardSet set = curriculumFlashcardSet(course);
        CurriculumLesson curriculumLesson = curriculumLesson(CurriculumScope.MASTER, null, LessonType.FLASHCARD);
        when(flashcardSetRepository.findByIdAndDeletedAtIsNull(set.getId())).thenReturn(Optional.of(set));
        when(curriculumLessonRepository.findById(set.getCurriculumLessonId())).thenReturn(Optional.of(curriculumLesson));
        when(questionRepository.searchForAdmin(any(), any(), any(), any(), any(), anyBoolean(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        importService.listSourceQuestions(set.getId(), null, null, null, null);

        verify(courseAccessService).requireReadableCourse(course.getId());
        verify(trainerClassCurriculumService, never()).requireOwnedClassLessonForRead(any(), any());
    }

    @Test
    void listSourceQuestionsUsesClassScopedCurriculumAccess() {
        UUID classId = UUID.randomUUID();
        FlashcardSet set = curriculumFlashcardSet(course());
        CurriculumLesson curriculumLesson = curriculumLesson(CurriculumScope.CLASS, classId, LessonType.FLASHCARD);
        when(flashcardSetRepository.findByIdAndDeletedAtIsNull(set.getId())).thenReturn(Optional.of(set));
        when(curriculumLessonRepository.findById(set.getCurriculumLessonId())).thenReturn(Optional.of(curriculumLesson));
        when(questionRepository.searchForAdmin(any(), any(), any(), any(), any(), anyBoolean(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        importService.listSourceQuestions(set.getId(), null, null, null, null);

        verify(trainerClassCurriculumService).requireOwnedClassLessonForRead(classId, set.getCurriculumLessonId());
        verify(courseAccessService, never()).requireReadableCourse(any());
    }

    @Test
    void listSourceQuestionsRejectsClassScopedCurriculumWithoutClassId() {
        FlashcardSet set = curriculumFlashcardSet(course());
        CurriculumLesson curriculumLesson = curriculumLesson(CurriculumScope.CLASS, null, LessonType.FLASHCARD);
        when(flashcardSetRepository.findByIdAndDeletedAtIsNull(set.getId())).thenReturn(Optional.of(set));
        when(curriculumLessonRepository.findById(set.getCurriculumLessonId())).thenReturn(Optional.of(curriculumLesson));

        assertBusinessException(
                () -> importService.listSourceQuestions(set.getId(), null, null, null, null),
                ErrorCode.CONFLICT,
                "Class curriculum is inconsistent"
        );

        verify(trainerClassCurriculumService, never()).requireOwnedClassLessonForRead(any(), any());
    }

    private void assertBusinessException(Runnable action, ErrorCode errorCode, String message) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(errorCode))
                .hasMessage(message);
    }

    private FlashcardSet flashcardSet() {
        Course course = course();

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

    private FlashcardSet curriculumFlashcardSet(Course course) {
        FlashcardSet set = new FlashcardSet();
        set.setId(UUID.randomUUID());
        set.setCurriculumLessonId(UUID.randomUUID());
        set.setCourse(course);
        set.setTitle("Curriculum flashcards");
        return set;
    }

    private CurriculumLesson curriculumLesson(CurriculumScope scope, UUID classId, LessonType type) {
        CurriculumVersion version = new CurriculumVersion();
        version.setId(UUID.randomUUID());
        version.setCourseId(UUID.randomUUID());
        version.setClassId(classId);
        version.setScope(scope);
        version.setStatus(CurriculumStatus.PUBLISHED);

        CurriculumSection section = new CurriculumSection();
        section.setId(UUID.randomUUID());
        section.setTitle("Section");
        section.setSortOrder(0);
        version.addSection(section);

        CurriculumLesson lesson = new CurriculumLesson();
        lesson.setId(UUID.randomUUID());
        lesson.setLessonIdentityId(UUID.randomUUID());
        lesson.setTitle("Curriculum lesson");
        lesson.setType(type);
        lesson.setStatus(com.smartlearnly.backend.learning.lesson.entity.LessonStatus.PUBLISHED);
        lesson.setPreview(false);
        lesson.setSortOrder(0);
        section.addLesson(lesson);
        return lesson;
    }

    private Course course() {
        Course course = new Course();
        course.setId(UUID.randomUUID());
        course.setTitle("Course");
        course.setSlug("course");
        return course;
    }

    private Question question(UUID courseId, QuestionStatus status) {
        return question(courseId, status, QuestionType.MULTIPLE_CHOICE, UUID.randomUUID(), "Question?");
    }

    private Question question(
            UUID courseId,
            QuestionStatus status,
            QuestionType type,
            UUID moduleId,
            String questionText) {
        Question question = new Question();
        question.setId(UUID.randomUUID());
        question.setCourseId(courseId);
        question.setModuleId(moduleId);
        question.setQuestionText(questionText);
        question.setQuestionType(type);
        question.setStatus(status);
        question.setDifficulty((short) 2);
        return question;
    }

    private QuestionAnswer answer(UUID questionId, String text, boolean correct, int orderIndex) {
        QuestionAnswer answer = new QuestionAnswer();
        answer.setId(UUID.randomUUID());
        answer.setQuestionId(questionId);
        answer.setAnswerText(text);
        answer.setIsCorrect(correct);
        answer.setOrderIndex(orderIndex);
        return answer;
    }

    private FlashcardCard flashcardCard(FlashcardSet set, String frontText, String backText) {
        FlashcardCard card = new FlashcardCard();
        card.setId(UUID.randomUUID());
        card.setFlashcardSet(set);
        card.setFrontText(frontText);
        card.setBackText(backText);
        card.setOrderIndex(0);
        return card;
    }

    private UserAccount actor() {
        UserAccount actor = new UserAccount();
        actor.setId(UUID.randomUUID());
        actor.setEmail("trainer@smartlearnly.dev");
        actor.setFullName("Trainer");
        actor.setRole("TRAINER");
        return actor;
    }
}
