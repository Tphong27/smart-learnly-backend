package com.smartlearnly.backend.question.ai.controller;

import com.smartlearnly.backend.common.api.ApiResponse;
import com.smartlearnly.backend.question.ai.dto.AiQuestionDraftDtos;
import com.smartlearnly.backend.question.ai.service.AiQuestionDraftService;
import com.smartlearnly.backend.question.ai.service.AiQuestionSourceService;
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
public class CourseAiQuestionDraftController {
    private final AiQuestionDraftService aiQuestionDraftService;
    private final AiQuestionSourceService aiQuestionSourceService;

    @GetMapping("/source-capabilities")
    public ApiResponse<AiQuestionDraftDtos.SourceCapabilitiesResponse> sourceCapabilities(@PathVariable UUID courseId) {
        return ApiResponse.success("AI generation source capabilities loaded successfully", aiQuestionSourceService.sourceCapabilities(courseId));
    }

    @GetMapping("/sources")
    public ApiResponse<List<AiQuestionDraftDtos.SourceOptionResponse>> sources(@PathVariable UUID courseId) {
        return ApiResponse.success("AI generation sources loaded successfully", aiQuestionSourceService.listSources(courseId));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<AiQuestionDraftDtos.BatchResponse>> create(
            @PathVariable UUID courseId,
            @Valid @RequestBody AiQuestionDraftDtos.CreateBatchRequest request
    ) {
        AiQuestionDraftDtos.BatchResponse batch = aiQuestionDraftService.createBatch(courseId, request);
        return ResponseEntity.created(URI.create("/api/v1/admin/courses/" + courseId + "/questions/ai-drafts/" + batch.batchId()))
                .body(ApiResponse.success("AI question generation batch created successfully", batch));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<AiQuestionDraftDtos.BatchResponse>> createMultipart(
            @PathVariable UUID courseId,
            @Valid @RequestPart("request") AiQuestionDraftDtos.CreateBatchRequest request,
            @RequestPart(value = "files", required = false) List<MultipartFile> files
    ) {
        AiQuestionDraftDtos.BatchResponse batch = aiQuestionDraftService.createBatch(courseId, request, files == null ? List.of() : files);
        return ResponseEntity.created(URI.create("/api/v1/admin/courses/" + courseId + "/questions/ai-drafts/" + batch.batchId()))
                .body(ApiResponse.success("AI question generation batch created successfully", batch));
    }

    @GetMapping
    public ApiResponse<List<AiQuestionDraftDtos.BatchResponse>> list(@PathVariable UUID courseId) {
        return ApiResponse.success("AI question generation batches loaded successfully", aiQuestionDraftService.listBatches(courseId));
    }

    @GetMapping("/{batchId}")
    public ApiResponse<AiQuestionDraftDtos.BatchResponse> get(@PathVariable UUID courseId, @PathVariable UUID batchId) {
        return ApiResponse.success("AI question generation batch loaded successfully", aiQuestionDraftService.getBatch(courseId, batchId));
    }

    @PostMapping("/{batchId}/sources/{sourceId}/download-url")
    public ApiResponse<AiQuestionDraftDtos.SourceDownloadUrlResponse> sourceDownloadUrl(
            @PathVariable UUID courseId,
            @PathVariable UUID batchId,
            @PathVariable UUID sourceId
    ) {
        return ApiResponse.success("AI generation source download URL created successfully", aiQuestionSourceService.sourceDownloadUrl(courseId, batchId, sourceId));
    }

    @GetMapping("/{batchId}/items")
    public ApiResponse<List<AiQuestionDraftDtos.DraftResponse>> items(@PathVariable UUID courseId, @PathVariable UUID batchId) {
        return ApiResponse.success("AI question drafts loaded successfully", aiQuestionDraftService.listDrafts(courseId, batchId));
    }

    @PutMapping("/{batchId}/drafts/{draftId}")
    public ApiResponse<AiQuestionDraftDtos.DraftResponse> updateDraft(
            @PathVariable UUID courseId,
            @PathVariable UUID batchId,
            @PathVariable UUID draftId,
            @Valid @RequestBody AiQuestionDraftDtos.UpdateDraftRequest request
    ) {
        return ApiResponse.success("AI question draft updated successfully", aiQuestionDraftService.updateDraft(courseId, batchId, draftId, request));
    }

    @PostMapping("/{batchId}/drafts/{draftId}/reject")
    public ApiResponse<AiQuestionDraftDtos.DraftResponse> rejectDraft(
            @PathVariable UUID courseId,
            @PathVariable UUID batchId,
            @PathVariable UUID draftId,
            @Valid @RequestBody AiQuestionDraftDtos.RejectDraftRequest request
    ) {
        return ApiResponse.success("AI question draft rejected successfully", aiQuestionDraftService.rejectDraft(courseId, batchId, draftId, request));
    }

    @PostMapping("/{batchId}/drafts/{draftId}/evidence-confirmation")
    public ApiResponse<AiQuestionDraftDtos.DraftResponse> confirmEvidence(
            @PathVariable UUID courseId,
            @PathVariable UUID batchId,
            @PathVariable UUID draftId,
            @Valid @RequestBody AiQuestionDraftDtos.EvidenceConfirmationRequest request
    ) {
        return ApiResponse.success("AI question draft evidence updated successfully", aiQuestionDraftService.confirmEvidence(courseId, batchId, draftId, request));
    }

    @PostMapping("/{batchId}/add-selected")
    public ApiResponse<AiQuestionDraftDtos.AddSelectedResponse> addSelected(
            @PathVariable UUID courseId,
            @PathVariable UUID batchId,
            @Valid @RequestBody AiQuestionDraftDtos.AddSelectedRequest request
    ) {
        return ApiResponse.success("Selected AI question drafts processed successfully", aiQuestionDraftService.addSelected(courseId, batchId, request));
    }

    @PostMapping("/{batchId}/retry")
    public ApiResponse<AiQuestionDraftDtos.BatchResponse> retry(@PathVariable UUID courseId, @PathVariable UUID batchId) {
        return ApiResponse.success("AI question generation batch retry completed", aiQuestionDraftService.retry(courseId, batchId));
    }

}
