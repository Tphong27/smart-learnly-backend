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
public class AdminFlashcardController {
    private final AdminFlashcardService adminFlashcardService;

    /** Tạo flashcard lesson trong master curriculum; TMO chỉ được xem course. */
    @PostMapping("/courses/{courseId}/sections/{sectionId}/flashcard-lessons")
    @PreAuthorize("hasAnyRole('ADMIN', 'SME', 'TRAINER')")
    public ResponseEntity<ApiResponse<FlashcardLessonCreatedResponse>> createFlashcardLesson(
            @PathVariable UUID courseId,
            @PathVariable UUID sectionId,
            @Valid @RequestBody CreateFlashcardLessonRequest request) {
        FlashcardLessonCreatedResponse response = adminFlashcardService.createFlashcardLesson(courseId, sectionId, request);
        return ResponseEntity.created(URI.create("/api/v1/admin/flashcard-sets/" + response.setId()))
                .body(ApiResponse.success("Flashcard lesson created successfully", response));
    }

    /** Trả flashcard set theo ID cho role có quyền xem nội dung khóa học. */
    @GetMapping("/flashcard-sets/{setId}")
    public ApiResponse<FlashcardSetResponse> getSet(@PathVariable UUID setId) {
        return ApiResponse.success("Flashcard set loaded successfully", adminFlashcardService.getSet(setId));
    }

    /** Trả flashcard set gắn với lesson cho role có quyền xem. */
    @GetMapping("/lessons/{lessonId}/flashcards")
    public ApiResponse<FlashcardSetResponse> getSetByLesson(@PathVariable UUID lessonId) {
        return ApiResponse.success("Flashcard set loaded successfully", adminFlashcardService.getSetByLesson(lessonId));
    }

    /** Cập nhật metadata flashcard set trong master curriculum. */
    @PatchMapping("/flashcard-sets/{setId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SME', 'TRAINER')")
    public ApiResponse<FlashcardSetResponse> updateSet(
            @PathVariable UUID setId,
            @Valid @RequestBody UpdateFlashcardSetRequest request) {
        return ApiResponse.success("Flashcard set updated successfully", adminFlashcardService.updateSet(setId, request));
    }

    /** Xóa flashcard set khỏi master curriculum. */
    @DeleteMapping("/flashcard-sets/{setId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SME', 'TRAINER')")
    public ApiResponse<Void> deleteSet(@PathVariable UUID setId) {
        adminFlashcardService.deleteSet(setId);
        return ApiResponse.success("Flashcard set deleted successfully");
    }

    /** Thêm card mới vào flashcard set của master curriculum. */
    @PostMapping("/flashcard-sets/{setId}/cards")
    @PreAuthorize("hasAnyRole('ADMIN', 'SME', 'TRAINER')")
    public ResponseEntity<ApiResponse<FlashcardCardResponse>> addCard(
            @PathVariable UUID setId,
            @Valid @RequestBody CreateFlashcardCardRequest request) {
        FlashcardCardResponse response = adminFlashcardService.addCard(setId, request);
        return ResponseEntity.created(URI.create("/api/v1/admin/flashcard-cards/" + response.id()))
                .body(ApiResponse.success("Flashcard card created successfully", response));
    }

    /** Cập nhật card thuộc flashcard set của master curriculum. */
    @PatchMapping("/flashcard-cards/{cardId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SME', 'TRAINER')")
    public ApiResponse<FlashcardCardResponse> updateCard(
            @PathVariable UUID cardId,
            @Valid @RequestBody UpdateFlashcardCardRequest request) {
        return ApiResponse.success("Flashcard card updated successfully", adminFlashcardService.updateCard(cardId, request));
    }

    /** Xóa card khỏi flashcard set của master curriculum. */
    @DeleteMapping("/flashcard-cards/{cardId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SME', 'TRAINER')")
    public ApiResponse<Void> deleteCard(@PathVariable UUID cardId) {
        adminFlashcardService.deleteCard(cardId);
        return ApiResponse.success("Flashcard card deleted successfully");
    }

    /** Lưu thứ tự card mới trong flashcard set của master curriculum. */
    @PatchMapping("/flashcard-sets/{setId}/cards/reorder")
    @PreAuthorize("hasAnyRole('ADMIN', 'SME', 'TRAINER')")
    public ApiResponse<FlashcardSetResponse> reorderCards(
            @PathVariable UUID setId,
            @Valid @RequestBody ReorderFlashcardCardsRequest request) {
        return ApiResponse.success("Flashcard cards reordered successfully", adminFlashcardService.reorderCards(setId, request));
    }
}
