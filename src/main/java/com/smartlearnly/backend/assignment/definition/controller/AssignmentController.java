package com.smartlearnly.backend.assignment.definition.controller;

import com.smartlearnly.backend.assignment.definition.dto.AssignmentModel;
import com.smartlearnly.backend.assignment.definition.service.AssignmentService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/assignments")
@PreAuthorize("isAuthenticated()")
public class AssignmentController {

    private final AssignmentService assignmentService;

    public AssignmentController(AssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }

    /** Tạo bài tập sau khi backend kiểm tra quyền và dữ liệu đầu vào. */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TMO', 'SME', 'TRAINER')")
    public ResponseEntity<AssignmentModel.Response> create(@Valid @RequestBody AssignmentModel.CreateRequest request) {
        AssignmentModel.Response response = assignmentService.createAssignment(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    /** Lấy toàn bộ bài tập cho màn hình quản trị có quyền phù hợp. */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TMO', 'SME', 'TRAINER')")
    public ResponseEntity<List<AssignmentModel.Response>> getAll() {
        List<AssignmentModel.Response> responses = assignmentService.getAllAssignments();
        return ResponseEntity.ok(responses);
    }

    /** Lấy bài tập mà nhân sự hiện tại được quản lý. */
    @GetMapping("/mine")
    @PreAuthorize("hasAnyRole('ADMIN', 'TMO', 'SME', 'TRAINER')")
    public ResponseEntity<List<AssignmentModel.Response>> getMine(
            @RequestParam(required = false) UUID courseId,
            @RequestParam(required = false) Boolean isFlashtest) {
        return ResponseEntity.ok(assignmentService.getMyAssignments(courseId, isFlashtest));
    }

    /** Lấy bài tập mà học viên hiện tại có thể thực hiện trong khóa/lớp đã chọn. */
    @GetMapping("/available")
    @PreAuthorize("hasRole('TRAINEE')")
    public ResponseEntity<List<AssignmentModel.Response>> getAvailable(
            @RequestParam(required = false) UUID courseId,
            @RequestParam(required = false) UUID classId,
            @RequestParam(required = false) Boolean isFlashtest) {
        return ResponseEntity.ok(
                assignmentService.getAvailableAssignments(courseId, classId, isFlashtest));
    }

    /** Lấy lớp mà người dùng có thể gán bài tập. */
    @GetMapping("/classes")
    @PreAuthorize("hasAnyRole('ADMIN', 'TMO', 'SME', 'TRAINER')")
    public ResponseEntity<List<AssignmentModel.ClassOptionResponse>> getAssignableClasses(
            @RequestParam(required = false) UUID courseId) {
        return ResponseEntity.ok(assignmentService.getAssignableClasses(courseId));
    }

    /** Lấy chi tiết một bài tập theo định danh. */
    @GetMapping("/{id}")
    public ResponseEntity<AssignmentModel.Response> getById(@PathVariable UUID id) {
        AssignmentModel.Response response = assignmentService.getAssignmentById(id);
        return ResponseEntity.ok(response);
    }

    /** Lấy bài tập gắn với lesson, ưu tiên lớp cụ thể khi có. */
    @GetMapping("/lesson/{lessonId}")
    public ResponseEntity<AssignmentModel.Response> getByLessonId(
            @PathVariable UUID lessonId,
            @RequestParam(required = false) UUID classId) {
        return assignmentService.findAssignmentByLessonId(lessonId, classId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Cập nhật cấu hình bài tập khi người dùng có quyền biên soạn. */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TMO', 'SME', 'TRAINER')")
    public ResponseEntity<AssignmentModel.Response> update(
            @PathVariable UUID id,
            @Valid @RequestBody AssignmentModel.UpdateRequest request) {
        AssignmentModel.Response response = assignmentService.updateAssignment(id, request);
        return ResponseEntity.ok(response);
    }

    /** Xóa bài tập theo quy tắc nghiệp vụ của service. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TMO', 'SME', 'TRAINER')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        assignmentService.deleteAssignment(id);
        return ResponseEntity.noContent().build();
    }
}
