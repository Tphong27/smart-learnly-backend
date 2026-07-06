package com.smartlearnly.backend.question.controller;

import com.smartlearnly.backend.common.api.ApiResponse;
import com.smartlearnly.backend.question.dto.QuestionAudioResponse;
import com.smartlearnly.backend.question.service.QuestionAudioService;
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
@Tag(name = "Admin Question Audio", description = "Question audio attachment APIs.")
@SecurityRequirement(name = "bearerAuth")
public class QuestionAudioController {
    private final QuestionAudioService questionAudioService;

    @PostMapping(value = "/{questionId}/audio", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload or replace a question audio")
    public ApiResponse<QuestionAudioResponse> uploadOrReplace(
            @PathVariable UUID questionId,
            @RequestPart("file") MultipartFile file
    ) {
        return ApiResponse.success("Question audio uploaded successfully", questionAudioService.uploadOrReplace(questionId, file));
    }

    @DeleteMapping("/{questionId}/audio")
    @Operation(summary = "Remove a question audio reference")
    public ApiResponse<QuestionAudioResponse> remove(@PathVariable UUID questionId) {
        return ApiResponse.success("Question audio removed successfully", questionAudioService.remove(questionId));
    }
}
