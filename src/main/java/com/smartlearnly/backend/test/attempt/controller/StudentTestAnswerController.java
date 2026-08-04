
package com.smartlearnly.backend.test.attempt.controller;

import com.smartlearnly.backend.test.attempt.dto.StudentTestAnswerModel;
import com.smartlearnly.backend.test.attempt.service.StudentTestAnswerService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/student-test-answers")
@RequiredArgsConstructor
public class StudentTestAnswerController {

    private final StudentTestAnswerService service;

    /** Lưu đáp án đang làm của học viên cho một attempt. */
    @PostMapping("/save")
    public ResponseEntity<StudentTestAnswerModel.Response>
    saveStudentAnswer(
            @Valid @RequestBody
            StudentTestAnswerModel.SaveRequest request) {

        StudentTestAnswerModel.Response response =
                service.saveStudentAnswer(request);

        return new ResponseEntity<>(
                response,
                HttpStatus.OK);
    }

    /** Lưu kết quả chấm thủ công cho một đáp án. */
    @PutMapping("/{id}/grade")
    public ResponseEntity<StudentTestAnswerModel.Response>
    gradeStudentAnswer(
            @PathVariable UUID id,
            @Valid @RequestBody
            StudentTestAnswerModel.GradeRequest request) {

        return ResponseEntity.ok(
                service.gradeStudentAnswer(
                        id,
                        request));
    }

    /** Lấy các đáp án thuộc một attempt. */
    @GetMapping("/attempt/{attemptId}")
    public ResponseEntity<
            List<StudentTestAnswerModel.Response>>
    getAnswersByAttempt(
            @PathVariable UUID attemptId) {

        return ResponseEntity.ok(
                service.getAnswersByAttempt(
                        attemptId));
    }
}

