package com.smartlearnly.backend.question.ai.controller;

import com.smartlearnly.backend.common.api.ApiResponse;
import com.smartlearnly.backend.question.ai.dto.AiQuestionDraftDtos;
import com.smartlearnly.backend.question.ai.service.AiQuestionDraftService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Validated
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SME')")
@RequestMapping("/api/v1/admin/question-banks/{bankId}/ai-drafts")
@Tag(name = "Admin AI Question Drafts", description = "AI-generated question draft staging APIs.")
@SecurityRequirement(name = "bearerAuth")
public class AdminAiQuestionDraftController {
    private final AiQuestionDraftService aiQuestionDraftService;

    @GetMapping("/sources")
    @Operation(summary = "List RAG-ready lesson material sources for AI question generation")
    public ApiResponse<List<AiQuestionDraftDtos.SourceOptionResponse>> sources(@PathVariable UUID bankId) {
        return ApiResponse.success("AI generation sources loaded successfully", aiQuestionDraftService.listSources(bankId));
    }

    @GetMapping("/source-capabilities")
    @Operation(summary = "Get supported AI question generation source limits")
    public ApiResponse<AiQuestionDraftDtos.SourceCapabilitiesResponse> sourceCapabilities(@PathVariable UUID bankId) {
        return ApiResponse.success("AI generation source capabilities loaded successfully", aiQuestionDraftService.sourceCapabilities(bankId));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create an AI question generation batch")
    public ResponseEntity<ApiResponse<AiQuestionDraftDtos.BatchResponse>> create(
            @PathVariable UUID bankId,
            @Valid @RequestBody AiQuestionDraftDtos.CreateBatchRequest request
    ) {
        AiQuestionDraftDtos.BatchResponse batch = aiQuestionDraftService.createBatch(bankId, request);
        return ResponseEntity.created(URI.create("/api/v1/admin/question-banks/" + bankId + "/ai-drafts/" + batch.batchId()))
                .body(ApiResponse.success("AI question generation batch created successfully", batch));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Create an AI question generation batch from mixed source types")
    public ResponseEntity<ApiResponse<AiQuestionDraftDtos.BatchResponse>> createMultipart(
            @PathVariable UUID bankId,
            @Valid @RequestPart("request") AiQuestionDraftDtos.CreateBatchRequest request,
            @RequestPart(value = "files", required = false) List<MultipartFile> files
    ) {
        AiQuestionDraftDtos.BatchResponse batch = aiQuestionDraftService.createBatch(bankId, request, files == null ? List.of() : files);
        return ResponseEntity.created(URI.create("/api/v1/admin/question-banks/" + bankId + "/ai-drafts/" + batch.batchId()))
                .body(ApiResponse.success("AI question generation batch created successfully", batch));
    }

    @GetMapping
    @Operation(summary = "List AI question generation batches")
    public ApiResponse<List<AiQuestionDraftDtos.BatchResponse>> list(@PathVariable UUID bankId) {
        return ApiResponse.success("AI question generation batches loaded successfully", aiQuestionDraftService.listBatches(bankId));
    }

    @GetMapping("/{batchId}")
    @Operation(summary = "Get an AI question generation batch")
    public ApiResponse<AiQuestionDraftDtos.BatchResponse> get(@PathVariable UUID bankId, @PathVariable UUID batchId) {
        return ApiResponse.success("AI question generation batch loaded successfully", aiQuestionDraftService.getBatch(bankId, batchId));
    }

    @PostMapping("/{batchId}/sources/{sourceId}/download-url")
    @Operation(summary = "Create a short-lived audit download URL for an AI generation source")
    public ApiResponse<AiQuestionDraftDtos.SourceDownloadUrlResponse> sourceDownloadUrl(
            @PathVariable UUID bankId,
            @PathVariable UUID batchId,
            @PathVariable UUID sourceId
    ) {
        return ApiResponse.success("AI generation source download URL created successfully", aiQuestionDraftService.sourceDownloadUrl(bankId, batchId, sourceId));
    }

    @GetMapping("/{batchId}/items")
    @Operation(summary = "List draft items in an AI question generation batch")
    public ApiResponse<List<AiQuestionDraftDtos.DraftResponse>> items(@PathVariable UUID bankId, @PathVariable UUID batchId) {
        return ApiResponse.success("AI question drafts loaded successfully", aiQuestionDraftService.listDrafts(bankId, batchId));
    }

    @PutMapping("/{batchId}/drafts/{draftId}")
    @Operation(summary = "Edit an AI question draft")
    public ApiResponse<AiQuestionDraftDtos.DraftResponse> updateDraft(
            @PathVariable UUID bankId,
            @PathVariable UUID batchId,
            @PathVariable UUID draftId,
            @Valid @RequestBody AiQuestionDraftDtos.UpdateDraftRequest request
    ) {
        return ApiResponse.success("AI question draft updated successfully", aiQuestionDraftService.updateDraft(bankId, batchId, draftId, request));
    }

    @PostMapping("/{batchId}/drafts/{draftId}/reject")
    @Operation(summary = "Reject an AI question draft")
    public ApiResponse<AiQuestionDraftDtos.DraftResponse> rejectDraft(
            @PathVariable UUID bankId,
            @PathVariable UUID batchId,
            @PathVariable UUID draftId,
            @Valid @RequestBody AiQuestionDraftDtos.RejectDraftRequest request
    ) {
        return ApiResponse.success("AI question draft rejected successfully", aiQuestionDraftService.rejectDraft(bankId, batchId, draftId, request));
    }

    @PostMapping("/{batchId}/drafts/{draftId}/evidence-confirmation")
    @Operation(summary = "Confirm whether existing evidence still supports an edited AI question draft")
    public ApiResponse<AiQuestionDraftDtos.DraftResponse> confirmEvidence(
            @PathVariable UUID bankId,
            @PathVariable UUID batchId,
            @PathVariable UUID draftId,
            @Valid @RequestBody AiQuestionDraftDtos.EvidenceConfirmationRequest request
    ) {
        return ApiResponse.success("AI question draft evidence updated successfully", aiQuestionDraftService.confirmEvidence(bankId, batchId, draftId, request));
    }

    @PostMapping("/{batchId}/add-selected")
    @Operation(summary = "Add selected AI drafts to the question bank as draft questions")
    public ApiResponse<AiQuestionDraftDtos.AddSelectedResponse> addSelected(
            @PathVariable UUID bankId,
            @PathVariable UUID batchId,
            @Valid @RequestBody AiQuestionDraftDtos.AddSelectedRequest request
    ) {
        return ApiResponse.success("Selected AI question drafts processed successfully", aiQuestionDraftService.addSelected(bankId, batchId, request));
    }

    @PostMapping("/{batchId}/retry")
    @Operation(summary = "Retry a failed AI question generation batch")
    public ApiResponse<AiQuestionDraftDtos.BatchResponse> retry(@PathVariable UUID bankId, @PathVariable UUID batchId) {
        return ApiResponse.success("AI question generation batch retry completed", aiQuestionDraftService.retry(bankId, batchId));
    }
}
