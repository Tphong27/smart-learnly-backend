package com.smartlearnly.backend.test.attempt.controller;

import com.smartlearnly.backend.test.attempt.dto.StudentTestAnswerModel;
import com.smartlearnly.backend.test.attempt.service.StudentTestAnswerService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/course-quiz-answers")
@RequiredArgsConstructor
public class StudentTestAnswerController {

    private final StudentTestAnswerService service;

    /** Lưu lựa chọn đáp án hiện tại của trainee trong course quiz. */
    @PostMapping("/save")
    @PreAuthorize("hasRole('TRAINEE')")
    public ResponseEntity<StudentTestAnswerModel.Response> saveStudentAnswer(
            @Valid @RequestBody StudentTestAnswerModel.SaveRequest request) {
        return ResponseEntity.ok(service.saveStudentAnswer(request));
    }

    /** Trả các đáp án thuộc attempt course quiz sau khi xác thực quyền xem. */
    @GetMapping("/attempt/{attemptId}")
    @PreAuthorize("hasAnyRole('TMO', 'SME', 'TRAINER', 'TRAINEE')")
    public ResponseEntity<List<StudentTestAnswerModel.Response>> getAnswersByAttempt(
            @PathVariable UUID attemptId) {
        return ResponseEntity.ok(service.getAnswersByAttempt(attemptId));
    }
}
