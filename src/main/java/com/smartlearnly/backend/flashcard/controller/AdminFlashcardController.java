package com.smartlearnly.backend.flashcard.controller;

import com.smartlearnly.backend.common.api.ApiResponse;
import com.smartlearnly.backend.flashcard.dto.AdminFlashcardDtos.CreateFlashcardCardRequest;
import com.smartlearnly.backend.flashcard.dto.AdminFlashcardDtos.CreateFlashcardLessonRequest;
import com.smartlearnly.backend.flashcard.dto.AdminFlashcardDtos.FlashcardCardResponse;
import com.smartlearnly.backend.flashcard.dto.AdminFlashcardDtos.FlashcardLessonCreatedResponse;
import com.smartlearnly.backend.flashcard.dto.AdminFlashcardDtos.FlashcardSetResponse;
import com.smartlearnly.backend.flashcard.dto.AdminFlashcardDtos.ReorderFlashcardCardsRequest;
import com.smartlearnly.backend.flashcard.dto.AdminFlashcardDtos.UpdateFlashcardCardRequest;
import com.smartlearnly.backend.flashcard.dto.AdminFlashcardDtos.UpdateFlashcardSetRequest;
import com.smartlearnly.backend.flashcard.service.AdminFlashcardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
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
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'TMO', 'SME', 'TRAINER')")
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin Flashcards", description = "Staff flashcard lesson authoring APIs.")
@SecurityRequirement(name = "bearerAuth")
public class AdminFlashcardController {
    private final AdminFlashcardService adminFlashcardService;

    @PostMapping("/courses/{courseId}/sections/{sectionId}/flashcard-lessons")
    @Operation(summary = "Create a flashcard lesson")
    public ResponseEntity<ApiResponse<FlashcardLessonCreatedResponse>> createFlashcardLesson(
            @PathVariable UUID courseId,
            @PathVariable UUID sectionId,
            @Valid @RequestBody CreateFlashcardLessonRequest request) {
        FlashcardLessonCreatedResponse response = adminFlashcardService.createFlashcardLesson(courseId, sectionId, request);
        return ResponseEntity.created(URI.create("/api/v1/admin/flashcard-sets/" + response.setId()))
                .body(ApiResponse.success("Flashcard lesson created successfully", response));
    }

    @GetMapping("/flashcard-sets/{setId}")
    @Operation(summary = "Get a flashcard set")
    public ApiResponse<FlashcardSetResponse> getSet(@PathVariable UUID setId) {
        return ApiResponse.success("Flashcard set loaded successfully", adminFlashcardService.getSet(setId));
    }

    @GetMapping("/lessons/{lessonId}/flashcards")
    @Operation(summary = "Get a flashcard set by lesson")
    public ApiResponse<FlashcardSetResponse> getSetByLesson(@PathVariable UUID lessonId) {
        return ApiResponse.success("Flashcard set loaded successfully", adminFlashcardService.getSetByLesson(lessonId));
    }

    @PatchMapping("/flashcard-sets/{setId}")
    @Operation(summary = "Update a flashcard set")
    public ApiResponse<FlashcardSetResponse> updateSet(
            @PathVariable UUID setId,
            @Valid @RequestBody UpdateFlashcardSetRequest request) {
        return ApiResponse.success("Flashcard set updated successfully", adminFlashcardService.updateSet(setId, request));
    }

    @DeleteMapping("/flashcard-sets/{setId}")
    @Operation(summary = "Delete a flashcard set")
    public ApiResponse<Void> deleteSet(@PathVariable UUID setId) {
        adminFlashcardService.deleteSet(setId);
        return ApiResponse.success("Flashcard set deleted successfully");
    }

    @PostMapping("/flashcard-sets/{setId}/cards")
    @Operation(summary = "Add a flashcard card")
    public ResponseEntity<ApiResponse<FlashcardCardResponse>> addCard(
            @PathVariable UUID setId,
            @Valid @RequestBody CreateFlashcardCardRequest request) {
        FlashcardCardResponse response = adminFlashcardService.addCard(setId, request);
        return ResponseEntity.created(URI.create("/api/v1/admin/flashcard-cards/" + response.id()))
                .body(ApiResponse.success("Flashcard card created successfully", response));
    }

    @PatchMapping("/flashcard-cards/{cardId}")
    @Operation(summary = "Update a flashcard card")
    public ApiResponse<FlashcardCardResponse> updateCard(
            @PathVariable UUID cardId,
            @Valid @RequestBody UpdateFlashcardCardRequest request) {
        return ApiResponse.success("Flashcard card updated successfully", adminFlashcardService.updateCard(cardId, request));
    }

    @DeleteMapping("/flashcard-cards/{cardId}")
    @Operation(summary = "Delete a flashcard card")
    public ApiResponse<Void> deleteCard(@PathVariable UUID cardId) {
        adminFlashcardService.deleteCard(cardId);
        return ApiResponse.success("Flashcard card deleted successfully");
    }

    @PatchMapping("/flashcard-sets/{setId}/cards/reorder")
    @Operation(summary = "Reorder flashcard cards")
    public ApiResponse<FlashcardSetResponse> reorderCards(
            @PathVariable UUID setId,
            @Valid @RequestBody ReorderFlashcardCardsRequest request) {
        return ApiResponse.success("Flashcard cards reordered successfully", adminFlashcardService.reorderCards(setId, request));
    }
}