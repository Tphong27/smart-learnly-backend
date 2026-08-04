package com.smartlearnly.backend.test.definition.controller;

import com.smartlearnly.backend.test.definition.dto.TestQuestionModel;
import com.smartlearnly.backend.test.definition.service.TestQuestionService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/test-questions")
@RequiredArgsConstructor
public class TestQuestionController {

    private final TestQuestionService service;

    /** Gắn câu hỏi vào đề cho nhân sự có quyền biên tập. */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SME', 'TMO', 'TRAINER')")
    public ResponseEntity<TestQuestionModel.Response>
    addQuestionToTest(
            @Valid @RequestBody
            TestQuestionModel.AddRequest request) {

        TestQuestionModel.Response response =
                service.addQuestionToTest(request);

        return new ResponseEntity<>(
                response,
                HttpStatus.CREATED);
    }

    /** Trả câu hỏi ở định dạng an toàn cho học viên làm đề. */
    @GetMapping("/test/{testId}")
    public ResponseEntity<List<TestQuestionModel.LearnerResponse>>
    getLearnerQuestionsByTest(
            @PathVariable UUID testId) {

        return ResponseEntity.ok(
                service.getLearnerQuestionsByTest(testId));
    }

    /** Đổi thứ tự hoặc số điểm của câu hỏi đã gắn vào đề. */
    @PutMapping("/test/{testId}/question/{questionId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SME', 'TMO', 'TRAINER')")
    public ResponseEntity<TestQuestionModel.Response>
    updateTestQuestion(
            @PathVariable UUID testId,
            @PathVariable UUID questionId,
            @Valid @RequestBody
            TestQuestionModel.UpdateRequest request) {

        return ResponseEntity.ok(
                service.updateTestQuestion(
                        testId,
                        questionId,
                        request));
    }

    /** Gỡ câu hỏi khỏi đề cho nhân sự có quyền biên tập. */
    @DeleteMapping("/test/{testId}/question/{questionId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SME', 'TMO', 'TRAINER')")
    public ResponseEntity<Void>
    removeQuestionFromTest(
            @PathVariable UUID testId,
            @PathVariable UUID questionId) {

        service.removeQuestionFromTest(
                testId,
                questionId);

        return ResponseEntity.noContent().build();
    }
}
