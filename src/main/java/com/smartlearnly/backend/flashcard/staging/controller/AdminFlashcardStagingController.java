package com.smartlearnly.backend.flashcard.staging.controller;

import com.smartlearnly.backend.common.api.ApiResponse;
import com.smartlearnly.backend.flashcard.staging.dto.AdminFlashcardStagingDtos.ApproveStagingCardsRequest;
import com.smartlearnly.backend.flashcard.staging.dto.AdminFlashcardStagingDtos.ApproveStagingCardsResponse;
import com.smartlearnly.backend.flashcard.staging.dto.AdminFlashcardStagingDtos.GenerateFromTranscriptRequest;
import com.smartlearnly.backend.flashcard.staging.dto.AdminFlashcardStagingDtos.GenerateFromTextRequest;
import com.smartlearnly.backend.flashcard.staging.dto.AdminFlashcardStagingDtos.ImportQuestionBankRequest;
import com.smartlearnly.backend.flashcard.staging.dto.AdminFlashcardStagingDtos.RejectStagingCardsRequest;
import com.smartlearnly.backend.flashcard.staging.dto.AdminFlashcardStagingDtos.RejectStagingCardsResponse;
import com.smartlearnly.backend.flashcard.staging.dto.AdminFlashcardStagingDtos.SourceQuestionResponse;
import com.smartlearnly.backend.flashcard.staging.dto.AdminFlashcardStagingDtos.StagingBatchResponse;
import com.smartlearnly.backend.flashcard.staging.dto.AdminFlashcardStagingDtos.StagingCardResponse;
import com.smartlearnly.backend.flashcard.staging.dto.AdminFlashcardStagingDtos.UpdateStagingCardRequest;
import com.smartlearnly.backend.flashcard.staging.service.AdminFlashcardStagingService;
import com.smartlearnly.backend.flashcard.staging.service.FlashcardQuestionBankImportService;
import com.smartlearnly.backend.flashcard.staging.service.FlashcardStagingCardEditService;
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
@Tag(name = "Admin Flashcard Staging", description = "Administrator flashcard staging APIs.")
@SecurityRequirement(name = "bearerAuth")
public class AdminFlashcardStagingController {
    private final AdminFlashcardStagingService adminFlashcardStagingService;
    private final FlashcardQuestionBankImportService flashcardQuestionBankImportService;
    private final FlashcardStagingCardEditService flashcardStagingCardEditService;

    @GetMapping("/flashcard-sets/{setId}/staging/source-questions")
    @Operation(summary = "List same-course question bank questions for flashcard staging")
    public ApiResponse<List<SourceQuestionResponse>> listSourceQuestions(
            @PathVariable UUID setId,
            @RequestParam(required = false) UUID questionBankId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Short difficulty,
            @RequestParam(required = false) String status
    ) {
        return ApiResponse.success(
                "Flashcard staging source questions loaded successfully",
                adminFlashcardStagingService.listSourceQuestions(setId, questionBankId, keyword, difficulty, "approved")
        );
    }

    @PostMapping("/flashcard-sets/{setId}/staging/import-question-bank")
    @Operation(summary = "Import question bank questions into flashcard staging")
    public ResponseEntity<ApiResponse<StagingBatchResponse>> importQuestionBank(
            @PathVariable UUID setId,
            @Valid @RequestBody ImportQuestionBankRequest request
    ) {
        StagingBatchResponse response = flashcardQuestionBankImportService.importQuestionBank(setId, request);
        return ResponseEntity.created(URI.create("/api/v1/admin/flashcard-sets/" + setId + "/staging"))
                .body(ApiResponse.success("Flashcard staging batch created successfully", response));
    }

    @PostMapping("/flashcard-sets/{setId}/staging/generate-from-text")
    @Operation(summary = "Generate flashcard staging cards from pasted text")
    public ResponseEntity<ApiResponse<StagingBatchResponse>> generateFromText(
            @PathVariable UUID setId,
            @Valid @RequestBody GenerateFromTextRequest request
    ) {
        StagingBatchResponse response = adminFlashcardStagingService.generateFromText(setId, request);
        return ResponseEntity.created(URI.create("/api/v1/admin/flashcard-sets/" + setId + "/staging"))
                .body(ApiResponse.success("Flashcard staging batch created successfully", response));
    }

    @PostMapping(
            value = "/flashcard-sets/{setId}/staging/generate-from-file",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @Operation(summary = "Generate flashcard staging cards from an uploaded DOCX or PDF")
    public ResponseEntity<ApiResponse<StagingBatchResponse>> generateFromFile(
            @PathVariable UUID setId,
            @RequestPart(value = "file", required = false) MultipartFile file,
            @RequestParam(required = false) Integer desiredCount,
            @RequestParam(required = false) String language,
            @RequestParam(required = false) String difficulty,
            @RequestParam(required = false) String generationMode
    ) {
        StagingBatchResponse response = adminFlashcardStagingService.generateFromFile(
                setId,
                file,
                desiredCount,
                language,
                difficulty,
                generationMode
        );
        return ResponseEntity.created(URI.create("/api/v1/admin/flashcard-sets/" + setId + "/staging"))
                .body(ApiResponse.success("Flashcard staging batch created successfully", response));
    }

    @PostMapping("/flashcard-sets/{setId}/staging/generate-from-transcript")
    @Operation(summary = "Generate flashcard staging cards from pasted video transcript text")
    public ResponseEntity<ApiResponse<StagingBatchResponse>> generateFromTranscript(
            @PathVariable UUID setId,
            @Valid @RequestBody GenerateFromTranscriptRequest request
    ) {
        StagingBatchResponse response = adminFlashcardStagingService.generateFromTranscript(setId, request);
        return ResponseEntity.created(URI.create("/api/v1/admin/flashcard-sets/" + setId + "/staging"))
                .body(ApiResponse.success("Flashcard staging batch created successfully", response));
    }

    @PostMapping(
            value = "/flashcard-sets/{setId}/staging/generate-from-transcript-file",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @Operation(summary = "Generate flashcard staging cards from an uploaded SRT or VTT transcript")
    public ResponseEntity<ApiResponse<StagingBatchResponse>> generateFromTranscriptFile(
            @PathVariable UUID setId,
            @RequestPart(value = "file", required = false) MultipartFile file,
            @RequestParam(required = false) Integer desiredCount,
            @RequestParam(required = false) String language,
            @RequestParam(required = false) String difficulty,
            @RequestParam(required = false) String generationMode
    ) {
        StagingBatchResponse response = adminFlashcardStagingService.generateFromTranscriptFile(
                setId,
                file,
                desiredCount,
                language,
                difficulty,
                generationMode
        );
        return ResponseEntity.created(URI.create("/api/v1/admin/flashcard-sets/" + setId + "/staging"))
                .body(ApiResponse.success("Flashcard staging batch created successfully", response));
    }

    @GetMapping("/flashcard-sets/{setId}/staging")
    @Operation(summary = "List flashcard staging batches")
    public ApiResponse<List<StagingBatchResponse>> listStaging(@PathVariable UUID setId) {
        return ApiResponse.success(
                "Flashcard staging batches loaded successfully",
                adminFlashcardStagingService.listStaging(setId)
        );
    }

    @PatchMapping("/flashcard-staging-cards/{stagingCardId}")
    @Operation(summary = "Update a flashcard staging card")
    public ApiResponse<StagingCardResponse> updateCard(
            @PathVariable UUID stagingCardId,
            @Valid @RequestBody UpdateStagingCardRequest request
    ) {
        return ApiResponse.success(
                "Flashcard staging card updated successfully",
                flashcardStagingCardEditService.updateCard(stagingCardId, request)
        );
    }

    @DeleteMapping("/flashcard-staging-cards/{stagingCardId}")
    @Operation(summary = "Reject a flashcard staging card")
    public ApiResponse<Void> rejectCard(@PathVariable UUID stagingCardId) {
        adminFlashcardStagingService.rejectCard(stagingCardId);
        return ApiResponse.success("Flashcard staging card rejected successfully");
    }

    @PostMapping("/flashcard-sets/{setId}/staging/reject")
    @Operation(summary = "Reject flashcard staging cards")
    public ApiResponse<RejectStagingCardsResponse> reject(
            @PathVariable UUID setId,
            @Valid @RequestBody RejectStagingCardsRequest request
    ) {
        return ApiResponse.success(
                "Flashcard staging cards rejected successfully",
                adminFlashcardStagingService.reject(setId, request)
        );
    }

    @PostMapping("/flashcard-sets/{setId}/staging/approve")
    @Operation(summary = "Approve flashcard staging cards into real flashcards")
    public ApiResponse<ApproveStagingCardsResponse> approve(
            @PathVariable UUID setId,
            @Valid @RequestBody ApproveStagingCardsRequest request
    ) {
        return ApiResponse.success(
                "Flashcard staging cards approved successfully",
                adminFlashcardStagingService.approve(setId, request)
        );
    }
}
