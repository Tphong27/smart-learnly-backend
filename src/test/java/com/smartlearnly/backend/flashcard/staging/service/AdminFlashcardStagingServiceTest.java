package com.smartlearnly.backend.flashcard.staging.service;

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
import com.smartlearnly.backend.flashcard.entity.FlashcardCard;
import com.smartlearnly.backend.flashcard.entity.FlashcardSet;
import com.smartlearnly.backend.flashcard.repository.FlashcardCardRepository;
import com.smartlearnly.backend.flashcard.repository.FlashcardSetRepository;
import com.smartlearnly.backend.flashcard.staging.dto.AdminFlashcardStagingDtos.ApproveStagingCardsRequest;
import com.smartlearnly.backend.flashcard.staging.dto.AdminFlashcardStagingDtos.ApproveStagingCardsResponse;
import com.smartlearnly.backend.flashcard.staging.dto.AdminFlashcardStagingDtos.ImportQuestionBankRequest;
import com.smartlearnly.backend.flashcard.staging.dto.AdminFlashcardStagingDtos.SourceQuestionResponse;
import com.smartlearnly.backend.flashcard.staging.dto.AdminFlashcardStagingDtos.StagingBatchResponse;
import com.smartlearnly.backend.flashcard.staging.dto.AdminFlashcardStagingDtos.UpdateStagingCardRequest;
import com.smartlearnly.backend.flashcard.staging.entity.FlashcardStagingBatch;
import com.smartlearnly.backend.flashcard.staging.entity.FlashcardStagingCard;
import com.smartlearnly.backend.flashcard.staging.repository.FlashcardStagingBatchRepository;
import com.smartlearnly.backend.flashcard.staging.repository.FlashcardStagingCardRepository;
import com.smartlearnly.backend.learning.lesson.entity.Lesson;
import com.smartlearnly.backend.learning.lesson.entity.LessonStatus;
import com.smartlearnly.backend.learning.lesson.entity.LessonType;
import com.smartlearnly.backend.learning.module.entity.CourseSection;
import com.smartlearnly.backend.question.entity.Question;
import com.smartlearnly.backend.question.entity.QuestionAnswer;
import com.smartlearnly.backend.question.entity.QuestionBank;
import com.smartlearnly.backend.question.entity.QuestionStatus;
import com.smartlearnly.backend.question.entity.QuestionType;
import com.smartlearnly.backend.question.repository.QuestionAnswerRepository;
import com.smartlearnly.backend.question.repository.QuestionBankRepository;
import com.smartlearnly.backend.question.repository.QuestionRepository;
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
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class AdminFlashcardStagingServiceTest {
    @Mock
    private FlashcardSetRepository flashcardSetRepository;
    @Mock
    private FlashcardCardRepository flashcardCardRepository;
    @Mock
    private FlashcardStagingBatchRepository stagingBatchRepository;
    @Mock
    private FlashcardStagingCardRepository stagingCardRepository;
    @Mock
    private QuestionRepository questionRepository;
    @Mock
    private QuestionAnswerRepository questionAnswerRepository;
    @Mock
    private QuestionBankRepository questionBankRepository;
    @Mock
    private CurrentUserService currentUserService;

    private AdminFlashcardStagingService service;

    @BeforeEach
    void setUp() {
        service = new AdminFlashcardStagingService(
                flashcardSetRepository,
                flashcardCardRepository,
                stagingBatchRepository,
                stagingCardRepository,
                questionRepository,
                questionAnswerRepository,
                questionBankRepository,
                currentUserService
        );
    }

    @Test
    void importSelectedQuestionBankQuestionsCreatesOneBatchAndStagingCards() {
        FlashcardSet flashcardSet = flashcardSet();
        UserAccount actor = actor();
        QuestionBank bank = bank(flashcardSet.getLesson().getCourse().getId(), "Bank A");
        Question firstQuestion = question(flashcardSet.getLesson().getCourse().getId(), bank.getId(), "Question 1");
        Question secondQuestion = question(flashcardSet.getLesson().getCourse().getId(), bank.getId(), "Question 2");
        QuestionAnswer firstAnswer = answer(firstQuestion.getId(), "Correct 1", true, 0);
        QuestionAnswer secondAnswer = answer(secondQuestion.getId(), "Correct 2", true, 0);
        when(flashcardSetRepository.findByIdAndDeletedAtIsNull(flashcardSet.getId())).thenReturn(Optional.of(flashcardSet));
        when(currentUserService.requireAuthenticatedUser()).thenReturn(actor);
        when(questionRepository.findAllById(List.of(firstQuestion.getId(), secondQuestion.getId())))
                .thenReturn(List.of(firstQuestion, secondQuestion));
        when(questionAnswerRepository.findByQuestionIdInOrderByQuestionIdAscOrderIndexAsc(List.of(firstQuestion.getId(), secondQuestion.getId())))
                .thenReturn(List.of(firstAnswer, secondAnswer));
        when(questionBankRepository.findAllById(any())).thenReturn(List.of(bank));
        when(stagingBatchRepository.save(any(FlashcardStagingBatch.class))).thenAnswer(invocation -> {
            FlashcardStagingBatch batch = invocation.getArgument(0);
            batch.setId(UUID.randomUUID());
            return batch;
        });
        when(stagingCardRepository.saveAll(anyList())).thenAnswer(invocation -> {
            List<FlashcardStagingCard> cards = invocation.getArgument(0);
            cards.forEach(card -> card.setId(UUID.randomUUID()));
            return cards;
        });

        StagingBatchResponse response = service.importQuestionBank(
                flashcardSet.getId(),
                new ImportQuestionBankRequest(List.of(firstQuestion.getId(), secondQuestion.getId()))
        );

        assertThat(response.sourceType()).isEqualTo("QUESTION_BANK");
        assertThat(response.status()).isEqualTo("draft");
        assertThat(response.sourceName()).isEqualTo("Bank A");
        assertThat(response.cards()).hasSize(2);
        assertThat(response.cards()).extracting(card -> card.frontText()).containsExactly("Question 1", "Question 2");
        assertThat(response.cards()).extracting(card -> card.backText())
                .containsExactly("Correct answer(s):\nCorrect 1", "Correct answer(s):\nCorrect 2");
        ArgumentCaptor<FlashcardStagingBatch> batchCaptor = ArgumentCaptor.forClass(FlashcardStagingBatch.class);
        verify(stagingBatchRepository).save(batchCaptor.capture());
        assertThat(batchCaptor.getValue().getCreatedBy()).isSameAs(actor);
        assertThat(batchCaptor.getValue().getFlashcardSet()).isSameAs(flashcardSet);
    }

    @Test
    void importRejectsQuestionFromAnotherCourse() {
        FlashcardSet flashcardSet = flashcardSet();
        Question question = question(UUID.randomUUID(), UUID.randomUUID(), "Wrong course");
        when(flashcardSetRepository.findByIdAndDeletedAtIsNull(flashcardSet.getId())).thenReturn(Optional.of(flashcardSet));
        when(currentUserService.requireAuthenticatedUser()).thenReturn(actor());
        when(questionRepository.findAllById(List.of(question.getId()))).thenReturn(List.of(question));

        assertThatThrownBy(() -> service.importQuestionBank(
                flashcardSet.getId(),
                new ImportQuestionBankRequest(List.of(question.getId()))
        ))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));

        verify(stagingBatchRepository, never()).save(any());
        verify(stagingCardRepository, never()).saveAll(anyList());
    }

    @Test
    void updateStagingCardValidatesFrontAndBack() {
        FlashcardStagingCard card = stagingCard(stagingBatch(flashcardSet()), "draft", 0);
        when(stagingCardRepository.findById(card.getId())).thenReturn(Optional.of(card));

        assertThatThrownBy(() -> service.updateCard(
                card.getId(),
                new UpdateStagingCardRequest(" ", "Back", null, null, null, null, null)
        ))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));

        verify(stagingCardRepository, never()).save(any());
    }

    @Test
    void rejectStagingCardChangesStatusAndDoesNotCreateRealFlashcard() {
        FlashcardStagingCard card = stagingCard(stagingBatch(flashcardSet()), "draft", 0);
        when(stagingCardRepository.findById(card.getId())).thenReturn(Optional.of(card));

        service.rejectCard(card.getId());

        assertThat(card.getStatus()).isEqualTo("rejected");
        verify(stagingCardRepository).save(card);
        verify(flashcardCardRepository, never()).saveAll(anyList());
    }

    @Test
    void approveSelectedStagingCardsCreatesRealFlashcardsAppendedAfterExistingMaxOrderIndex() {
        FlashcardSet flashcardSet = flashcardSet();
        UserAccount actor = actor();
        FlashcardStagingBatch batch = stagingBatch(flashcardSet);
        FlashcardStagingCard first = stagingCard(batch, "draft", 0);
        FlashcardStagingCard second = stagingCard(batch, "draft", 1);
        when(flashcardSetRepository.findByIdAndDeletedAtIsNull(flashcardSet.getId())).thenReturn(Optional.of(flashcardSet));
        when(currentUserService.requireAuthenticatedUser()).thenReturn(actor);
        when(stagingCardRepository.findByIdIn(List.of(first.getId(), second.getId()))).thenReturn(List.of(second, first));
        when(flashcardCardRepository.findMaxOrderIndexBySetId(flashcardSet.getId())).thenReturn(7);
        when(flashcardCardRepository.saveAll(anyList())).thenAnswer(invocation -> {
            List<FlashcardCard> cards = invocation.getArgument(0);
            cards.forEach(card -> card.setId(UUID.randomUUID()));
            return cards;
        });
        when(stagingCardRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(stagingCardRepository.countByBatchIdAndStatus(batch.getId(), "draft")).thenReturn(0L);

        ApproveStagingCardsResponse response = service.approve(
                flashcardSet.getId(),
                new ApproveStagingCardsRequest(List.of(first.getId(), second.getId()))
        );

        assertThat(response.approvedCount()).isEqualTo(2);
        ArgumentCaptor<List<FlashcardCard>> flashcardsCaptor = ArgumentCaptor.forClass(List.class);
        verify(flashcardCardRepository).saveAll(flashcardsCaptor.capture());
        assertThat(flashcardsCaptor.getValue()).extracting(FlashcardCard::getOrderIndex).containsExactly(8, 9);
        assertThat(flashcardsCaptor.getValue()).extracting(FlashcardCard::getFrontText).containsExactly("Front 0", "Front 1");
        assertThat(first.getStatus()).isEqualTo("approved");
        assertThat(second.getStatus()).isEqualTo("approved");
        assertThat(batch.getStatus()).isEqualTo("approved");
        assertThat(batch.getApprovedBy()).isSameAs(actor);
    }

    @Test
    void approveCannotDuplicateAlreadyApprovedStagingCards() {
        FlashcardSet flashcardSet = flashcardSet();
        FlashcardStagingCard card = stagingCard(stagingBatch(flashcardSet), "approved", 0);
        when(flashcardSetRepository.findByIdAndDeletedAtIsNull(flashcardSet.getId())).thenReturn(Optional.of(flashcardSet));
        when(currentUserService.requireAuthenticatedUser()).thenReturn(actor());
        when(stagingCardRepository.findByIdIn(List.of(card.getId()))).thenReturn(List.of(card));

        assertThatThrownBy(() -> service.approve(
                flashcardSet.getId(),
                new ApproveStagingCardsRequest(List.of(card.getId()))
        ))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));

        verify(flashcardCardRepository, never()).saveAll(anyList());
    }

    @Test
    void approveRejectsNullOrEmptyStagingCardIds() {
        FlashcardSet flashcardSet = flashcardSet();
        when(flashcardSetRepository.findByIdAndDeletedAtIsNull(flashcardSet.getId())).thenReturn(Optional.of(flashcardSet));

        assertThatThrownBy(() -> service.approve(flashcardSet.getId(), null))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));

        assertThatThrownBy(() -> service.approve(
                flashcardSet.getId(),
                new ApproveStagingCardsRequest(List.of())
        ))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));

        verify(flashcardCardRepository, never()).saveAll(anyList());
    }

    @Test
    void sourceQuestionsReturnsOnlySameCourseQuestions() {
        FlashcardSet flashcardSet = flashcardSet();
        UUID bankId = UUID.randomUUID();
        Question sameCourseQuestion = question(flashcardSet.getLesson().getCourse().getId(), bankId, "Same course");
        Question otherCourseQuestion = question(UUID.randomUUID(), bankId, "Other course");
        QuestionAnswer answer = answer(sameCourseQuestion.getId(), "Correct", true, 0);
        QuestionBank bank = bank(flashcardSet.getLesson().getCourse().getId(), "Bank A");
        bank.setId(bankId);
        when(flashcardSetRepository.findByIdAndDeletedAtIsNull(flashcardSet.getId())).thenReturn(Optional.of(flashcardSet));
        when(questionRepository.findAll(any(Specification.class), any(Sort.class)))
                .thenReturn(List.of(sameCourseQuestion, otherCourseQuestion));
        when(questionAnswerRepository.findByQuestionIdInOrderByQuestionIdAscOrderIndexAsc(List.of(sameCourseQuestion.getId())))
                .thenReturn(List.of(answer));
        when(questionBankRepository.findAllById(any())).thenReturn(List.of(bank));

        List<SourceQuestionResponse> response = service.listSourceQuestions(
                flashcardSet.getId(),
                null,
                null,
                null,
                null
        );

        assertThat(response).hasSize(1);
        assertThat(response.get(0).questionId()).isEqualTo(sameCourseQuestion.getId());
        assertThat(response.get(0).questionBankName()).isEqualTo("Bank A");
        assertThat(response.get(0).correctAnswers()).containsExactly("Correct");
    }

    private FlashcardSet flashcardSet() {
        Course course = course();
        CourseSection section = section(course);
        Lesson lesson = lesson(course, section);
        FlashcardSet flashcardSet = new FlashcardSet();
        flashcardSet.setId(UUID.randomUUID());
        flashcardSet.setLesson(lesson);
        flashcardSet.setCourse(course);
        flashcardSet.setTitle("Flashcards");
        flashcardSet.setCreatedAt(Instant.now());
        flashcardSet.setUpdatedAt(Instant.now());
        return flashcardSet;
    }

    private FlashcardStagingBatch stagingBatch(FlashcardSet flashcardSet) {
        FlashcardStagingBatch batch = new FlashcardStagingBatch();
        batch.setId(UUID.randomUUID());
        batch.setFlashcardSet(flashcardSet);
        batch.setLesson(flashcardSet.getLesson());
        batch.setCourse(flashcardSet.getLesson().getCourse());
        batch.setCreatedBy(actor());
        batch.setSourceType("QUESTION_BANK");
        batch.setStatus("draft");
        batch.setSourceName("Bank A");
        batch.setCreatedAt(Instant.now());
        batch.setUpdatedAt(Instant.now());
        return batch;
    }

    private FlashcardStagingCard stagingCard(FlashcardStagingBatch batch, String status, int sortOrder) {
        FlashcardStagingCard card = new FlashcardStagingCard();
        card.setId(UUID.randomUUID());
        card.setBatch(batch);
        card.setSourceQuestionId(UUID.randomUUID());
        card.setFrontText("Front " + sortOrder);
        card.setBackText("Back " + sortOrder);
        card.setStatus(status);
        card.setSortOrder(sortOrder);
        card.setCreatedAt(Instant.now());
        card.setUpdatedAt(Instant.now());
        return card;
    }

    private Question question(UUID courseId, UUID bankId, String questionText) {
        Question question = new Question();
        question.setId(UUID.randomUUID());
        question.setQuestionBankId(bankId);
        question.setCourseId(courseId);
        question.setQuestionText(questionText);
        question.setQuestionType(QuestionType.MULTIPLE_CHOICE);
        question.setDifficulty((short) 2);
        question.setExplanation(null);
        question.setIsAiGenerated(false);
        question.setStatus(QuestionStatus.APPROVED);
        question.setCreatedAt(Instant.now());
        question.setUpdatedAt(Instant.now());
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

    private QuestionBank bank(UUID courseId, String name) {
        QuestionBank bank = new QuestionBank();
        bank.setId(UUID.randomUUID());
        bank.setCourseId(courseId);
        bank.setName(name);
        bank.setStatus("approved");
        bank.setCreatedAt(Instant.now());
        bank.setUpdatedAt(Instant.now());
        return bank;
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

    private UserAccount actor() {
        UserAccount actor = new UserAccount();
        actor.setId(UUID.randomUUID());
        actor.setEmail("trainer@smartlearnly.dev");
        actor.setFullName("Trainer");
        actor.setRole("TRAINER");
        return actor;
    }
}
