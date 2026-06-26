package com.smartlearnly.backend.question.controller;

import com.smartlearnly.backend.common.api.ApiResponse;
import com.smartlearnly.backend.question.dto.QuestionBankDto;
import com.smartlearnly.backend.question.service.QuestionBankService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
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
@RequestMapping("/api/v1/admin/question-banks")
@Tag(name = "Admin Question Banks", description = "Question Bank management APIs.")
@SecurityRequirement(name = "bearerAuth")
public class QuestionBankController {
    private final QuestionBankService questionBankService;

    @GetMapping
    @Operation(summary = "List question banks")
    public ApiResponse<List<QuestionBankDto.Response>> list(@RequestParam(required = false) UUID courseId, @RequestParam(required = false) String search, @RequestParam(required = false) String status) {
        return ApiResponse.success("Question banks loaded successfully", questionBankService.list(courseId, search, status));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SME')")
    @Operation(summary = "Create a question bank")
    public ResponseEntity<ApiResponse<QuestionBankDto.Response>> create(@Valid @RequestBody QuestionBankDto.CreateRequest request) {
        QuestionBankDto.Response bank = questionBankService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/admin/question-banks/" + bank.bankId()))
                .body(ApiResponse.success("Question bank created successfully", bank));
    }

    @GetMapping("/{bankId}")
    @Operation(summary = "Get question bank details")
    public ApiResponse<QuestionBankDto.Response> get(@PathVariable UUID bankId) {
        return ApiResponse.success("Question bank loaded successfully", questionBankService.get(bankId));
    }

    @PutMapping("/{bankId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SME')")
    @Operation(summary = "Update a question bank")
    public ApiResponse<QuestionBankDto.Response> update(@PathVariable UUID bankId, @Valid @RequestBody QuestionBankDto.UpdateRequest request) {
        return ApiResponse.success("Question bank updated successfully", questionBankService.update(bankId, request));
    }

    @DeleteMapping("/{bankId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SME')")
    @Operation(summary = "Archive a question bank")
    public ApiResponse<Void> archive(@PathVariable UUID bankId) {
        questionBankService.archive(bankId);
        return ApiResponse.success("Question bank archived successfully");
    }
}
