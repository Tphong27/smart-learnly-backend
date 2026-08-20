package com.smartlearnly.backend.curriculum.controller;

import com.smartlearnly.backend.common.api.ApiResponse;
import com.smartlearnly.backend.curriculum.dto.ReorderRequest;
import com.smartlearnly.backend.curriculum.service.TrainerLessonQuestionService;
import com.smartlearnly.backend.test.definition.dto.TestQuestionModel;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Trainer quiz question CRUD scoped to a class-draft lesson. Mirrors admin
 * TestQuestion endpoints but every call verifies the lesson belongs to the
 * trainer's class draft curriculum.
 */
@Validated
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('TRAINER', 'TMO')")
@RequestMapping("/api/v1/trainer/classes/{classId}/curriculum/lessons/{lessonId}/questions")
public class TrainerLessonQuestionController {

    private final TrainerLessonQuestionService trainerLessonQuestionService;

    /** Liệt kê câu hỏi quiz của lesson để TMO vẫn có thể xem curriculum lớp. */
    @GetMapping
    public ApiResponse<List<TestQuestionModel.Response>> listQuestions(
            @PathVariable UUID classId,
            @PathVariable UUID lessonId
    ) {
        return ApiResponse.success(
                "Questions loaded successfully",
                trainerLessonQuestionService.listQuestions(classId, lessonId)
        );
    }

    /** Gắn câu hỏi vào quiz lesson; chỉ Trainer hoặc Admin được thay đổi. */
    @PostMapping
    @PreAuthorize("hasRole('TRAINER')")
    public ResponseEntity<ApiResponse<TestQuestionModel.Response>> attachQuestion(
            @PathVariable UUID classId,
            @PathVariable UUID lessonId,
            @Valid @RequestBody TestQuestionModel.AddRequest request
    ) {
        TestQuestionModel.Response response = trainerLessonQuestionService.attachQuestion(classId, lessonId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Question attached successfully", response));
    }

    /** Cập nhật câu hỏi đã gắn trong class curriculum draft. */
    @PutMapping("/{questionId}")
    @PreAuthorize("hasRole('TRAINER')")
    public ApiResponse<TestQuestionModel.Response> updateQuestion(
            @PathVariable UUID classId,
            @PathVariable UUID lessonId,
            @PathVariable UUID questionId,
            @Valid @RequestBody TestQuestionModel.UpdateRequest request
    ) {
        return ApiResponse.success(
                "Question updated successfully",
                trainerLessonQuestionService.updateQuestion(classId, lessonId, questionId, request)
        );
    }

    /** Gỡ câu hỏi khỏi quiz lesson trong class curriculum draft. */
    @DeleteMapping("/{questionId}")
    @PreAuthorize("hasRole('TRAINER')")
    public ApiResponse<Void> detachQuestion(
            @PathVariable UUID classId,
            @PathVariable UUID lessonId,
            @PathVariable UUID questionId
    ) {
        trainerLessonQuestionService.detachQuestion(classId, lessonId, questionId);
        return ApiResponse.success("Question detached successfully");
    }

    /** Lưu thứ tự câu hỏi mới trong quiz lesson của lớp. */
    @PostMapping("/reorder")
    @PreAuthorize("hasRole('TRAINER')")
    public ApiResponse<List<TestQuestionModel.Response>> reorderQuestions(
            @PathVariable UUID classId,
            @PathVariable UUID lessonId,
            @Valid @RequestBody ReorderRequest request
    ) {
        return ApiResponse.success(
                "Questions reordered successfully",
                trainerLessonQuestionService.reorderQuestions(classId, lessonId, request)
        );
    }
}
