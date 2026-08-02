package com.smartlearnly.backend.test.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.notification.dto.NotificationCreateCommand;
import com.smartlearnly.backend.notification.entity.NotificationType;
import com.smartlearnly.backend.notification.service.NotificationService;
import com.smartlearnly.backend.question.repository.QuestionAnswerRepository;
import com.smartlearnly.backend.test.dto.TestAttemptModel;
import com.smartlearnly.backend.test.entity.AttemptStatus;
import com.smartlearnly.backend.test.entity.TestAttempt;
import com.smartlearnly.backend.test.repository.StudentTestAnswerRepository;
import com.smartlearnly.backend.test.repository.TestAttemptRepository;
import com.smartlearnly.backend.test.repository.TestQuestionRepository;
import com.smartlearnly.backend.test.repository.TestRepository;
import com.smartlearnly.backend.user.repository.UserRepository;
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
import org.springframework.messaging.simp.SimpMessagingTemplate;

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
    private SimpMessagingTemplate messagingTemplate;
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
                messagingTemplate,
                testService,
                userRepository);
        service.setNotificationService(notificationService);
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
                    assertThat(command.title()).isEqualTo("Test attempt submitted");
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
}
