package com.smartlearnly.backend.flashcard.personal.controller;

import com.smartlearnly.backend.common.api.ApiResponse;
import com.smartlearnly.backend.common.api.PageResponse;
import com.smartlearnly.backend.flashcard.dto.FlashcardImageUploadResponse;
import com.smartlearnly.backend.flashcard.personal.dto.PersonalFlashcardDtos.BulkDeletePersonalFlashcardCardsRequest;
import com.smartlearnly.backend.flashcard.personal.dto.PersonalFlashcardDtos.BulkCreatePersonalFlashcardCardsRequest;
import com.smartlearnly.backend.flashcard.personal.dto.PersonalFlashcardDtos.CreatePersonalFlashcardCardRequest;
import com.smartlearnly.backend.flashcard.personal.dto.PersonalFlashcardDtos.CreatePersonalFlashcardSetRequest;
import com.smartlearnly.backend.flashcard.personal.dto.PersonalFlashcardDtos.GeneratePersonalFlashcardsFromTextRequest;
import com.smartlearnly.backend.flashcard.personal.dto.PersonalFlashcardDtos.PersonalBulkDeleteResponse;
import com.smartlearnly.backend.flashcard.personal.dto.PersonalFlashcardDtos.PersonalFlashcardCardResponse;
import com.smartlearnly.backend.flashcard.personal.dto.PersonalFlashcardDtos.PersonalFlashcardSetDetailResponse;
import com.smartlearnly.backend.flashcard.personal.dto.PersonalFlashcardDtos.PersonalFlashcardSetSummaryResponse;
import com.smartlearnly.backend.flashcard.personal.dto.PersonalFlashcardDtos.PersonalGeneratedFlashcardsResponse;
import com.smartlearnly.backend.flashcard.personal.dto.PersonalFlashcardDtos.PersonalFlashcardStudyResponse;
import com.smartlearnly.backend.flashcard.personal.dto.PersonalFlashcardDtos.ReorderPersonalFlashcardCardsRequest;
import com.smartlearnly.backend.flashcard.personal.dto.PersonalFlashcardDtos.ReplacePersonalFlashcardCardRequest;
import com.smartlearnly.backend.flashcard.personal.dto.PersonalFlashcardDtos.ReplacePersonalFlashcardSetRequest;
import com.smartlearnly.backend.flashcard.personal.service.PersonalFlashcardImageUploadService;
import com.smartlearnly.backend.flashcard.personal.service.PersonalFlashcardImportService;
import com.smartlearnly.backend.flashcard.personal.service.PersonalFlashcardService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Validated
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('TMO', 'TRAINEE', 'TRAINER', 'SME')")
@RequestMapping("/api/v1/my-flashcards/sets")
public class PersonalFlashcardController {
    private final PersonalFlashcardService personalFlashcardService;
    private final PersonalFlashcardImageUploadService personalFlashcardImageUploadService;
    private final PersonalFlashcardImportService personalFlashcardImportService;

    @GetMapping
    public ApiResponse<PageResponse<PersonalFlashcardSetSummaryResponse>> listSets(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "updated_desc") String sort,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ApiResponse.success(
                "Personal flashcard sets loaded successfully",
                personalFlashcardService.listSets(q, sort, page, size)
        );
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PersonalFlashcardSetDetailResponse>> createSet(
            @Valid @RequestBody CreatePersonalFlashcardSetRequest request
    ) {
        PersonalFlashcardSetDetailResponse response = personalFlashcardService.createSet(request);
        return ResponseEntity.created(URI.create("/api/v1/my-flashcards/sets/" + response.id()))
                .body(ApiResponse.success("Personal flashcard set created successfully", response));
    }

    @GetMapping("/{setId}")
    public ApiResponse<PersonalFlashcardSetDetailResponse> getSet(@PathVariable UUID setId) {
        return ApiResponse.success(
                "Personal flashcard set loaded successfully",
                personalFlashcardService.getSet(setId)
        );
    }

    @PutMapping("/{setId}")
    public ApiResponse<PersonalFlashcardSetDetailResponse> replaceSet(
            @PathVariable UUID setId,
            @Valid @RequestBody ReplacePersonalFlashcardSetRequest request
    ) {
        return ApiResponse.success(
                "Personal flashcard set updated successfully",
                personalFlashcardService.replaceSet(setId, request)
        );
    }

    @DeleteMapping("/{setId}")
    public ApiResponse<Void> deleteSet(@PathVariable UUID setId) {
        personalFlashcardService.deleteSet(setId);
        return ApiResponse.success("Personal flashcard set deleted successfully");
    }

    @PostMapping("/{setId}/cards")
    public ResponseEntity<ApiResponse<PersonalFlashcardCardResponse>> addCard(
            @PathVariable UUID setId,
            @Valid @RequestBody CreatePersonalFlashcardCardRequest request
    ) {
        PersonalFlashcardCardResponse response = personalFlashcardService.addCard(setId, request);
        return ResponseEntity.created(URI.create(
                        "/api/v1/my-flashcards/sets/" + setId + "/cards/" + response.id()))
                .body(ApiResponse.success("Personal flashcard card created successfully", response));
    }

    @PutMapping("/{setId}/cards/{cardId}")
    public ApiResponse<PersonalFlashcardCardResponse> replaceCard(
            @PathVariable UUID setId,
            @PathVariable UUID cardId,
            @Valid @RequestBody ReplacePersonalFlashcardCardRequest request
    ) {
        return ApiResponse.success(
                "Personal flashcard card updated successfully",
                personalFlashcardService.replaceCard(setId, cardId, request)
        );
    }

    @DeleteMapping("/{setId}/cards/{cardId}")
    public ApiResponse<Void> deleteCard(@PathVariable UUID setId, @PathVariable UUID cardId) {
        personalFlashcardService.deleteCard(setId, cardId);
        return ApiResponse.success("Personal flashcard card deleted successfully");
    }

    @PostMapping("/{setId}/cards/bulk-delete")
    public ApiResponse<PersonalBulkDeleteResponse> bulkDeleteCards(
            @PathVariable UUID setId,
            @Valid @RequestBody BulkDeletePersonalFlashcardCardsRequest request
    ) {
        return ApiResponse.success(
                "Personal flashcard cards deleted successfully",
                personalFlashcardService.bulkDeleteCards(setId, request)
        );
    }

    @PostMapping("/{setId}/cards/bulk-create")
    public ApiResponse<PersonalFlashcardSetDetailResponse> bulkCreateCards(
            @PathVariable UUID setId,
            @Valid @RequestBody BulkCreatePersonalFlashcardCardsRequest request
    ) {
        return ApiResponse.success(
                "Personal flashcard cards created successfully",
                personalFlashcardImportService.bulkCreateCards(setId, request)
        );
    }

    @PostMapping("/{setId}/imports/generate-from-text")
    public ApiResponse<PersonalGeneratedFlashcardsResponse> generateFromText(
            @PathVariable UUID setId,
            @Valid @RequestBody GeneratePersonalFlashcardsFromTextRequest request
    ) {
        return ApiResponse.success(
                "Personal flashcard candidates generated successfully",
                personalFlashcardImportService.generateFromText(setId, request)
        );
    }

    @PostMapping(value = "/{setId}/imports/generate-from-file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<PersonalGeneratedFlashcardsResponse> generateFromFile(
            @PathVariable UUID setId,
            @RequestPart(value = "file", required = false) MultipartFile file,
            @RequestParam(defaultValue = "10") @Min(1) @Max(30) Integer desiredCount,
            @RequestParam(defaultValue = "auto") String language
    ) {
        return ApiResponse.success(
                "Personal flashcard candidates generated successfully",
                personalFlashcardImportService.generateFromFile(setId, file, desiredCount, language)
        );
    }

    @PutMapping("/{setId}/cards/reorder")
    public ApiResponse<PersonalFlashcardSetDetailResponse> reorderCards(
            @PathVariable UUID setId,
            @Valid @RequestBody ReorderPersonalFlashcardCardsRequest request
    ) {
        return ApiResponse.success(
                "Personal flashcard cards reordered successfully",
                personalFlashcardService.reorderCards(setId, request)
        );
    }

    @PostMapping(value = "/{setId}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<FlashcardImageUploadResponse> uploadImage(
            @PathVariable UUID setId,
            @RequestPart(value = "file", required = false) MultipartFile file
    ) {
        return ApiResponse.success(
                "Personal flashcard image uploaded successfully",
                personalFlashcardImageUploadService.upload(setId, file)
        );
    }

    @GetMapping("/{setId}/study")
    public ApiResponse<PersonalFlashcardStudyResponse> getStudy(@PathVariable UUID setId) {
        return ApiResponse.success(
                "Personal flashcard study set loaded successfully",
                personalFlashcardService.getStudy(setId)
        );
    }
}
