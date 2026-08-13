package com.smartlearnly.backend.flashcard.staging.controller;

import com.smartlearnly.backend.common.api.ApiResponse;
import com.smartlearnly.backend.flashcard.staging.dto.AdminFlashcardStagingDtos.ApproveStagingCardsRequest;
import com.smartlearnly.backend.flashcard.staging.dto.AdminFlashcardStagingDtos.ApproveStagingCardsResponse;
import com.smartlearnly.backend.flashcard.staging.dto.AdminFlashcardStagingDtos.ApproveTemporaryFlashcardsRequest;
import com.smartlearnly.backend.flashcard.staging.dto.AdminFlashcardStagingDtos.ApproveTemporaryFlashcardsResponse;
import com.smartlearnly.backend.flashcard.staging.dto.AdminFlashcardStagingDtos.GenerateFromTranscriptRequest;
import com.smartlearnly.backend.flashcard.staging.dto.AdminFlashcardStagingDtos.GenerateFromTextRequest;
import com.smartlearnly.backend.flashcard.staging.dto.AdminFlashcardStagingDtos.ImportCourseQuestionsRequest;
import com.smartlearnly.backend.flashcard.staging.dto.AdminFlashcardStagingDtos.RejectStagingCardsRequest;
import com.smartlearnly.backend.flashcard.staging.dto.AdminFlashcardStagingDtos.RejectStagingCardsResponse;
import com.smartlearnly.backend.flashcard.staging.dto.AdminFlashcardStagingDtos.SourceQuestionResponse;
import com.smartlearnly.backend.flashcard.staging.dto.AdminFlashcardStagingDtos.StagingBatchResponse;
import com.smartlearnly.backend.flashcard.staging.dto.AdminFlashcardStagingDtos.StagingCardResponse;
import com.smartlearnly.backend.flashcard.staging.dto.AdminFlashcardStagingDtos.TemporaryFlashcardCandidateBatchResponse;
import com.smartlearnly.backend.flashcard.staging.dto.AdminFlashcardStagingDtos.UpdateStagingCardRequest;
import com.smartlearnly.backend.flashcard.staging.service.AdminFlashcardStagingService;
import com.smartlearnly.backend.flashcard.staging.service.FlashcardCourseQuestionImportService;
import com.smartlearnly.backend.flashcard.staging.service.FlashcardStagingGenerationService;
import com.smartlearnly.backend.flashcard.staging.service.FlashcardStagingCardEditService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Validated
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SME', 'TRAINER')")
@RequestMapping("/api/v1/admin")
public class AdminFlashcardStagingController {
    private final AdminFlashcardStagingService adminFlashcardStagingService;
    private final FlashcardCourseQuestionImportService flashcardCourseQuestionImportService;
    private final FlashcardStagingGenerationService flashcardStagingGenerationService;
    private final FlashcardStagingCardEditService flashcardStagingCardEditService;

    /** Liệt kê question đã duyệt có thể dùng làm nguồn flashcard. */
    @GetMapping("/flashcard-sets/{setId}/staging/source-questions")
    public ApiResponse<List<SourceQuestionResponse>> listSourceQuestions(
            @PathVariable UUID setId,
            @RequestParam(required = false) UUID moduleId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Short difficulty,
            @RequestParam(required = false) String status
    ) {
        return ApiResponse.success(
                "Flashcard staging source questions loaded successfully",
                flashcardCourseQuestionImportService.listSourceQuestions(
                        setId,
                        moduleId,
                        keyword,
                        difficulty,
                        "approved"
                )
        );
    }

    /** Nhập question của course vào staging batch để author duyệt. */
    @PostMapping("/flashcard-sets/{setId}/staging/import-course-questions")
    public ResponseEntity<ApiResponse<StagingBatchResponse>> importCourseQuestions(
            @PathVariable UUID setId,
            @Valid @RequestBody ImportCourseQuestionsRequest request
    ) {
        StagingBatchResponse response = flashcardCourseQuestionImportService.importCourseQuestions(setId, request);
        return ResponseEntity.created(URI.create("/api/v1/admin/flashcard-sets/" + setId + "/staging"))
                .body(ApiResponse.success("Flashcard staging batch created successfully", response));
    }

    /** Tạo preview tạm từ question của course mà chưa ghi staging. */
    @PostMapping("/flashcard-sets/{setId}/temporary-review/import-course-questions")
    public ApiResponse<TemporaryFlashcardCandidateBatchResponse> temporaryCourseQuestions(
            @PathVariable UUID setId,
            @Valid @RequestBody ImportCourseQuestionsRequest request
    ) {
        return ApiResponse.success(
                "Temporary flashcard candidates created successfully",
                flashcardCourseQuestionImportService.previewCourseQuestions(setId, request)
        );
    }

    /** Sinh staging flashcard từ văn bản do author cung cấp. */
    @PostMapping("/flashcard-sets/{setId}/staging/generate-from-text")
    public ResponseEntity<ApiResponse<StagingBatchResponse>> generateFromText(
            @PathVariable UUID setId,
            @Valid @RequestBody GenerateFromTextRequest request
    ) {
        StagingBatchResponse response = flashcardStagingGenerationService.generateFromText(setId, request);
        return ResponseEntity.created(URI.create("/api/v1/admin/flashcard-sets/" + setId + "/staging"))
                .body(ApiResponse.success("Flashcard staging batch created successfully", response));
    }

    /** Sinh staging flashcard từ file nguồn và các tùy chọn đếm/ngôn ngữ. */
    @PostMapping(
            value = "/flashcard-sets/{setId}/staging/generate-from-file",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<ApiResponse<StagingBatchResponse>> generateFromFile(
            @PathVariable UUID setId,
            @RequestPart(value = "file", required = false) MultipartFile file,
            @RequestParam(required = false) Integer desiredCount,
            @RequestParam(required = false) String language,
            @RequestParam(required = false) String generationMode
    ) {
        StagingBatchResponse response = flashcardStagingGenerationService.generateFromFile(
                setId,
                file,
                desiredCount,
                language,
                generationMode
        );
        return ResponseEntity.created(URI.create("/api/v1/admin/flashcard-sets/" + setId + "/staging"))
                .body(ApiResponse.success("Flashcard staging batch created successfully", response));
    }

    /** Tạo preview flashcard tạm từ file mà chưa lưu vào staging. */
    @PostMapping(
            value = "/flashcard-sets/{setId}/temporary-review/generate-from-file",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ApiResponse<TemporaryFlashcardCandidateBatchResponse> temporaryFromFile(
            @PathVariable UUID setId,
            @RequestPart(value = "file", required = false) MultipartFile file,
            @RequestParam(required = false) Integer desiredCount,
            @RequestParam(required = false) String language,
            @RequestParam(required = false) String generationMode
    ) {
        return ApiResponse.success(
                "Temporary flashcard candidates created successfully",
                flashcardStagingGenerationService.generateTemporaryFromFile(
                        setId,
                        file,
                        desiredCount,
                        language,
                        generationMode
                )
        );
    }

    /** Sinh staging flashcard từ transcript dạng text. */
    @PostMapping("/flashcard-sets/{setId}/staging/generate-from-transcript")
    public ResponseEntity<ApiResponse<StagingBatchResponse>> generateFromTranscript(
            @PathVariable UUID setId,
            @Valid @RequestBody GenerateFromTranscriptRequest request
    ) {
        StagingBatchResponse response = flashcardStagingGenerationService.generateFromTranscript(setId, request);
        return ResponseEntity.created(URI.create("/api/v1/admin/flashcard-sets/" + setId + "/staging"))
                .body(ApiResponse.success("Flashcard staging batch created successfully", response));
    }

    /** Sinh staging flashcard từ file transcript. */
    @PostMapping(
            value = "/flashcard-sets/{setId}/staging/generate-from-transcript-file",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<ApiResponse<StagingBatchResponse>> generateFromTranscriptFile(
            @PathVariable UUID setId,
            @RequestPart(value = "file", required = false) MultipartFile file,
            @RequestParam(required = false) Integer desiredCount,
            @RequestParam(required = false) String language,
            @RequestParam(required = false) String generationMode
    ) {
        StagingBatchResponse response = flashcardStagingGenerationService.generateFromTranscriptFile(
                setId,
                file,
                desiredCount,
                language,
                generationMode
        );
        return ResponseEntity.created(URI.create("/api/v1/admin/flashcard-sets/" + setId + "/staging"))
                .body(ApiResponse.success("Flashcard staging batch created successfully", response));
    }

    /** Liệt kê các staging batch của flashcard set. */
    @GetMapping("/flashcard-sets/{setId}/staging")
    public ApiResponse<List<StagingBatchResponse>> listStaging(@PathVariable UUID setId) {
        return ApiResponse.success(
                "Flashcard staging batches loaded successfully",
                adminFlashcardStagingService.listStaging(setId)
        );
    }

    /** Cập nhật nội dung một staging card trước khi duyệt. */
    @PatchMapping("/flashcard-staging-cards/{stagingCardId}")
    public ApiResponse<StagingCardResponse> updateCard(
            @PathVariable UUID stagingCardId,
            @Valid @RequestBody UpdateStagingCardRequest request
    ) {
        return ApiResponse.success(
                "Flashcard staging card updated successfully",
                flashcardStagingCardEditService.updateCard(stagingCardId, request)
        );
    }

    /** Từ chối một staging card riêng lẻ. */
    @DeleteMapping("/flashcard-staging-cards/{stagingCardId}")
    public ApiResponse<Void> rejectCard(@PathVariable UUID stagingCardId) {
        adminFlashcardStagingService.rejectCard(stagingCardId);
        return ApiResponse.success("Flashcard staging card rejected successfully");
    }

    /** Từ chối một nhóm staging card theo request. */
    @PostMapping("/flashcard-sets/{setId}/staging/reject")
    public ApiResponse<RejectStagingCardsResponse> reject(
            @PathVariable UUID setId,
            @Valid @RequestBody RejectStagingCardsRequest request
    ) {
        return ApiResponse.success(
                "Flashcard staging cards rejected successfully",
                adminFlashcardStagingService.reject(setId, request)
        );
    }

    /** Duyệt staging card và ghi vào flashcard set chính thức. */
    @PostMapping("/flashcard-sets/{setId}/staging/approve")
    public ApiResponse<ApproveStagingCardsResponse> approve(
            @PathVariable UUID setId,
            @Valid @RequestBody ApproveStagingCardsRequest request
    ) {
        return ApiResponse.success(
                "Flashcard staging cards approved successfully",
                adminFlashcardStagingService.approve(setId, request)
        );
    }

    /** Duyệt trực tiếp candidate tạm vào flashcard set. */
    @PostMapping("/flashcard-sets/{setId}/temporary-review/approve")
    public ApiResponse<ApproveTemporaryFlashcardsResponse> approveTemporary(
            @PathVariable UUID setId,
            @Valid @RequestBody ApproveTemporaryFlashcardsRequest request
    ) {
        return ApiResponse.success(
                "Temporary flashcard candidates approved successfully",
                adminFlashcardStagingService.approveTemporary(setId, request)
        );
    }
}
