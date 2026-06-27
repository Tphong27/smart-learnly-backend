package com.smartlearnly.backend.test.controller;

import com.smartlearnly.backend.test.dto.TestQuestionModel;
import com.smartlearnly.backend.test.service.TestQuestionService;
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

    @GetMapping("/test/{testId}")
    public ResponseEntity<List<TestQuestionModel.LearnerResponse>>
    getLearnerQuestionsByTest(
            @PathVariable UUID testId) {

        return ResponseEntity.ok(
                service.getLearnerQuestionsByTest(testId));
    }

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
