package com.smartlearnly.backend.assignment.service;

import com.smartlearnly.backend.assignment.dto.AssignmentSubmissionModel;
import com.smartlearnly.backend.assignment.entity.Assignment;
import com.smartlearnly.backend.assignment.entity.AssignmentSubmission;
import com.smartlearnly.backend.assignment.entity.SubmissionStatus;
import com.smartlearnly.backend.assignment.repository.AssignmentRepository;
import com.smartlearnly.backend.assignment.repository.AssignmentSubmissionRepository;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.flashtest.dto.MonitorEvent;
import com.smartlearnly.backend.user.entity.UserAccount;
import com.smartlearnly.backend.user.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AssignmentSubmissionService {

    private final AssignmentSubmissionRepository repository;
    private final AssignmentRepository assignmentRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final CurrentUserService currentUserService;

    @Transactional
    public AssignmentSubmissionModel.Response startAssignment(
            AssignmentSubmissionModel.StartRequest request) {
        UUID studentId = required(request.getStudentId(), "studentId");
        requireSelfIfTrainee(studentId);
        Assignment assignment = loadAssignment(required(request.getAssignmentId(), "assignmentId"));

        AssignmentSubmission submission = repository
                .findByAssignmentIdAndStudentId(
                        request.getAssignmentId(),
                        studentId)
                .orElseGet(AssignmentSubmission::new);
        if (isFinalStatus(submission.getStatus())
                && !canReopenExpiredSubmission(submission, assignment)) {
            return mapToResponse(submission);
        }
        assertAssignmentOpen(assignment);
        submission.setAssignmentId(request.getAssignmentId());
        submission.setStudentId(request.getStudentId());
        if (submission.getStartTime() == null) {
            submission.setStartTime(Instant.now());
        }
        submission.setIsLate(false);
        submission.setStatus(SubmissionStatus.DOING);

        AssignmentSubmission saved = repository.save(submission);
        AssignmentSubmissionModel.Response response = mapToResponse(saved);
        broadcast(response, assignment, request.getStudentName());
        return response;
    }

    @Transactional
    public AssignmentSubmissionModel.Response submitAssignment(
            AssignmentSubmissionModel.CreateRequest request) {
        UUID studentId = required(request.getStudentId(), "studentId");
        requireSelfIfTrainee(studentId);
        Assignment assignment = loadAssignment(required(request.getAssignmentId(), "assignmentId"));
        AssignmentSubmission submission = repository
                .findByAssignmentIdAndStudentId(request.getAssignmentId(), request.getStudentId())
                .orElseGet(AssignmentSubmission::new);
        boolean expired = assignment.getDueDate() != null
                && Instant.now().isAfter(assignment.getDueDate());
        if (expired && !Boolean.TRUE.equals(assignment.getAllowLateSubmission())) {
            if (hasSubmittedWork(submission.getStatus())) {
                return mapToResponse(submission);
            }
            AssignmentSubmission expiredSubmission = repository
                    .findByAssignmentIdAndStudentId(
                            request.getAssignmentId(),
                            required(request.getStudentId(), "studentId"))
                    .orElseGet(AssignmentSubmission::new);
            expiredSubmission.setAssignmentId(request.getAssignmentId());
            expiredSubmission.setStudentId(request.getStudentId());
            expiredSubmission.setIsLate(true);
            expiredSubmission.setStatus(SubmissionStatus.EXPIRED);
            AssignmentSubmission saved = repository.save(expiredSubmission);
            broadcast(mapToResponse(saved), assignment, request.getStudentName());
            throw new BusinessException(
                    ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Assignment due date has passed");
        }

        if (isFinalStatus(submission.getStatus())
                && !canResubmitFinalSubmission(submission, assignment)) {
            return mapToResponse(submission);
        }
        submission.setAssignmentId(request.getAssignmentId());
        submission.setStudentId(request.getStudentId());
        submission.setSubmissionText(request.getSubmissionText());
        submission.setFileUrl(request.getFileUrl());
        submission.setFileName(request.getFileName());
        submission.setSubmittedAt(Instant.now());
        submission.setIsLate(expired);
        submission.setStatus(expired ? SubmissionStatus.EXPIRED : SubmissionStatus.SUBMITTED);
        submission.setScore(null);
        submission.setAiFeedback(null);
        submission.setTrainerFeedback(null);
        submission.setGradedBy(null);
        submission.setGradedAt(null);

        AssignmentSubmission saved = repository.save(submission);
        AssignmentSubmissionModel.Response response = mapToResponse(saved);
        broadcast(response, assignment, request.getStudentName());
        return response;
    }

    @Transactional
    public AssignmentSubmissionModel.Response updateSubmission(
            UUID id,
            AssignmentSubmissionModel.UpdateRequest request) {
        AssignmentSubmission submission = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Submission not found"));
        requireSelfIfTrainee(submission.getStudentId());
        Assignment assignment = loadAssignment(submission.getAssignmentId());
        assertAssignmentOpen(assignment);

        submission.setSubmissionText(request.getSubmissionText());
        submission.setFileUrl(request.getFileUrl());
        submission.setFileName(request.getFileName());

        AssignmentSubmission updated = repository.save(submission);
        AssignmentSubmissionModel.Response response = mapToResponse(updated);
        broadcast(response, assignment, null);
        return response;
    }

    public AssignmentSubmissionModel.Response gradeSubmission(
            UUID id,
            AssignmentSubmissionModel.GradeRequest request) {
        AssignmentSubmission submission = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Submission not found"));

        if (request.getScore() != null
                && (request.getScore().compareTo(BigDecimal.ZERO) < 0
                || request.getScore().compareTo(BigDecimal.TEN) > 0)) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_FAILED,
                    "Score must be between 0 and 10");
        }

        submission.setScore(request.getScore());
        submission.setAiFeedback(request.getAiFeedback());
        submission.setTrainerFeedback(request.getTrainerFeedback());
        submission.setStatus(request.getStatus());
        submission.setGradedBy(request.getGradedBy());
        submission.setGradedAt(Instant.now());

        AssignmentSubmission updated = repository.save(submission);
        return mapToResponse(updated);
    }

    public List<AssignmentSubmissionModel.Response> getSubmissionsByAssignment(UUID assignmentId) {
        return repository.findByAssignmentId(assignmentId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    public AssignmentSubmissionModel.Response getSubmissionByAssignmentAndStudent(
            UUID assignmentId,
            UUID studentId) {
        requireSelfIfTrainee(studentId);
        return repository.findByAssignmentIdAndStudentId(assignmentId, studentId)
                .map(this::mapToResponse)
                .orElse(null);
    }

    private Assignment loadAssignment(UUID assignmentId) {
        return assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new EntityNotFoundException("Assignment not found"));
    }

    private void assertAssignmentOpen(Assignment assignment) {
        if (assignment.getDueDate() != null && Instant.now().isAfter(assignment.getDueDate())) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Assignment due date has passed");
        }
    }

    private boolean canReopenExpiredSubmission(
            AssignmentSubmission submission,
            Assignment assignment) {
        return submission.getStatus() == SubmissionStatus.EXPIRED
                && assignment.getDueDate() != null
                && Instant.now().isBefore(assignment.getDueDate());
    }

    private boolean canResubmitFinalSubmission(
            AssignmentSubmission submission,
            Assignment assignment) {
        return isFinalStatus(submission.getStatus())
                && (assignment.getDueDate() == null
                || Instant.now().isBefore(assignment.getDueDate()));
    }

    private boolean hasSubmittedWork(SubmissionStatus status) {
        return status == SubmissionStatus.SUBMITTED
                || status == SubmissionStatus.GRADED
                || status == SubmissionStatus.LATE;
    }

    private boolean isFinalStatus(SubmissionStatus status) {
        return status == SubmissionStatus.SUBMITTED
                || status == SubmissionStatus.GRADED
                || status == SubmissionStatus.LATE
                || status == SubmissionStatus.EXPIRED;
    }

    private void broadcast(
            AssignmentSubmissionModel.Response response,
            Assignment assignment,
            String studentName) {
        MonitorEvent event = new MonitorEvent();
        event.setTargetId(response.getAssignmentId());
        event.setSubmissionId(response.getId());
        event.setStudentId(response.getStudentId());
        event.setStudentName(studentName == null ? response.getStudentName() : studentName);
        event.setType("essay");
        event.setStatus(response.getStatus() == null ? "DOING" : response.getStatus().name());
        event.setStartTime(response.getStartTime());
        event.setEndTime(assignment.getDueDate());
        event.setRemainingSeconds(remainingSeconds(assignment.getDueDate()));
        event.setScore(response.getScore());
        event.setFileUrl(response.getFileUrl());
        event.setFileName(response.getFileName());
        messagingTemplate.convertAndSend(
                "/topic/assignments/monitor/" + response.getAssignmentId(),
                event);
        messagingTemplate.convertAndSend("/topic/assignments/monitor", event);
    }

    private AssignmentSubmissionModel.Response mapToResponse(AssignmentSubmission submission) {
        AssignmentSubmissionModel.Response response = new AssignmentSubmissionModel.Response();
        response.setId(submission.getId());
        response.setAssignmentId(submission.getAssignmentId());
        response.setStudentId(submission.getStudentId());
        response.setStudentName(resolveStudentName(submission.getStudentId()));
        response.setSubmissionText(submission.getSubmissionText());
        response.setFileUrl(submission.getFileUrl());
        response.setFileName(submission.getFileName());
        response.setStartTime(submission.getStartTime());
        response.setSubmittedAt(submission.getSubmittedAt());
        response.setIsLate(submission.getIsLate());
        response.setScore(submission.getScore());
        response.setAiFeedback(submission.getAiFeedback());
        response.setTrainerFeedback(submission.getTrainerFeedback());
        response.setStatus(submission.getStatus());
        response.setGradedBy(submission.getGradedBy());
        response.setGradedAt(submission.getGradedAt());
        response.setCreatedAt(submission.getCreatedAt());
        response.setUpdatedAt(submission.getUpdatedAt());
        return response;
    }

    private String resolveStudentName(UUID studentId) {
        if (studentId == null) {
            return null;
        }
        return userRepository.findByIdAndDeletedAtIsNull(studentId)
                .map(user -> {
                    if (user.getFullName() != null && !user.getFullName().isBlank()) {
                        return user.getFullName();
                    }
                    return user.getEmail();
                })
                .orElse(null);
    }

    private Long remainingSeconds(Instant endTime) {
        if (endTime == null) {
            return null;
        }
        return Math.max(0, Duration.between(Instant.now(), endTime).getSeconds());
    }

    private UUID required(UUID value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    private void requireSelfIfTrainee(UUID studentId) {
        UserAccount actor = currentUserService.requireAuthenticatedUser();
        if ("TRAINEE".equalsIgnoreCase(actor.getRole())
                && !actor.getId().equals(studentId)) {
            throw new BusinessException(
                    ErrorCode.FORBIDDEN,
                    "You can only access your own assignment submission");
        }
    }
}
