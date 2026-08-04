package com.smartlearnly.backend.test.definition.controller;

import com.smartlearnly.backend.test.definition.dto.TestQuestionModel;
import com.smartlearnly.backend.test.definition.service.TestQuestionService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/test-questions")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SME', 'TMO', 'TRAINER')")
public class AdminTestQuestionController {

    private final TestQuestionService service;

    /** Trả câu hỏi đầy đủ, bao gồm dữ liệu chỉ dành cho nhân sự quản trị. */
    @GetMapping("/test/{testId}")
    public ResponseEntity<List<TestQuestionModel.Response>>
    getQuestionsByTest(
            @PathVariable UUID testId) {

        return ResponseEntity.ok(
                service.getQuestionsByTest(testId));
    }
}
