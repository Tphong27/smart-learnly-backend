package com.smartlearnly.backend.test.attempt.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.curriculum.repository.CurriculumLessonRepository;
import com.smartlearnly.backend.notification.dto.NotificationCreateCommand;
import com.smartlearnly.backend.notification.entity.NotificationType;
import com.smartlearnly.backend.notification.service.NotificationService;
import com.smartlearnly.backend.question.entity.QuestionAnswer;
import com.smartlearnly.backend.question.repository.QuestionAnswerRepository;
import com.smartlearnly.backend.test.attempt.dto.TestAttemptModel;
import com.smartlearnly.backend.test.entity.AttemptStatus;
import com.smartlearnly.backend.test.entity.StudentTestAnswer;
import com.smartlearnly.backend.test.entity.TestAttempt;
import com.smartlearnly.backend.test.entity.TestQuestion;
import com.smartlearnly.backend.test.repository.StudentTestAnswerRepository;
import com.smartlearnly.backend.test.repository.TestAttemptRepository;
import com.smartlearnly.backend.test.repository.TestQuestionRepository;
import com.smartlearnly.backend.test.repository.TestRepository;
import com.smartlearnly.backend.test.definition.service.TestService;
import com.smartlearnly.backend.user.entity.UserAccount;
import com.smartlearnly.backend.user.repository.UserRepository;
import java.math.BigDecimal;
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
class TestAttemptServiceTest {
    @Mock
    private TestAttemptRepository testAttemptRepository;
    @Mock
    private TestRepository testRepository;
    @Mock
    private TestQuestionRepository testQuestionRepository;
    @Mock
    private QuestionAnswerRepository questionAnswerRepository;
    @Mock
    private StudentTestAnswerRepository studentTestAnswerRepository;
    @Mock
    private CurriculumLessonRepository curriculumLessonRepository;
    @Mock
    private TestService testService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private NotificationService notificationService;

    private TestAttemptService service;

    @BeforeEach
    void setUp() {
        service = new TestAttemptService(
                testAttemptRepository,
                testRepository,
                testQuestionRepository,
                questionAnswerRepository,
                studentTestAnswerRepository,
                curriculumLessonRepository,
                testService,
                userRepository);
        service.setNotificationService(notificationService);
    }

    @Test
    void startAttemptShouldCreateCourseQuizAttempt() {
        UUID testId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        com.smartlearnly.backend.test.entity.Test test = test(testId, UUID.randomUUID());
        test.setDurationMinutes(20);
        test.setOpensAt(Instant.now().minusSeconds(60));
        test.setClosesAt(Instant.now().plusSeconds(3600));
        TestAttemptModel.StartRequest request = new TestAttemptModel.StartRequest();
        request.setTestId(testId);
        request.setStudentId(UUID.randomUUID());
        request.setAssignmentId(assignmentId);
        request.setStudentName("Linh Nguyen");
        request.setAccessCode("123456");

        when(testRepository.findById(testId)).thenReturn(Optional.of(test));
        when(testService.requireCurrentTraineeAccess(testId, null)).thenReturn(studentId);
        when(testService.isWithinSchedule(eq(test), any(Instant.class))).thenReturn(true);
        when(testService.accessCodeMatches(test, "123456")).thenReturn(true);
        when(testAttemptRepository.findByTestIdAndStudentIdAndClassIdIsNullOrderByStartTimeDesc(
                testId, studentId))
                .thenReturn(List.of());
        when(testAttemptRepository.save(any(TestAttempt.class))).thenAnswer(invocation -> {
            TestAttempt saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            saved.setCreatedAt(Instant.now());
            return saved;
        });
        when(testQuestionRepository.findByIdTestId(testId)).thenReturn(List.of());

        TestAttemptModel.Response response = service.startAttempt(request);

        assertThat(response.getTestId()).isEqualTo(testId);
        assertThat(response.getStudentId()).isEqualTo(studentId);
        assertThat(response.getAssignmentId()).isEqualTo(assignmentId);
        assertThat(response.getStatus()).isEqualTo(AttemptStatus.DOING);
        assertThat(response.getEndTime()).isAfter(response.getStartTime());
    }

    @Test
    void startAttemptShouldReturnExistingActiveAttemptWithoutCreatingAnother() {
        UUID testId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        TestAttempt existing = attempt(UUID.randomUUID(), testId, studentId, AttemptStatus.DOING);
        com.smartlearnly.backend.test.entity.Test test = test(testId, UUID.randomUUID());
        TestAttemptModel.StartRequest request = new TestAttemptModel.StartRequest();
        request.setTestId(testId);
        request.setAccessCode("654321");

        when(testRepository.findById(testId)).thenReturn(Optional.of(test));
        when(testService.requireCurrentTraineeAccess(testId, null)).thenReturn(studentId);
        when(testService.isWithinSchedule(eq(test), any(Instant.class))).thenReturn(true);
        when(testService.accessCodeMatches(test, "654321")).thenReturn(true);
        when(testAttemptRepository.findByTestIdAndStudentIdAndClassIdIsNullOrderByStartTimeDesc(
                testId, studentId))
                .thenReturn(List.of(existing));
        when(testQuestionRepository.findByIdTestId(testId)).thenReturn(List.of());

        TestAttemptModel.Response response = service.startAttempt(request);

        assertThat(response.getId()).isEqualTo(existing.getId());
        assertThat(response.getStatus()).isEqualTo(AttemptStatus.DOING);
        verify(testAttemptRepository, times(0)).save(any(TestAttempt.class));
    }

    @Test
    void startAttemptShouldRejectInvalidAccessCode() {
        UUID testId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        com.smartlearnly.backend.test.entity.Test test = test(testId, UUID.randomUUID());
        TestAttemptModel.StartRequest request = new TestAttemptModel.StartRequest();
        request.setTestId(testId);
        request.setAccessCode("000000");

        when(testRepository.findById(testId)).thenReturn(Optional.of(test));
        when(testService.requireCurrentTraineeAccess(testId, null)).thenReturn(studentId);
        when(testService.isWithinSchedule(eq(test), any(Instant.class))).thenReturn(true);
        when(testService.accessCodeMatches(test, "000000")).thenReturn(false);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.startAttempt(request))
                .hasMessage("Invalid or expired test access code");

        verify(testAttemptRepository, times(0)).save(any(TestAttempt.class));
    }

    @Test
    void startAttemptShouldRejectWhenTestIsOutsideSchedule() {
        UUID testId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        com.smartlearnly.backend.test.entity.Test test = test(testId, UUID.randomUUID());
        TestAttemptModel.StartRequest request = new TestAttemptModel.StartRequest();
        request.setTestId(testId);
        request.setAccessCode("123456");

        when(testRepository.findById(testId)).thenReturn(Optional.of(test));
        when(testService.requireCurrentTraineeAccess(testId, null)).thenReturn(studentId);
        when(testService.isWithinSchedule(eq(test), any(Instant.class))).thenReturn(false);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.startAttempt(request))
                .hasMessage("This test is not open for attempts right now");

        verify(testAttemptRepository, never())
                .findByTestIdAndStudentIdAndClassIdIsNullOrderByStartTimeDesc(any(), any());
        verify(testAttemptRepository, never()).save(any(TestAttempt.class));
    }

    @Test
    void startAttemptShouldRejectMissingTestId() {
        TestAttemptModel.StartRequest request = new TestAttemptModel.StartRequest();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.startAttempt(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("testId is required");

        verify(testRepository, never()).findById(any());
    }

    @Test
    void startAttemptShouldCreateNewAttemptAfterRetakeWasAllowed() {
        UUID testId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        TestAttempt submitted = attempt(UUID.randomUUID(), testId, studentId, AttemptStatus.SUBMITTED);
        submitted.setRetakeAllowed(true);
        com.smartlearnly.backend.test.entity.Test test = test(testId, UUID.randomUUID());
        TestAttemptModel.StartRequest request = new TestAttemptModel.StartRequest();
        request.setTestId(testId);
        request.setAccessCode("222222");

        when(testRepository.findById(testId)).thenReturn(Optional.of(test));
        when(testService.requireCurrentTraineeAccess(testId, null)).thenReturn(studentId);
        when(testService.isWithinSchedule(eq(test), any(Instant.class))).thenReturn(true);
        when(testService.accessCodeMatches(test, "222222")).thenReturn(true);
        when(testAttemptRepository.findByTestIdAndStudentIdAndClassIdIsNullOrderByStartTimeDesc(
                testId, studentId))
                .thenReturn(List.of(submitted));
        when(testAttemptRepository.save(any(TestAttempt.class))).thenAnswer(invocation -> {
            TestAttempt saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(UUID.randomUUID());
                saved.setCreatedAt(Instant.now());
            }
            return saved;
        });
        when(testQuestionRepository.findByIdTestId(testId)).thenReturn(List.of());

        TestAttemptModel.Response response = service.startAttempt(request);

        assertThat(submitted.getRetakeAllowed()).isFalse();
        assertThat(response.getId()).isNotEqualTo(submitted.getId());
        assertThat(response.getStatus()).isEqualTo(AttemptStatus.DOING);
        verify(testAttemptRepository, times(2)).save(any(TestAttempt.class));
    }

    @Test
    void startAttemptShouldCreateIndependentAttemptForClassContext() {
        UUID testId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID classId = UUID.randomUUID();
        com.smartlearnly.backend.test.entity.Test test = test(testId, UUID.randomUUID());
        TestAttemptModel.StartRequest request = new TestAttemptModel.StartRequest();
        request.setTestId(testId);
        request.setClassId(classId);

        when(testRepository.findById(testId)).thenReturn(Optional.of(test));
        when(testService.requireCurrentTraineeAccess(testId, classId)).thenReturn(studentId);
        when(testService.isWithinSchedule(eq(test), any(Instant.class))).thenReturn(true);
        when(curriculumLessonRepository.existsByTestId(testId)).thenReturn(true);
        when(testAttemptRepository.findByTestIdAndStudentIdAndClassIdOrderByStartTimeDesc(
                testId, studentId, classId)).thenReturn(List.of());
        when(testAttemptRepository.save(any(TestAttempt.class))).thenAnswer(invocation -> {
            TestAttempt saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            saved.setCreatedAt(Instant.now());
            return saved;
        });
        when(testQuestionRepository.findByIdTestId(testId)).thenReturn(List.of());

        TestAttemptModel.Response response = service.startAttempt(request);

        assertThat(response.getClassId()).isEqualTo(classId);
        ArgumentCaptor<TestAttempt> attemptCaptor = ArgumentCaptor.forClass(TestAttempt.class);
        verify(testAttemptRepository).save(attemptCaptor.capture());
        assertThat(attemptCaptor.getValue().getClassId()).isEqualTo(classId);
        verify(testAttemptRepository, never())
                .findByTestIdAndStudentIdAndClassIdIsNullOrderByStartTimeDesc(any(), any());
    }

    @Test
    void getAttemptsShouldReturnOnlyRequestedClassContext() {
        UUID testId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID classId = UUID.randomUUID();
        TestAttempt classAttempt = attempt(
                UUID.randomUUID(), testId, studentId, AttemptStatus.SUBMITTED);
        classAttempt.setClassId(classId);
        classAttempt.setScore(BigDecimal.ZERO);

        when(testAttemptRepository.findByTestIdAndStudentIdAndClassIdOrderByStartTimeDesc(
                testId, studentId, classId)).thenReturn(List.of(classAttempt));
        when(testQuestionRepository.findByIdTestId(testId)).thenReturn(List.of());
        when(studentTestAnswerRepository.findByAttemptId(classAttempt.getId())).thenReturn(List.of());
        when(studentTestAnswerRepository.saveAll(List.of())).thenReturn(List.of());

        List<TestAttemptModel.Response> responses = service.getAttempts(testId, studentId, classId);

        assertThat(responses).singleElement()
                .extracting(TestAttemptModel.Response::getClassId)
                .isEqualTo(classId);
        verify(testService).requireAttemptAccess(testId, studentId, classId);
        verify(testAttemptRepository, never())
                .findByTestIdAndStudentIdAndClassIdIsNullOrderByStartTimeDesc(any(), any());
    }

    @Test
    void getAttemptByIdShouldAuthorizeUsingPersistedClassContext() {
        UUID attemptId = UUID.randomUUID();
        UUID classId = UUID.randomUUID();
        TestAttempt attempt = attempt(
                attemptId, UUID.randomUUID(), UUID.randomUUID(), AttemptStatus.DOING);
        attempt.setClassId(classId);
        when(testAttemptRepository.findById(attemptId)).thenReturn(Optional.of(attempt));

        TestAttemptModel.Response response = service.getAttemptById(
                attemptId, UUID.randomUUID());

        assertThat(response.getClassId()).isEqualTo(classId);
        verify(testService).requireAttemptAccess(
                attempt.getTestId(), attempt.getStudentId(), classId);
    }

    @Test
    void submitAttemptShouldNotifyStudentAndTestCreator() {
        UUID attemptId = UUID.randomUUID();
        UUID testId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        TestAttempt attempt = attempt(attemptId, testId, studentId, AttemptStatus.DOING);
        com.smartlearnly.backend.test.entity.Test test = test(testId, creatorId);

        when(testAttemptRepository.findById(attemptId)).thenReturn(Optional.of(attempt));
        when(testQuestionRepository.findByIdTestId(testId)).thenReturn(List.of());
        when(studentTestAnswerRepository.findByAttemptId(attemptId)).thenReturn(List.of());
        when(studentTestAnswerRepository.saveAll(List.of())).thenReturn(List.of());
        when(testAttemptRepository.save(attempt)).thenReturn(attempt);
        when(testRepository.findById(testId)).thenReturn(Optional.of(test));

        TestAttemptModel.Response response =
                service.submitAttempt(attemptId, new TestAttemptModel.SubmitRequest());

        assertThat(response.getStatus()).isEqualTo(AttemptStatus.SUBMITTED);
        ArgumentCaptor<NotificationCreateCommand> notificationCaptor =
                ArgumentCaptor.forClass(NotificationCreateCommand.class);
        verify(notificationService, times(2)).emit(notificationCaptor.capture());
        assertThat(notificationCaptor.getAllValues())
                .extracting(NotificationCreateCommand::userId)
                .containsExactly(studentId, creatorId);
        assertThat(notificationCaptor.getAllValues())
                .allSatisfy(command -> {
                    assertThat(command.type()).isEqualTo(NotificationType.TEST);
                    assertThat(command.title()).isEqualTo("Course quiz attempt submitted");
                    assertThat(command.referenceType()).isEqualTo("TEST_ATTEMPT");
                    assertThat(command.referenceId()).isEqualTo(attemptId);
                });
        assertThat(notificationCaptor.getAllValues().get(0).eventKey())
                .isEqualTo("test-attempt:" + attemptId + ":SUBMITTED:student");
        assertThat(notificationCaptor.getAllValues().get(1).eventKey())
                .isEqualTo("test-attempt:" + attemptId + ":SUBMITTED:owner");
        assertThat(notificationCaptor.getAllValues().get(1).actorId()).isEqualTo(studentId);
    }

    @Test
    void submitAttemptShouldGradeAnswersAndBroadcastPercentage() {
        UUID attemptId = UUID.randomUUID();
        UUID testId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID questionId = UUID.randomUUID();
        UUID answerId = UUID.randomUUID();
        TestAttempt attempt = attempt(attemptId, testId, studentId, AttemptStatus.DOING);
        TestQuestion question = question(testId, questionId, "5.0");
        StudentTestAnswer answer = answer(attemptId, questionId, answerId);
        QuestionAnswer selectedAnswer = selectedAnswer(answerId, questionId, true);

        when(testAttemptRepository.findById(attemptId)).thenReturn(Optional.of(attempt));
        when(testQuestionRepository.findByIdTestId(testId)).thenReturn(List.of(question));
        when(studentTestAnswerRepository.findByAttemptId(attemptId)).thenReturn(List.of(answer));
        when(questionAnswerRepository.findById(answerId)).thenReturn(Optional.of(selectedAnswer));
        when(studentTestAnswerRepository.saveAll(List.of(answer))).thenReturn(List.of(answer));
        when(testAttemptRepository.save(attempt)).thenReturn(attempt);
        when(testRepository.findById(testId)).thenReturn(Optional.of(test(testId, studentId)));
        when(userRepository.findByIdAndDeletedAtIsNull(studentId)).thenReturn(Optional.of(user(studentId)));

        TestAttemptModel.Response response =
                service.submitAttempt(attemptId, new TestAttemptModel.SubmitRequest());

        assertThat(response.getStatus()).isEqualTo(AttemptStatus.SUBMITTED);
        assertThat(response.getScore()).isEqualByComparingTo("5.0");
        assertThat(response.getPercentage()).isEqualByComparingTo("100.00");
        assertThat(answer.getIsCorrect()).isTrue();
        assertThat(answer.getScoreAwarded()).isEqualByComparingTo("5.0");
    }

    @Test
    void submitAttemptShouldScoreZeroWhenSelectedAnswerIsWrong() {
        UUID attemptId = UUID.randomUUID();
        UUID testId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID questionId = UUID.randomUUID();
        UUID answerId = UUID.randomUUID();
        TestAttempt attempt = attempt(attemptId, testId, studentId, AttemptStatus.DOING);
        TestQuestion question = question(testId, questionId, "5.0");
        StudentTestAnswer answer = answer(attemptId, questionId, answerId);

        when(testAttemptRepository.findById(attemptId)).thenReturn(Optional.of(attempt));
        when(testQuestionRepository.findByIdTestId(testId)).thenReturn(List.of(question));
        when(studentTestAnswerRepository.findByAttemptId(attemptId)).thenReturn(List.of(answer));
        when(questionAnswerRepository.findById(answerId))
                .thenReturn(Optional.of(selectedAnswer(answerId, questionId, false)));
        when(studentTestAnswerRepository.saveAll(List.of(answer))).thenReturn(List.of(answer));
        when(testAttemptRepository.save(attempt)).thenReturn(attempt);
        when(testRepository.findById(testId)).thenReturn(Optional.of(test(testId, studentId)));

        TestAttemptModel.Response response =
                service.submitAttempt(attemptId, new TestAttemptModel.SubmitRequest());

        assertThat(response.getScore()).isEqualByComparingTo("0");
        assertThat(response.getPercentage()).isEqualByComparingTo("0.00");
        assertThat(answer.getIsCorrect()).isFalse();
        assertThat(answer.getScoreAwarded()).isEqualByComparingTo("0");
    }

    @Test
    void submitAttemptShouldMarkExpiredWhenEndTimeAlreadyPassed() {
        UUID attemptId = UUID.randomUUID();
        UUID testId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        TestAttempt attempt = attempt(attemptId, testId, studentId, AttemptStatus.DOING);
        attempt.setEndTime(Instant.now().minusSeconds(1));

        when(testAttemptRepository.findById(attemptId)).thenReturn(Optional.of(attempt));
        when(testQuestionRepository.findByIdTestId(testId)).thenReturn(List.of());
        when(studentTestAnswerRepository.findByAttemptId(attemptId)).thenReturn(List.of());
        when(studentTestAnswerRepository.saveAll(List.of())).thenReturn(List.of());
        when(testAttemptRepository.save(attempt)).thenReturn(attempt);
        when(testRepository.findById(testId)).thenReturn(Optional.of(test(testId, studentId)));

        TestAttemptModel.Response response =
                service.submitAttempt(attemptId, new TestAttemptModel.SubmitRequest());

        assertThat(response.getStatus()).isEqualTo(AttemptStatus.EXPIRED);
        verify(notificationService).emit(any(NotificationCreateCommand.class));
    }

    @Test
    void submitAttemptShouldReturnSubmittedAttemptWithoutRegrading() {
        UUID attemptId = UUID.randomUUID();
        UUID testId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        TestAttempt attempt = attempt(attemptId, testId, studentId, AttemptStatus.SUBMITTED);
        attempt.setScore(BigDecimal.TEN);

        when(testAttemptRepository.findById(attemptId)).thenReturn(Optional.of(attempt));
        when(testQuestionRepository.findByIdTestId(testId)).thenReturn(List.of());

        TestAttemptModel.Response response =
                service.submitAttempt(attemptId, new TestAttemptModel.SubmitRequest());

        assertThat(response.getStatus()).isEqualTo(AttemptStatus.SUBMITTED);
        assertThat(response.getScore()).isEqualByComparingTo("10");
        verify(studentTestAnswerRepository, times(0)).findByAttemptId(any());
        verify(testAttemptRepository, times(0)).save(any(TestAttempt.class));
    }

    @Test
    void submitAttemptShouldNotNotifyOwnerWhenCreatorIsTheStudent() {
        UUID attemptId = UUID.randomUUID();
        UUID testId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        TestAttempt attempt = attempt(attemptId, testId, studentId, AttemptStatus.DOING);
        com.smartlearnly.backend.test.entity.Test test = test(testId, studentId);

        when(testAttemptRepository.findById(attemptId)).thenReturn(Optional.of(attempt));
        when(testQuestionRepository.findByIdTestId(testId)).thenReturn(List.of());
        when(studentTestAnswerRepository.findByAttemptId(attemptId)).thenReturn(List.of());
        when(studentTestAnswerRepository.saveAll(List.of())).thenReturn(List.of());
        when(testAttemptRepository.save(attempt)).thenReturn(attempt);
        when(testRepository.findById(testId)).thenReturn(Optional.of(test));

        service.submitAttempt(attemptId, new TestAttemptModel.SubmitRequest());

        verify(notificationService).emit(any(NotificationCreateCommand.class));
    }

    private TestAttempt attempt(UUID attemptId, UUID testId, UUID studentId, AttemptStatus status) {
        TestAttempt attempt = new TestAttempt();
        attempt.setId(attemptId);
        attempt.setTestId(testId);
        attempt.setStudentId(studentId);
        attempt.setStartTime(Instant.now().minusSeconds(60));
        attempt.setEndTime(Instant.now().plusSeconds(60));
        attempt.setStatus(status);
        attempt.setCreatedAt(Instant.now().minusSeconds(60));
        return attempt;
    }

    private com.smartlearnly.backend.test.entity.Test test(UUID testId, UUID creatorId) {
        com.smartlearnly.backend.test.entity.Test test = new com.smartlearnly.backend.test.entity.Test();
        test.setId(testId);
        test.setTitle("Java foundations test");
        test.setCreatedBy(creatorId);
        return test;
    }

    private TestQuestion question(UUID testId, UUID questionId, String marks) {
        TestQuestion question = new TestQuestion();
        question.setId(new TestQuestion.TestQuestionId(testId, questionId));
        question.setMarks(new BigDecimal(marks));
        return question;
    }

    private StudentTestAnswer answer(UUID attemptId, UUID questionId, UUID selectedAnswerId) {
        StudentTestAnswer answer = new StudentTestAnswer();
        answer.setId(UUID.randomUUID());
        answer.setAttemptId(attemptId);
        answer.setQuestionId(questionId);
        answer.setSelectedAnswerId(selectedAnswerId);
        return answer;
    }

    private QuestionAnswer selectedAnswer(UUID answerId, UUID questionId, boolean correct) {
        QuestionAnswer answer = new QuestionAnswer();
        answer.setId(answerId);
        answer.setQuestionId(questionId);
        answer.setIsCorrect(correct);
        return answer;
    }

    private UserAccount user(UUID studentId) {
        UserAccount user = new UserAccount();
        user.setId(studentId);
        user.setFullName("Linh Nguyen");
        user.setEmail("linh@example.test");
        return user;
    }
}
