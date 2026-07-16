package com.smartlearnly.backend.assignment.controller;

import com.smartlearnly.backend.assignment.dto.AssignmentModel;
import com.smartlearnly.backend.assignment.dto.AssignmentAiDraftModel;
import com.smartlearnly.backend.assignment.service.AssignmentAiDraftService;
import com.smartlearnly.backend.assignment.service.AssignmentService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/assignments")
public class AssignmentController {

    private final AssignmentService assignmentService;
    private final AssignmentAiDraftService assignmentAiDraftService;

    public AssignmentController(
            AssignmentService assignmentService,
            AssignmentAiDraftService assignmentAiDraftService) {
        this.assignmentService = assignmentService;
        this.assignmentAiDraftService = assignmentAiDraftService;
    }

    @PostMapping(value = "/ai-draft", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AssignmentAiDraftModel.Response> generateAiDraft(
            @RequestParam String message,
            @RequestParam(required = false) String mode,
            @RequestParam(required = false) String currentTitle,
            @RequestParam(required = false) String currentDescription,
            @RequestPart(value = "file", required = false) MultipartFile file) {
        AssignmentAiDraftModel.Response response = assignmentAiDraftService.generateDraft(
                message,
                mode,
                currentTitle,
                currentDescription,
                file);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<AssignmentModel.Response> create(@Valid @RequestBody AssignmentModel.CreateRequest request) {
        AssignmentModel.Response response = assignmentService.createAssignment(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<AssignmentModel.Response>> getAll() {
        List<AssignmentModel.Response> responses = assignmentService.getAllAssignments();
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/mine")
    public ResponseEntity<List<AssignmentModel.Response>> getMine(
            @RequestParam(required = false) UUID courseId,
            @RequestParam(required = false) Boolean isFlashtest) {
        return ResponseEntity.ok(assignmentService.getMyAssignments(courseId, isFlashtest));
    }

    // @GetMapping("/available")
    // public ResponseEntity<List<AssignmentModel.Response>> getAvailable(
    //         @RequestParam(required = false) UUID courseId,
    //         @RequestParam(required = false) Boolean isFlashtest) {
    //     return ResponseEntity.ok(assignmentService.getAvailableAssignments(courseId, isFlashtest));
    // }

    @GetMapping("/available")
    public ResponseEntity<List<AssignmentModel.Response>> getAvailable(
            @RequestParam(required = false) UUID courseId,
            @RequestParam(required = false) UUID classId,
            @RequestParam(required = false) Boolean isFlashtest) {
        return ResponseEntity.ok(
                assignmentService.getAvailableAssignments(courseId, classId, isFlashtest));
    }

    @GetMapping("/classes")
    public ResponseEntity<List<AssignmentModel.ClassOptionResponse>> getAssignableClasses(
            @RequestParam(required = false) UUID courseId) {
        return ResponseEntity.ok(assignmentService.getAssignableClasses(courseId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AssignmentModel.Response> getById(@PathVariable UUID id) {
        AssignmentModel.Response response = assignmentService.getAssignmentById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/lesson/{lessonId}")
    public ResponseEntity<AssignmentModel.Response> getByLessonId(
            @PathVariable UUID lessonId,
            @RequestParam(required = false) UUID classId) {
        return assignmentService.findAssignmentByLessonId(lessonId, classId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<AssignmentModel.Response> update(
            @PathVariable UUID id,
            @Valid @RequestBody AssignmentModel.UpdateRequest request) {
        AssignmentModel.Response response = assignmentService.updateAssignment(id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        assignmentService.deleteAssignment(id);
        return ResponseEntity.noContent().build();
    }
}
