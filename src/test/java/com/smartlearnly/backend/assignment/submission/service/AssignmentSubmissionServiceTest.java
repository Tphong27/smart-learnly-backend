package com.smartlearnly.backend.assignment.submission.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.assignment.ai.service.AssignmentAiDraftService;
import com.smartlearnly.backend.assignment.submission.dto.AssignmentSubmissionModel;
import com.smartlearnly.backend.assignment.entity.Assignment;
import com.smartlearnly.backend.assignment.entity.AssignmentSubmission;
import com.smartlearnly.backend.assignment.entity.SubmissionStatus;
import com.smartlearnly.backend.assignment.repository.AssignmentRepository;
import com.smartlearnly.backend.assignment.repository.AssignmentSubmissionRepository;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.notification.dto.NotificationCreateCommand;
import com.smartlearnly.backend.notification.entity.NotificationType;
import com.smartlearnly.backend.notification.service.NotificationService;
import com.smartlearnly.backend.user.entity.UserAccount;
import com.smartlearnly.backend.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
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
class AssignmentSubmissionServiceTest {
    @Mock
    private AssignmentSubmissionRepository submissionRepository;
    @Mock
    private AssignmentRepository assignmentRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private AssignmentAiDraftService assignmentAiDraftService;
    @Mock
    private NotificationService notificationService;

    private AssignmentSubmissionService service;

    @BeforeEach
    void setUp() {
        service = new AssignmentSubmissionService(
                submissionRepository,
                assignmentRepository,
                userRepository,
                messagingTemplate,
                currentUserService,
                assignmentAiDraftService);
        service.setNotificationService(notificationService);
    }

    @Test
    void submitAssignmentShouldSaveWorkForAuthenticatedTrainee() {
        UUID assignmentId = UUID.randomUUID();
        UserAccount trainee = trainee();
        Assignment assignment = assignment(assignmentId);
        AssignmentSubmissionModel.CreateRequest request = new AssignmentSubmissionModel.CreateRequest();
        request.setAssignmentId(assignmentId);
        request.setStudentId(trainee.getId());
        request.setStudentName(trainee.getFullName());
        request.setFileUrl("/api/v1/submissions/files/work.pdf");
        request.setFileName("work.pdf");

        when(currentUserService.requireAuthenticatedUser()).thenReturn(trainee);
        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
        when(assignmentRepository.existsAvailableForStudent(assignmentId, trainee.getId()))
                .thenReturn(true);
        when(submissionRepository.findByAssignmentIdAndStudentId(assignmentId, trainee.getId()))
                .thenReturn(Optional.empty());
        when(submissionRepository.save(any(AssignmentSubmission.class))).thenAnswer(invocation -> {
            AssignmentSubmission saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            saved.setCreatedAt(Instant.now());
            saved.setUpdatedAt(Instant.now());
            return saved;
        });
        when(userRepository.findByIdAndDeletedAtIsNull(trainee.getId())).thenReturn(Optional.of(trainee));

        AssignmentSubmissionModel.Response response = service.submitAssignment(request);

        assertThat(response.getStudentId()).isEqualTo(trainee.getId());
        assertThat(response.getStatus()).isEqualTo(SubmissionStatus.SUBMITTED);
        assertThat(response.getFileName()).isEqualTo("work.pdf");

        ArgumentCaptor<NotificationCreateCommand> notificationCaptor =
                ArgumentCaptor.forClass(NotificationCreateCommand.class);
        verify(notificationService).emit(notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().userId()).isEqualTo(assignment.getCreatedBy());
        assertThat(notificationCaptor.getValue().type()).isEqualTo(NotificationType.ASSIGNMENT);
        assertThat(notificationCaptor.getValue().referenceType()).isEqualTo("ASSIGNMENT_SUBMISSION");
    }

    @Test
    void gradeSubmissionShouldNotifyTrainee() {
        UserAccount trainer = new UserAccount();
        trainer.setId(UUID.randomUUID());
        trainer.setRole("TRAINER");
        Assignment assignment = assignment(UUID.randomUUID());
        AssignmentSubmission submission = new AssignmentSubmission();
        submission.setId(UUID.randomUUID());
        submission.setAssignmentId(assignment.getId());
        submission.setStudentId(UUID.randomUUID());
        submission.setStatus(SubmissionStatus.SUBMITTED);

        AssignmentSubmissionModel.GradeRequest request = new AssignmentSubmissionModel.GradeRequest();
        request.setScore(new BigDecimal("8.5"));
        request.setTrainerFeedback("Good work");
        request.setStatus(SubmissionStatus.GRADED);

        when(submissionRepository.findById(submission.getId())).thenReturn(Optional.of(submission));
        when(currentUserService.requireAuthenticatedUser()).thenReturn(trainer);
        when(assignmentRepository.findById(assignment.getId())).thenReturn(Optional.of(assignment));
        when(assignmentRepository.existsManagedByStaff(assignment.getId(), trainer.getId()))
                .thenReturn(true);
        when(submissionRepository.save(submission)).thenReturn(submission);

        service.gradeSubmission(submission.getId(), request);

        ArgumentCaptor<NotificationCreateCommand> notificationCaptor =
                ArgumentCaptor.forClass(NotificationCreateCommand.class);
        verify(notificationService).emit(notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().userId()).isEqualTo(submission.getStudentId());
        assertThat(notificationCaptor.getValue().type()).isEqualTo(NotificationType.FEEDBACK);
        assertThat(notificationCaptor.getValue().actorId()).isEqualTo(trainer.getId());
    }

    @Test
    void gradeSubmissionShouldPreserveExistingFeedbackWhenOnlyScoreChanges() {
        UserAccount trainer = new UserAccount();
        trainer.setId(UUID.randomUUID());
        trainer.setRole("TRAINER");
        Assignment assignment = assignment(UUID.randomUUID());
        AssignmentSubmission submission = new AssignmentSubmission();
        submission.setId(UUID.randomUUID());
        submission.setAssignmentId(assignment.getId());
        submission.setStudentId(UUID.randomUUID());
        submission.setStatus(SubmissionStatus.SUBMITTED);
        submission.setTrainerFeedback("Keep this feedback");

        AssignmentSubmissionModel.GradeRequest request = new AssignmentSubmissionModel.GradeRequest();
        request.setScore(new BigDecimal("9"));
        request.setStatus(SubmissionStatus.GRADED);

        when(submissionRepository.findById(submission.getId())).thenReturn(Optional.of(submission));
        when(currentUserService.requireAuthenticatedUser()).thenReturn(trainer);
        when(assignmentRepository.findById(assignment.getId())).thenReturn(Optional.of(assignment));
        when(assignmentRepository.existsManagedByStaff(assignment.getId(), trainer.getId()))
                .thenReturn(true);
        when(submissionRepository.save(submission)).thenReturn(submission);

        AssignmentSubmissionModel.Response response = service.gradeSubmission(submission.getId(), request);

        assertThat(response.getTrainerFeedback()).isEqualTo("Keep this feedback");
        assertThat(response.getScore()).isEqualByComparingTo("9");
    }

    @Test
    void startAssignmentShouldRejectTraineeImpersonation() {
        UserAccount trainee = trainee();
        Assignment assignment = assignment(UUID.randomUUID());
        AssignmentSubmissionModel.StartRequest request = new AssignmentSubmissionModel.StartRequest();
        request.setAssignmentId(assignment.getId());
        request.setStudentId(UUID.randomUUID());

        when(currentUserService.requireAuthenticatedUser()).thenReturn(trainee);
        when(assignmentRepository.findById(assignment.getId())).thenReturn(Optional.of(assignment));

        assertThatThrownBy(() -> service.startAssignment(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
        verify(submissionRepository, never()).save(any());
    }

    @Test
    void gradeSubmissionShouldRejectTrainerOutsideAssignedClass() {
        UserAccount trainer = new UserAccount();
        trainer.setId(UUID.randomUUID());
        trainer.setRole("TRAINER");
        Assignment assignment = assignment(UUID.randomUUID());
        AssignmentSubmission submission = new AssignmentSubmission();
        submission.setId(UUID.randomUUID());
        submission.setAssignmentId(assignment.getId());
        submission.setStudentId(UUID.randomUUID());

        when(submissionRepository.findById(submission.getId())).thenReturn(Optional.of(submission));
        when(assignmentRepository.findById(assignment.getId())).thenReturn(Optional.of(assignment));
        when(currentUserService.requireAuthenticatedUser()).thenReturn(trainer);

        assertThatThrownBy(() -> service.gradeSubmission(
                submission.getId(),
                new AssignmentSubmissionModel.GradeRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
        verify(submissionRepository, never()).save(any());
    }

    @Test
    void submitAssignmentShouldRejectTraineeOutsideEnrolledClass() {
        UserAccount trainee = trainee();
        Assignment assignment = assignment(UUID.randomUUID());
        AssignmentSubmissionModel.CreateRequest request = new AssignmentSubmissionModel.CreateRequest();
        request.setAssignmentId(assignment.getId());
        request.setStudentId(trainee.getId());

        when(currentUserService.requireAuthenticatedUser()).thenReturn(trainee);
        when(assignmentRepository.findById(assignment.getId())).thenReturn(Optional.of(assignment));

        assertThatThrownBy(() -> service.submitAssignment(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
        verify(submissionRepository, never()).save(any());
    }

    @Test
    void requireFileAccessShouldRejectAnotherTraineesSubmission() {
        UserAccount trainee = trainee();
        Assignment assignment = assignment(UUID.randomUUID());
        AssignmentSubmission submission = new AssignmentSubmission();
        submission.setAssignmentId(assignment.getId());
        submission.setStudentId(UUID.randomUUID());
        submission.setFileUrl("/api/v1/submissions/files/private.pdf");

        when(currentUserService.requireAuthenticatedUser()).thenReturn(trainee);
        when(submissionRepository.findByFileUrl(submission.getFileUrl()))
                .thenReturn(Optional.of(submission));
        when(assignmentRepository.findById(assignment.getId())).thenReturn(Optional.of(assignment));

        assertThatThrownBy(() -> service.requireFileAccess(submission.getFileUrl()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    private UserAccount trainee() {
        UserAccount trainee = new UserAccount();
        trainee.setId(UUID.randomUUID());
        trainee.setRole("TRAINEE");
        trainee.setEmail("trainee@smartlearnly.dev");
        trainee.setFullName("Trainee User");
        return trainee;
    }

    private Assignment assignment(UUID id) {
        Assignment assignment = new Assignment();
        assignment.setId(id);
        assignment.setTitle("Course assignment");
        assignment.setAllowLateSubmission(false);
        assignment.setIsArchived(false);
        assignment.setCreatedBy(UUID.randomUUID());
        return assignment;
    }
}
