package com.smartlearnly.backend.question.controller;

import com.smartlearnly.backend.common.api.ApiResponse;
import com.smartlearnly.backend.question.dto.QuestionMediaAttachmentResponse;
import com.smartlearnly.backend.question.dto.QuestionMediaDtos;
import com.smartlearnly.backend.question.service.QuestionMediaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/questions")
@PreAuthorize("hasAnyRole('ADMIN', 'SME')")
@Tag(name = "Admin Question Media", description = "Question multi media attachment APIs.")
@SecurityRequirement(name = "bearerAuth")
public class QuestionMediaController {
    private final QuestionMediaService questionMediaService;

    @GetMapping("/{questionId}/media")
    @Operation(summary = "List question media attachments")
    public ApiResponse<List<QuestionMediaAttachmentResponse>> list(@PathVariable UUID questionId) {
        return ApiResponse.success("Question media loaded successfully", questionMediaService.list(questionId));
    }

    @PostMapping(value = "/{questionId}/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload one or more question media attachments")
    public ApiResponse<QuestionMediaDtos.UploadResponse> upload(
            @PathVariable UUID questionId,
            @RequestParam String mediaType,
            @RequestPart("files") List<MultipartFile> files
    ) {
        return ApiResponse.success("Question media uploaded successfully", questionMediaService.upload(questionId, mediaType, files));
    }

    @PutMapping("/{questionId}/media/reorder")
    @Operation(summary = "Reorder question media attachments by media type")
    public ApiResponse<List<QuestionMediaAttachmentResponse>> reorder(
            @PathVariable UUID questionId,
            @Valid @RequestBody QuestionMediaDtos.ReorderRequest request
    ) {
        return ApiResponse.success("Question media reordered successfully", questionMediaService.reorder(questionId, request));
    }

    @DeleteMapping("/{questionId}/media/{attachmentId}")
    @Operation(summary = "Delete one question media attachment")
    public ApiResponse<Void> delete(@PathVariable UUID questionId, @PathVariable UUID attachmentId) {
        questionMediaService.delete(questionId, attachmentId);
        return ApiResponse.success("Question media removed successfully");
    }
}
