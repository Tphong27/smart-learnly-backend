
package com.smartlearnly.backend.assignment.service;

import com.smartlearnly.backend.assignment.dto.AssignmentModel;
import com.smartlearnly.backend.assignment.entity.Assignment;
import com.smartlearnly.backend.assignment.repository.AssignmentRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AssignmentService {

    private final AssignmentRepository assignmentRepository;

    public AssignmentModel.Response createAssignment(
            AssignmentModel.CreateRequest request) {

        Assignment assignment = new Assignment();

        assignment.setClassId(request.getClassId());
        assignment.setTitle(request.getTitle());
        assignment.setDescription(request.getDescription());
        assignment.setInstructionFileUrl(request.getInstructionFileUrl());
        assignment.setInstructionFileName(request.getInstructionFileName());
        assignment.setDueDate(request.getDueDate());
        assignment.setAllowLateSubmission(request.getAllowLateSubmission());
        assignment.setLockoutDate(request.getLockoutDate());
        assignment.setMaxScore(request.getMaxScore());
        assignment.setTestId(request.getTestId());

        Assignment saved = assignmentRepository.save(assignment);

        return mapToResponse(saved);
    }

    public List<AssignmentModel.Response> getAllAssignments() {

        return assignmentRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    public AssignmentModel.Response getAssignmentById(UUID id) {

        Assignment assignment = assignmentRepository.findById(id)
                .orElseThrow(() ->
                        new EntityNotFoundException("Assignment not found"));

        return mapToResponse(assignment);
    }

    public AssignmentModel.Response updateAssignment(
            UUID id,
            AssignmentModel.UpdateRequest request) {

        Assignment assignment = assignmentRepository.findById(id)
                .orElseThrow(() ->
                        new EntityNotFoundException("Assignment not found"));

        assignment.setTitle(request.getTitle());
        assignment.setDescription(request.getDescription());
        assignment.setInstructionFileUrl(request.getInstructionFileUrl());
        assignment.setInstructionFileName(request.getInstructionFileName());
        assignment.setDueDate(request.getDueDate());
        assignment.setAllowLateSubmission(request.getAllowLateSubmission());
        assignment.setLockoutDate(request.getLockoutDate());
        assignment.setMaxScore(request.getMaxScore());
        assignment.setIsArchived(request.getIsArchived());
        assignment.setTestId(request.getTestId());

        Assignment updated = assignmentRepository.save(assignment);

        return mapToResponse(updated);
    }

    public void deleteAssignment(UUID id) {

        if (!assignmentRepository.existsById(id)) {
            throw new EntityNotFoundException("Assignment not found");
        }

        assignmentRepository.deleteById(id);
    }

    private AssignmentModel.Response mapToResponse(Assignment assignment) {

        AssignmentModel.Response response =
                new AssignmentModel.Response();

        response.setId(assignment.getId());
        response.setClassId(assignment.getClassId());
        response.setTitle(assignment.getTitle());
        response.setDescription(assignment.getDescription());
        response.setInstructionFileUrl(
                assignment.getInstructionFileUrl());
        response.setInstructionFileName(
                assignment.getInstructionFileName());
        response.setDueDate(assignment.getDueDate());
        response.setAllowLateSubmission(
                assignment.getAllowLateSubmission());
        response.setLockoutDate(assignment.getLockoutDate());
        response.setMaxScore(assignment.getMaxScore());
        response.setIsArchived(assignment.getIsArchived());
        response.setCreatedBy(assignment.getCreatedBy());
        response.setCreatedAt(assignment.getCreatedAt());
        response.setUpdatedAt(assignment.getUpdatedAt());
        response.setTestId(assignment.getTestId());

        return response;
    }
}

