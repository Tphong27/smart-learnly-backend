
package com.smartlearnly.backend.test.controller;

import com.smartlearnly.backend.test.dto.TestAttemptModel;
import com.smartlearnly.backend.test.service.TestAttemptService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/test-attempts")
@RequiredArgsConstructor
public class TestAttemptController {

    private final TestAttemptService service;

    @PostMapping("/start")
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

    @PutMapping("/{id}/submit")
    public ResponseEntity<TestAttemptModel.Response>
    submitAttempt(
            @PathVariable UUID id,
            @Valid @RequestBody
            TestAttemptModel.SubmitRequest request) {

        return ResponseEntity.ok(
                service.submitAttempt(id, request));
    }

    @GetMapping("/test/{testId}/student/{studentId}")
    public ResponseEntity<List<TestAttemptModel.Response>>
    getAttempts(
            @PathVariable UUID testId,
            @PathVariable UUID studentId) {

        return ResponseEntity.ok(
                service.getAttempts(
                        testId,
                        studentId));
    }
}

