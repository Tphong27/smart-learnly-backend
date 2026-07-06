package com.smartlearnly.backend.question.controller;

import com.smartlearnly.backend.common.api.ApiResponse;
import com.smartlearnly.backend.question.dto.QuestionImageResponse;
import com.smartlearnly.backend.question.service.QuestionImageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/questions")
@PreAuthorize("hasAnyRole('ADMIN', 'SME')")
@Tag(name = "Admin Question Images", description = "Question image attachment APIs.")
@SecurityRequirement(name = "bearerAuth")
public class QuestionImageController {
    private final QuestionImageService questionImageService;

    @PostMapping(value = "/{questionId}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload or replace a question image")
    public ApiResponse<QuestionImageResponse> uploadOrReplace(
            @PathVariable UUID questionId,
            @RequestPart("file") MultipartFile file
    ) {
        return ApiResponse.success("Question image uploaded successfully", questionImageService.uploadOrReplace(questionId, file));
    }

    @DeleteMapping("/{questionId}/image")
    @Operation(summary = "Remove a question image reference")
    public ApiResponse<QuestionImageResponse> remove(@PathVariable UUID questionId) {
        return ApiResponse.success("Question image removed successfully", questionImageService.remove(questionId));
    }
}
