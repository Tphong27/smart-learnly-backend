package com.smartlearnly.backend.test.definition.controller;

import com.smartlearnly.backend.test.definition.dto.TestQuestionModel;
import com.smartlearnly.backend.test.definition.service.TestQuestionService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/course-quizzes")
@RequiredArgsConstructor
public class TestQuestionController {

    private final TestQuestionService service;

    @PostMapping("/questions")
    public ResponseEntity<TestQuestionModel.Response> addQuestion(
            @RequestBody TestQuestionModel.AddRequest request) {
        return ResponseEntity.ok(service.addQuestionToTest(request));
    }

    @GetMapping("/{quizId}/staff-questions")
    public ResponseEntity<List<TestQuestionModel.Response>>
    getStaffQuestionsByQuiz(@PathVariable UUID quizId) {
        return ResponseEntity.ok(service.getQuestionsByTest(quizId));
    }

    /** Trả câu hỏi an toàn của quiz nhúng trong course cho người học đã xác thực. */
    @GetMapping("/{quizId}/questions")
    public ResponseEntity<List<TestQuestionModel.LearnerResponse>>
    getLearnerQuestionsByQuiz(@PathVariable UUID quizId) {
        return ResponseEntity.ok(service.getLearnerQuestionsByTest(quizId));
    }

    @PutMapping("/{quizId}/questions/{questionId}")
    public ResponseEntity<TestQuestionModel.Response> updateQuestion(
            @PathVariable UUID quizId,
            @PathVariable UUID questionId,
            @RequestBody TestQuestionModel.UpdateRequest request) {
        return ResponseEntity.ok(service.updateTestQuestion(quizId, questionId, request));
    }

    @DeleteMapping("/{quizId}/questions/{questionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeQuestion(
            @PathVariable UUID quizId,
            @PathVariable UUID questionId) {
        service.removeQuestionFromTest(quizId, questionId);
    }
}
