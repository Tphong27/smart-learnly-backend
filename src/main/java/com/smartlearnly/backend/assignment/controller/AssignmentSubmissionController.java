
package com.smartlearnly.backend.assignment.controller;

import com.smartlearnly.backend.assignment.dto.AssignmentSubmissionModel;
import com.smartlearnly.backend.assignment.service.AssignmentSubmissionService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/submissions")
@RequiredArgsConstructor
public class AssignmentSubmissionController {

    private final AssignmentSubmissionService submissionService;

    @PostMapping
    public ResponseEntity<AssignmentSubmissionModel.Response> submitAssignment(
            @Valid @RequestBody AssignmentSubmissionModel.CreateRequest request) {

        AssignmentSubmissionModel.Response response =
                submissionService.submitAssignment(request);

        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<AssignmentSubmissionModel.Response> updateSubmission(
            @PathVariable UUID id,
            @Valid @RequestBody AssignmentSubmissionModel.UpdateRequest request) {

        AssignmentSubmissionModel.Response response =
                submissionService.updateSubmission(id, request);

        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/grade")
    public ResponseEntity<AssignmentSubmissionModel.Response> gradeSubmission(
            @PathVariable UUID id,
            @Valid @RequestBody AssignmentSubmissionModel.GradeRequest request) {

        AssignmentSubmissionModel.Response response =
                submissionService.gradeSubmission(id, request);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/assignment/{assignmentId}")
    public ResponseEntity<List<AssignmentSubmissionModel.Response>>
    getSubmissionsByAssignment(
            @PathVariable UUID assignmentId) {

        List<AssignmentSubmissionModel.Response> responses =
                submissionService.getSubmissionsByAssignment(assignmentId);

        return ResponseEntity.ok(responses);
    }
}

