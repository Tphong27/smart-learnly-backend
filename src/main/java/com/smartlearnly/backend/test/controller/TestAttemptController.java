
package com.smartlearnly.backend.test.controller;

import com.smartlearnly.backend.test.dto.TestAttemptModel;
import com.smartlearnly.backend.test.service.TestAttemptService;
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

    @GetMapping("/test/{testId}/student/{studentId}")
    @PreAuthorize("hasAnyRole('TRAINEE', 'ADMIN', 'TMO', 'SME', 'TRAINER')")
    public ResponseEntity<List<TestAttemptModel.Response>>
    getAttempts(
            @PathVariable UUID testId,
            @PathVariable UUID studentId) {

        return ResponseEntity.ok(
                service.getAttempts(
                        testId,
                        studentId));
    }

    @GetMapping("/test/{testId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TMO', 'SME', 'TRAINER')")
    public ResponseEntity<List<TestAttemptModel.Response>>
    getAttemptsByTest(@PathVariable UUID testId) {
        return ResponseEntity.ok(service.getAttemptsByTest(testId));
    }

    @GetMapping("/{attemptId}")
    @PreAuthorize("hasAnyRole('TRAINEE', 'ADMIN', 'TMO', 'SME', 'TRAINER')")
    public ResponseEntity<TestAttemptModel.Response> getAttemptById(
            @PathVariable UUID attemptId) {
        return ResponseEntity.ok(service.getAttemptById(attemptId));
    }

    @PutMapping("/test/{testId}/student/{studentId}/reopen")
    @PreAuthorize("hasAnyRole('ADMIN', 'TMO', 'SME', 'TRAINER')")
    public ResponseEntity<Void> reopenAttempt(
            @PathVariable UUID testId,
            @PathVariable UUID studentId) {
        service.reopenAttempt(testId, studentId);
        return ResponseEntity.noContent().build();
    }
}

