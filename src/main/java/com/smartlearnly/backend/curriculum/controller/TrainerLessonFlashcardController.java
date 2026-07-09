package com.smartlearnly.backend.curriculum.controller;

import com.smartlearnly.backend.common.api.ApiResponse;
import com.smartlearnly.backend.curriculum.service.TrainerLessonFlashcardService;
import com.smartlearnly.backend.flashcard.dto.AdminFlashcardDtos.CreateFlashcardCardRequest;
import com.smartlearnly.backend.flashcard.dto.AdminFlashcardDtos.CreateFlashcardLessonRequest;
import com.smartlearnly.backend.flashcard.dto.AdminFlashcardDtos.FlashcardCardResponse;
import com.smartlearnly.backend.flashcard.dto.AdminFlashcardDtos.FlashcardLessonCreatedResponse;
import com.smartlearnly.backend.flashcard.dto.AdminFlashcardDtos.FlashcardSetResponse;
import com.smartlearnly.backend.flashcard.dto.AdminFlashcardDtos.ReorderFlashcardCardsRequest;
import com.smartlearnly.backend.flashcard.dto.AdminFlashcardDtos.UpdateFlashcardCardRequest;
import com.smartlearnly.backend.flashcard.dto.AdminFlashcardDtos.UpdateFlashcardSetRequest;
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

/**
 * Trainer flashcard CRUD scoped to a class-draft lesson. Mirrors admin
 * flashcard endpoints but every call verifies the associated lesson belongs
 * to the trainer's class draft curriculum. Sets are linked via the
 * {@code curriculum_lesson_id} column so master flashcards are untouched.
 */
@Validated
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('TRAINER', 'ADMIN', 'TMO')")
@RequestMapping("/api/v1/trainer/classes/{classId}/curriculum")
@Tag(name = "Trainer Class Lesson Flashcards", description = "Trainer flashcard authoring APIs for class curriculum drafts")
@SecurityRequirement(name = "bearerAuth")
public class TrainerLessonFlashcardController {
    private final TrainerLessonFlashcardService trainerLessonFlashcardService;

    @PostMapping("/lessons/{lessonId}/flashcards")
    @Operation(summary = "Create flashcard set for a class-draft lesson")
    public ResponseEntity<ApiResponse<FlashcardLessonCreatedResponse>> createFlashcardSet(
            @PathVariable UUID classId,
            @PathVariable UUID lessonId,
            @Valid @RequestBody CreateFlashcardLessonRequest request
    ) {
        FlashcardLessonCreatedResponse response = trainerLessonFlashcardService.createFlashcardSet(classId, lessonId, request);
        URI location = URI.create("/api/v1/trainer/classes/" + classId
                + "/curriculum/flashcards/set/" + response.setId());
        return ResponseEntity.created(location)
                .body(ApiResponse.success("Flashcard set created successfully", response));
    }

    @GetMapping("/lessons/{lessonId}/flashcards/set")
    @Operation(summary = "Get flashcard set attached to a class-draft lesson")
    public ApiResponse<FlashcardSetResponse> getSetByLesson(
            @PathVariable UUID classId,
            @PathVariable UUID lessonId
    ) {
        return ApiResponse.success(
                "Flashcard set loaded successfully",
                trainerLessonFlashcardService.getSetByLesson(classId, lessonId)
        );
    }

    @GetMapping("/flashcards/set/{setId}")
    @Operation(summary = "Get flashcard set")
    public ApiResponse<FlashcardSetResponse> getSet(
            @PathVariable UUID classId,
            @PathVariable UUID setId
    ) {
        return ApiResponse.success(
                "Flashcard set loaded successfully",
                trainerLessonFlashcardService.getSet(classId, setId)
        );
    }

    @PatchMapping("/flashcards/set/{setId}")
    @Operation(summary = "Update flashcard set metadata")
    public ApiResponse<FlashcardSetResponse> updateSet(
            @PathVariable UUID classId,
            @PathVariable UUID setId,
            @Valid @RequestBody UpdateFlashcardSetRequest request
    ) {
        return ApiResponse.success(
                "Flashcard set updated successfully",
                trainerLessonFlashcardService.updateSet(classId, setId, request)
        );
    }

    @DeleteMapping("/flashcards/set/{setId}")
    @Operation(summary = "Delete flashcard set")
    public ApiResponse<Void> deleteSet(
            @PathVariable UUID classId,
            @PathVariable UUID setId
    ) {
        trainerLessonFlashcardService.deleteSet(classId, setId);
        return ApiResponse.success("Flashcard set deleted successfully");
    }

    @PostMapping("/flashcards/set/{setId}/cards")
    @Operation(summary = "Add a flashcard card")
    public ResponseEntity<ApiResponse<FlashcardCardResponse>> addCard(
            @PathVariable UUID classId,
            @PathVariable UUID setId,
            @Valid @RequestBody CreateFlashcardCardRequest request
    ) {
        FlashcardCardResponse response = trainerLessonFlashcardService.addCard(classId, setId, request);
        URI location = URI.create("/api/v1/trainer/classes/" + classId
                + "/curriculum/flashcards/cards/" + response.id());
        return ResponseEntity.created(location)
                .body(ApiResponse.success("Flashcard card created successfully", response));
    }

    @PatchMapping("/flashcards/cards/{cardId}")
    @Operation(summary = "Update a flashcard card")
    public ApiResponse<FlashcardCardResponse> updateCard(
            @PathVariable UUID classId,
            @PathVariable UUID cardId,
            @Valid @RequestBody UpdateFlashcardCardRequest request
    ) {
        return ApiResponse.success(
                "Flashcard card updated successfully",
                trainerLessonFlashcardService.updateCard(classId, cardId, request)
        );
    }

    @DeleteMapping("/flashcards/cards/{cardId}")
    @Operation(summary = "Delete a flashcard card")
    public ApiResponse<Void> deleteCard(
            @PathVariable UUID classId,
            @PathVariable UUID cardId
    ) {
        trainerLessonFlashcardService.deleteCard(classId, cardId);
        return ApiResponse.success("Flashcard card deleted successfully");
    }

    @PatchMapping("/flashcards/set/{setId}/cards/reorder")
    @Operation(summary = "Reorder all flashcard cards")
    public ApiResponse<FlashcardSetResponse> reorderCards(
            @PathVariable UUID classId,
            @PathVariable UUID setId,
            @Valid @RequestBody ReorderFlashcardCardsRequest request
    ) {
        return ApiResponse.success(
                "Flashcard cards reordered successfully",
                trainerLessonFlashcardService.reorderCards(classId, setId, request)
        );
    }
}
