package com.smartlearnly.backend.question.controller;

import com.smartlearnly.backend.common.api.ApiResponse;
import com.smartlearnly.backend.question.dto.QuestionImageImportDtos;
import com.smartlearnly.backend.question.service.QuestionImageImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Validated
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SME')")
@RequestMapping("/api/v1/admin/question-imports/image")
@Tag(name = "Admin Question Image Imports", description = "AI-assisted image import preview and confirm APIs.")
@SecurityRequirement(name = "bearerAuth")
public class QuestionImageImportController {
    private final QuestionImageImportService questionImageImportService;

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Preview questions parsed from uploaded images")
    public ApiResponse<QuestionImageImportDtos.PreviewResponse> preview(
            @RequestParam UUID bankId,
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(defaultValue = "vi") String language
    ) {
        return ApiResponse.success("Image import preview generated successfully", questionImageImportService.preview(bankId, files, language));
    }

    @PostMapping("/confirm")
    @Operation(summary = "Confirm reviewed image-imported questions")
    public ApiResponse<QuestionImageImportDtos.ConfirmResponse> confirm(
            @Valid @RequestBody QuestionImageImportDtos.ConfirmRequest request
    ) {
        return ApiResponse.success("Image-imported questions created successfully", questionImageImportService.confirm(request));
    }
}
