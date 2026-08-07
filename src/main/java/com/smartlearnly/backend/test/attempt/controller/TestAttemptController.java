
package com.smartlearnly.backend.test.attempt.controller;

import com.smartlearnly.backend.test.attempt.dto.TestAttemptModel;
import com.smartlearnly.backend.test.attempt.service.TestAttemptService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/test-attempts")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class TestAttemptController {

    private final TestAttemptService service;

    /** Bắt đầu lần làm bài của học viên đang đăng nhập. */
    @PostMapping("/start")
    @PreAuthorize("hasRole('TRAINEE')")
    public ResponseEntity<TestAttemptModel.Response>
    startAttempt(
            @Valid @RequestBody
            TestAttemptModel.StartRequest request) {

        TestAttemptModel.Response response =
                service.startAttempt(request);

        return new ResponseEntity<>(
                response,
                HttpStatus.CREATED);
    }

    /** Nộp lần làm bài hiện tại để hệ thống chấm và khóa attempt. */
    @PutMapping("/{id}/submit")
    @PreAuthorize("hasRole('TRAINEE')")
    public ResponseEntity<TestAttemptModel.Response>
    submitAttempt(
            @PathVariable UUID id,
            @Valid @RequestBody
            TestAttemptModel.SubmitRequest request) {

        return ResponseEntity.ok(
                service.submitAttempt(id, request));
    }

    /** Lấy lịch sử attempt của một học viên theo đề khi caller có quyền xem. */
    @GetMapping("/test/{testId}/student/{studentId}")
    @PreAuthorize("hasAnyRole('TRAINEE', 'ADMIN', 'TMO', 'SME', 'TRAINER')")
    public ResponseEntity<List<TestAttemptModel.Response>>
    getAttempts(
            @PathVariable UUID testId,
            @PathVariable UUID studentId,
            @RequestParam(required = false) UUID classId) {

        return ResponseEntity.ok(
                service.getAttempts(
                        testId,
                        studentId,
                        classId));
    }

    /** Lấy danh sách attempt của đề cho nhân sự quản lý. */
    @GetMapping("/test/{testId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TMO', 'SME', 'TRAINER')")
    public ResponseEntity<List<TestAttemptModel.Response>>
    getAttemptsByTest(@PathVariable UUID testId) {
        return ResponseEntity.ok(service.getAttemptsByTest(testId));
    }

    /** Lấy chi tiết một attempt mà caller được phép truy cập. */
    @GetMapping("/{attemptId}")
    @PreAuthorize("hasAnyRole('TRAINEE', 'ADMIN', 'TMO', 'SME', 'TRAINER')")
    public ResponseEntity<TestAttemptModel.Response> getAttemptById(
            @PathVariable UUID attemptId,
            @RequestParam(required = false) UUID classId) {
        return ResponseEntity.ok(service.getAttemptById(attemptId, classId));
    }

    /** Mở quyền làm lại cho attempt gần nhất của học viên. */
    @PutMapping("/test/{testId}/student/{studentId}/reopen")
    @PreAuthorize("hasAnyRole('ADMIN', 'TMO', 'SME', 'TRAINER')")
    public ResponseEntity<Void> reopenAttempt(
            @PathVariable UUID testId,
            @PathVariable UUID studentId) {
        service.reopenAttempt(testId, studentId);
        return ResponseEntity.noContent().build();
    }
}

