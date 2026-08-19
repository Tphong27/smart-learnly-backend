package com.smartlearnly.backend.test.definition.controller;

import com.smartlearnly.backend.test.definition.dto.TestModel;
import com.smartlearnly.backend.test.definition.service.TestService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/course-quizzes")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class TestController {

    private final TestService testService;

    @GetMapping
    public ResponseEntity<List<TestModel.Response>> list(
            @RequestParam(required = false) UUID courseId,
            @RequestParam(required = false) UUID classId,
            @RequestParam(defaultValue = "managed") String scope) {
        List<TestModel.Response> tests = "available".equalsIgnoreCase(scope)
                ? testService.getAvailableTests(courseId, classId)
                : testService.getManagedTests(courseId, classId);
        return ResponseEntity.ok(tests);
    }

    @PostMapping
    public ResponseEntity<TestModel.Response> create(@RequestBody TestModel.CreateRequest request) {
        TestModel.Response created = testService.createTest(request);
        return ResponseEntity
                .created(URI.create("/api/v1/course-quizzes/" + created.getId()))
                .body(created);
    }

    /** Trả cấu hình quiz nhúng trong course sau khi xác thực quyền học hoặc quản lý course. */
    @GetMapping("/{id}")
    public ResponseEntity<TestModel.Response> getById(
            @PathVariable UUID id,
            @RequestParam(required = false) UUID classId) {
        return ResponseEntity.ok(testService.getTestById(id, classId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TestModel.Response> update(
            @PathVariable UUID id,
            @RequestBody TestModel.UpdateRequest request) {
        return ResponseEntity.ok(testService.updateTest(id, request));
    }

    /** Cập nhật số phút còn lại cho test và các attempt đang làm. */
    @PatchMapping("/{id}/duration")
    public ResponseEntity<TestModel.Response> updateDuration(
            @PathVariable UUID id,
            @Valid @RequestBody TestModel.DurationUpdateRequest request) {
        return ResponseEntity.ok(testService.updateDuration(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archive(@PathVariable UUID id) {
        testService.archiveTest(id);
    }

    @PostMapping("/{id}/access-code/verify")
    public ResponseEntity<TestModel.AccessCodeVerifyResponse> verifyAccessCode(
            @PathVariable UUID id,
            @RequestParam(required = false) UUID classId,
            @RequestBody TestModel.AccessCodeVerifyRequest request) {
        return ResponseEntity.ok(testService.verifyAccessCode(id, request, classId));
    }
}
