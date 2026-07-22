package com.smartlearnly.backend.question.ai.controller;

import com.smartlearnly.backend.common.api.ApiResponse;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.question.ai.dto.AiQuestionDraftDtos;
import com.smartlearnly.backend.question.ai.service.AiQuestionDraftService;
import com.smartlearnly.backend.question.entity.QuestionBank;
import com.smartlearnly.backend.question.repository.QuestionBankRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
@RequestMapping("/api/v1/admin/courses/{courseId}/questions/ai-drafts")
@Tag(name = "Admin Course AI Question Drafts", description = "Course and module scoped AI question draft staging APIs.")
@SecurityRequirement(name = "bearerAuth")
public class CourseAiQuestionDraftController {
    private final AiQuestionDraftService aiQuestionDraftService;
    private final QuestionBankRepository questionBankRepository;

    @GetMapping("/source-capabilities")
    @Operation(summary = "Get supported AI question generation source limits")
    public ApiResponse<AiQuestionDraftDtos.SourceCapabilitiesResponse> sourceCapabilities(@PathVariable UUID courseId) {
        return ApiResponse.success("AI generation source capabilities loaded successfully", aiQuestionDraftService.sourceCapabilities(resolveCompatibilityBankId(courseId)));
    }

    @GetMapping("/sources")
    @Operation(summary = "List transcript sources for AI question generation")
    public ApiResponse<List<AiQuestionDraftDtos.SourceOptionResponse>> sources(@PathVariable UUID courseId) {
        return ApiResponse.success("AI generation sources loaded successfully", aiQuestionDraftService.listSources(resolveCompatibilityBankId(courseId)));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create an AI question generation batch")
    public ResponseEntity<ApiResponse<AiQuestionDraftDtos.BatchResponse>> create(
            @PathVariable UUID courseId,
            @Valid @RequestBody AiQuestionDraftDtos.CreateBatchRequest request
    ) {
        UUID bankId = resolveCompatibilityBankId(courseId);
        AiQuestionDraftDtos.BatchResponse batch = aiQuestionDraftService.createBatch(bankId, request);
        return ResponseEntity.created(URI.create("/api/v1/admin/courses/" + courseId + "/questions/ai-drafts/" + batch.batchId()))
                .body(ApiResponse.success("AI question generation batch created successfully", batch));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Create an AI question generation batch from mixed source types")
    public ResponseEntity<ApiResponse<AiQuestionDraftDtos.BatchResponse>> createMultipart(
            @PathVariable UUID courseId,
            @Valid @RequestPart("request") AiQuestionDraftDtos.CreateBatchRequest request,
            @RequestPart(value = "files", required = false) List<MultipartFile> files
    ) {
        UUID bankId = resolveCompatibilityBankId(courseId);
        AiQuestionDraftDtos.BatchResponse batch = aiQuestionDraftService.createBatch(bankId, request, files == null ? List.of() : files);
        return ResponseEntity.created(URI.create("/api/v1/admin/courses/" + courseId + "/questions/ai-drafts/" + batch.batchId()))
                .body(ApiResponse.success("AI question generation batch created successfully", batch));
    }

    @GetMapping
    @Operation(summary = "List AI question generation batches")
    public ApiResponse<List<AiQuestionDraftDtos.BatchResponse>> list(@PathVariable UUID courseId) {
        return ApiResponse.success("AI question generation batches loaded successfully", aiQuestionDraftService.listBatches(resolveCompatibilityBankId(courseId)));
    }

    @GetMapping("/{batchId}")
    @Operation(summary = "Get an AI question generation batch")
    public ApiResponse<AiQuestionDraftDtos.BatchResponse> get(@PathVariable UUID courseId, @PathVariable UUID batchId) {
        return ApiResponse.success("AI question generation batch loaded successfully", aiQuestionDraftService.getBatch(resolveCompatibilityBankId(courseId), batchId));
    }

    @PostMapping("/{batchId}/sources/{sourceId}/download-url")
    @Operation(summary = "Create a short-lived audit download URL for an AI generation source")
    public ApiResponse<AiQuestionDraftDtos.SourceDownloadUrlResponse> sourceDownloadUrl(
            @PathVariable UUID courseId,
            @PathVariable UUID batchId,
            @PathVariable UUID sourceId
    ) {
        return ApiResponse.success("AI generation source download URL created successfully", aiQuestionDraftService.sourceDownloadUrl(resolveCompatibilityBankId(courseId), batchId, sourceId));
    }

    @GetMapping("/{batchId}/items")
    @Operation(summary = "List draft items in an AI question generation batch")
    public ApiResponse<List<AiQuestionDraftDtos.DraftResponse>> items(@PathVariable UUID courseId, @PathVariable UUID batchId) {
        return ApiResponse.success("AI question drafts loaded successfully", aiQuestionDraftService.listDrafts(resolveCompatibilityBankId(courseId), batchId));
    }

    @PutMapping("/{batchId}/drafts/{draftId}")
    @Operation(summary = "Edit an AI question draft")
    public ApiResponse<AiQuestionDraftDtos.DraftResponse> updateDraft(
            @PathVariable UUID courseId,
            @PathVariable UUID batchId,
            @PathVariable UUID draftId,
            @Valid @RequestBody AiQuestionDraftDtos.UpdateDraftRequest request
    ) {
        return ApiResponse.success("AI question draft updated successfully", aiQuestionDraftService.updateDraft(resolveCompatibilityBankId(courseId), batchId, draftId, request));
    }

    @PostMapping("/{batchId}/drafts/{draftId}/reject")
    @Operation(summary = "Reject an AI question draft")
    public ApiResponse<AiQuestionDraftDtos.DraftResponse> rejectDraft(
            @PathVariable UUID courseId,
            @PathVariable UUID batchId,
            @PathVariable UUID draftId,
            @Valid @RequestBody AiQuestionDraftDtos.RejectDraftRequest request
    ) {
        return ApiResponse.success("AI question draft rejected successfully", aiQuestionDraftService.rejectDraft(resolveCompatibilityBankId(courseId), batchId, draftId, request));
    }

    @PostMapping("/{batchId}/drafts/{draftId}/evidence-confirmation")
    @Operation(summary = "Confirm whether existing evidence still supports an edited AI question draft")
    public ApiResponse<AiQuestionDraftDtos.DraftResponse> confirmEvidence(
            @PathVariable UUID courseId,
            @PathVariable UUID batchId,
            @PathVariable UUID draftId,
            @Valid @RequestBody AiQuestionDraftDtos.EvidenceConfirmationRequest request
    ) {
        return ApiResponse.success("AI question draft evidence updated successfully", aiQuestionDraftService.confirmEvidence(resolveCompatibilityBankId(courseId), batchId, draftId, request));
    }

    @PostMapping("/{batchId}/add-selected")
    @Operation(summary = "Add selected AI drafts as draft course questions")
    public ApiResponse<AiQuestionDraftDtos.AddSelectedResponse> addSelected(
            @PathVariable UUID courseId,
            @PathVariable UUID batchId,
            @Valid @RequestBody AiQuestionDraftDtos.AddSelectedRequest request
    ) {
        return ApiResponse.success("Selected AI question drafts processed successfully", aiQuestionDraftService.addSelected(resolveCompatibilityBankId(courseId), batchId, request));
    }

    @PostMapping("/{batchId}/retry")
    @Operation(summary = "Retry a failed AI question generation batch")
    public ApiResponse<AiQuestionDraftDtos.BatchResponse> retry(@PathVariable UUID courseId, @PathVariable UUID batchId) {
        return ApiResponse.success("AI question generation batch retry completed", aiQuestionDraftService.retry(resolveCompatibilityBankId(courseId), batchId));
    }

    private UUID resolveCompatibilityBankId(UUID courseId) {
        return questionBankRepository
                .findByCourseIdAndStatusOrderByUpdatedAtDesc(courseId, "approved")
                .stream()
                .findFirst()
                .or(() -> questionBankRepository.findByCourseIdOrderByUpdatedAtDesc(courseId).stream().findFirst())
                .map(QuestionBank::getId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Course AI question compatibility storage was not found"));
    }
}
