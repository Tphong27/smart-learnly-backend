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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/course-quiz-attempts")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class TestAttemptController {

    private final TestAttemptService service;

    /** Bắt đầu hoặc tiếp tục attempt của quiz đang mở trong course. */
    @PostMapping("/start")
    @PreAuthorize("hasRole('TRAINEE')")
    public ResponseEntity<TestAttemptModel.Response> startAttempt(
            @Valid @RequestBody TestAttemptModel.StartRequest request) {
        return new ResponseEntity<>(service.startAttempt(request), HttpStatus.CREATED);
    }

    /** Nộp attempt course quiz hiện tại để chấm điểm và khóa lần làm bài. */
    @PutMapping("/{id}/submit")
    @PreAuthorize("hasRole('TRAINEE')")
    public ResponseEntity<TestAttemptModel.Response> submitAttempt(
            @PathVariable UUID id,
            @Valid @RequestBody TestAttemptModel.SubmitRequest request) {
        return ResponseEntity.ok(service.submitAttempt(id, request));
    }

    /** Trả lịch sử attempt của trainee cho một course quiz sau khi kiểm tra quyền. */
    @GetMapping("/quiz/{quizId}/student/{studentId}")
    @PreAuthorize("hasAnyRole('TRAINEE', 'TMO', 'SME', 'TRAINER')")
    public ResponseEntity<List<TestAttemptModel.Response>> getAttempts(
            @PathVariable UUID quizId,
            @PathVariable UUID studentId,
            @RequestParam(required = false) UUID classId) {
        return ResponseEntity.ok(service.getAttempts(quizId, studentId, classId));
    }

    /** Trả chi tiết attempt course quiz mà caller được phép xem. */
    @GetMapping("/quiz/{quizId}")
    @PreAuthorize("hasAnyRole('TMO', 'SME', 'TRAINER')")
    public ResponseEntity<List<TestAttemptModel.Response>> getAttemptsByTest(
            @PathVariable UUID quizId) {
        return ResponseEntity.ok(service.getAttemptsByTest(quizId));
    }

    @GetMapping("/{attemptId}")
    @PreAuthorize("hasAnyRole('TRAINEE', 'TMO', 'SME', 'TRAINER')")
    public ResponseEntity<TestAttemptModel.Response> getAttemptById(
            @PathVariable UUID attemptId,
            @RequestParam(required = false) UUID classId) {
        return ResponseEntity.ok(service.getAttemptById(attemptId, classId));
    }
}
