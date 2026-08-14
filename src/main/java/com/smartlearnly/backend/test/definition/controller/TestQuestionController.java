package com.smartlearnly.backend.test.definition.controller;

import com.smartlearnly.backend.test.definition.dto.TestQuestionModel;
import com.smartlearnly.backend.test.definition.service.TestQuestionService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/course-quizzes")
@RequiredArgsConstructor
public class TestQuestionController {

    private final TestQuestionService service;

    /** Trả câu hỏi an toàn của quiz nhúng trong course cho người học đã xác thực. */
    @GetMapping("/{quizId}/questions")
    public ResponseEntity<List<TestQuestionModel.LearnerResponse>>
    getLearnerQuestionsByQuiz(@PathVariable UUID quizId) {
        return ResponseEntity.ok(service.getLearnerQuestionsByTest(quizId));
    }
}
