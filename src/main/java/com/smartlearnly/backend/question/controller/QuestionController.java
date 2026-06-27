package com.smartlearnly.backend.question.controller;

import com.smartlearnly.backend.common.api.ApiResponse;
import com.smartlearnly.backend.common.api.PageResponse;
import com.smartlearnly.backend.question.dto.QuestionModel;
import com.smartlearnly.backend.question.service.QuestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SME', 'TMO', 'TRAINER')")
@RequestMapping("/api/v1/admin/questions")
@Tag(name = "Admin Questions", description = "Question Bank question management APIs.")
@SecurityRequirement(name = "bearerAuth")
public class QuestionController {
    private final QuestionService questionService;

    @GetMapping
    @Operation(summary = "List questions for staff")
    public ApiResponse<PageResponse<QuestionModel.Response>> list(
            @RequestParam(required = false) UUID bankId,
            @RequestParam(required = false) UUID questionBankId,
            @RequestParam(required = false) UUID courseId,
            @RequestParam(name = "course_id", required = false) UUID courseIdSnakeCase,
            @RequestParam(required = false) UUID moduleId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Short difficulty,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        UUID resolvedBankId = bankId != null ? bankId : questionBankId;
        UUID resolvedCourseId = courseId != null ? courseId : courseIdSnakeCase;
        return ApiResponse.success("Questions loaded successfully", questionService.list(resolvedBankId, resolvedCourseId, moduleId, search, type, status, difficulty, page, size));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SME')")
    @Operation(summary = "Create a question with answers")
    public ResponseEntity<ApiResponse<QuestionModel.Response>> create(@Valid @RequestBody QuestionModel.CreateRequest request) {
        QuestionModel.Response question = questionService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/admin/questions/" + question.questionId()))
                .body(ApiResponse.success("Question created successfully", question));
    }

    @GetMapping("/{questionId}")
    @Operation(summary = "Get question details")
    public ApiResponse<QuestionModel.Response> get(@PathVariable UUID questionId) {
        return ApiResponse.success("Question loaded successfully", questionService.get(questionId));
    }

    @PutMapping("/{questionId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SME')")
    @Operation(summary = "Update a question and replace its answers")
    public ApiResponse<QuestionModel.Response> update(@PathVariable UUID questionId, @Valid @RequestBody QuestionModel.UpdateRequest request) {
        return ApiResponse.success("Question updated successfully", questionService.update(questionId, request));
    }

    @DeleteMapping("/{questionId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SME')")
    @Operation(summary = "Archive a question")
    public ApiResponse<Void> archive(@PathVariable UUID questionId) {
        questionService.archive(questionId);
        return ApiResponse.success("Question archived successfully");
    }

    @PostMapping("/{questionId}/approve")
    @PreAuthorize("hasAnyRole('ADMIN', 'SME')")
    @Operation(summary = "Approve a question")
    public ApiResponse<QuestionModel.Response> approve(@PathVariable UUID questionId) {
        return ApiResponse.success("Question approved successfully", questionService.approve(questionId));
    }

    @PostMapping("/{questionId}/reject")
    @PreAuthorize("hasAnyRole('ADMIN', 'SME')")
    @Operation(summary = "Reject a question")
    public ApiResponse<QuestionModel.Response> reject(@PathVariable UUID questionId) {
        return ApiResponse.success("Question rejected successfully", questionService.reject(questionId));
    }
}
