package com.smartlearnly.backend.assignment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.assignment.dto.AssignmentSubmissionModel;
import com.smartlearnly.backend.assignment.entity.Assignment;
import com.smartlearnly.backend.assignment.entity.AssignmentSubmission;
import com.smartlearnly.backend.assignment.entity.SubmissionStatus;
import com.smartlearnly.backend.assignment.repository.AssignmentRepository;
import com.smartlearnly.backend.assignment.repository.AssignmentSubmissionRepository;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.user.entity.UserAccount;
import com.smartlearnly.backend.user.repository.UserRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

    private AssignmentSubmissionService service;

    @BeforeEach
    void setUp() {
        service = new AssignmentSubmissionService(
                submissionRepository,
                assignmentRepository,
                userRepository,
                messagingTemplate,
                currentUserService);
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
    }

    @Test
    void startAssignmentShouldRejectTraineeImpersonation() {
        UserAccount trainee = trainee();
        AssignmentSubmissionModel.StartRequest request = new AssignmentSubmissionModel.StartRequest();
        request.setAssignmentId(UUID.randomUUID());
        request.setStudentId(UUID.randomUUID());

        when(currentUserService.requireAuthenticatedUser()).thenReturn(trainee);

        assertThatThrownBy(() -> service.startAssignment(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
        verify(assignmentRepository, never()).findById(any());
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
        assignment.setIsFlashtest(false);
        return assignment;
    }
}
