
package com.smartlearnly.backend.assignment.service;

import com.smartlearnly.backend.assignment.dto.AssignmentSubmissionModel;
import com.smartlearnly.backend.assignment.entity.AssignmentSubmission;
import com.smartlearnly.backend.assignment.entity.SubmissionStatus;
import com.smartlearnly.backend.assignment.repository.AssignmentSubmissionRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AssignmentSubmissionService {

    private final AssignmentSubmissionRepository repository;

    public AssignmentSubmissionModel.Response submitAssignment(
            AssignmentSubmissionModel.CreateRequest request) {

        AssignmentSubmission submission =
                new AssignmentSubmission();

        submission.setAssignmentId(request.getAssignmentId());
        submission.setStudentId(request.getStudentId());
        submission.setSubmissionText(
                request.getSubmissionText());
        submission.setFileUrl(request.getFileUrl());
        submission.setFileName(request.getFileName());
        submission.setStatus(SubmissionStatus.SUBMITTED);

        AssignmentSubmission saved = repository.save(submission);

        return mapToResponse(saved);
    }

    public AssignmentSubmissionModel.Response updateSubmission(
            UUID id,
            AssignmentSubmissionModel.UpdateRequest request) {

        AssignmentSubmission submission =
                repository.findById(id)
                        .orElseThrow(() ->
                                new EntityNotFoundException(
                                        "Submission not found"));

        submission.setSubmissionText(
                request.getSubmissionText());
        submission.setFileUrl(request.getFileUrl());
        submission.setFileName(request.getFileName());

        AssignmentSubmission updated =
                repository.save(submission);

        return mapToResponse(updated);
    }

    public AssignmentSubmissionModel.Response gradeSubmission(
            UUID id,
            AssignmentSubmissionModel.GradeRequest request) {

        AssignmentSubmission submission =
                repository.findById(id)
                        .orElseThrow(() ->
                                new EntityNotFoundException(
                                        "Submission not found"));

        submission.setScore(request.getScore());
        submission.setAiFeedback(
                request.getAiFeedback());
        submission.setTrainerFeedback(
                request.getTrainerFeedback());

        submission.setStatus(request.getStatus());

        submission.setGradedBy(request.getGradedBy());
        submission.setGradedAt(Instant.now());

        AssignmentSubmission updated =
                repository.save(submission);

        return mapToResponse(updated);
    }

    public List<AssignmentSubmissionModel.Response>
    getSubmissionsByAssignment(UUID assignmentId) {

        return repository.findByAssignmentId(assignmentId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    private AssignmentSubmissionModel.Response mapToResponse(
            AssignmentSubmission submission) {

        AssignmentSubmissionModel.Response response =
                new AssignmentSubmissionModel.Response();

        response.setId(submission.getId());
        response.setAssignmentId(
                submission.getAssignmentId());
        response.setStudentId(
                submission.getStudentId());
        response.setSubmissionText(
                submission.getSubmissionText());
        response.setFileUrl(submission.getFileUrl());
        response.setFileName(submission.getFileName());
        response.setSubmittedAt(
                submission.getSubmittedAt());
        response.setIsLate(submission.getIsLate());
        response.setScore(submission.getScore());
        response.setAiFeedback(
                submission.getAiFeedback());
        response.setTrainerFeedback(
                submission.getTrainerFeedback());
        response.setStatus(submission.getStatus());
        response.setGradedBy(
                submission.getGradedBy());
        response.setGradedAt(
                submission.getGradedAt());
        response.setCreatedAt(
                submission.getCreatedAt());
        response.setUpdatedAt(
                submission.getUpdatedAt());

        return response;
    }
}