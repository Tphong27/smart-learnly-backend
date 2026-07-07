package com.smartlearnly.backend.assignment.controller;

import com.smartlearnly.backend.assignment.dto.AssignmentModel;
import com.smartlearnly.backend.assignment.service.AssignmentService; // Đảm bảo import đúng gói service của bạn
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/assignments")
public class AssignmentController {

    private final AssignmentService assignmentService;

    // Tiêm Service qua Constructor giống hệt CourseController
    public AssignmentController(AssignmentService assignmentService) {
        this.assignmentService = assignmentService;
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

    @GetMapping("/available")
    public ResponseEntity<List<AssignmentModel.Response>> getAvailable(
            @RequestParam(required = false) UUID courseId,
            @RequestParam(required = false) Boolean isFlashtest) {
        return ResponseEntity.ok(assignmentService.getAvailableAssignments(courseId, isFlashtest));
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
    public ResponseEntity<AssignmentModel.Response> getByLessonId(@PathVariable UUID lessonId) {
        AssignmentModel.Response response = assignmentService.getAssignmentByLessonId(lessonId);
        return ResponseEntity.ok(response);
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
