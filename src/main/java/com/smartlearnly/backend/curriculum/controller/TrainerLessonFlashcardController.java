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
import com.smartlearnly.backend.flashcard.dto.FlashcardImageUploadResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Trainer flashcard CRUD scoped to a class-draft lesson. Every route is
 * nested under the lesson so the ownership boundary is enforced end-to-end.
 * Sets are linked via {@code curriculum_lesson_id} so master flashcards
 * remain untouched.
 */
@Validated
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('TRAINER', 'ADMIN', 'TMO')")
@RequestMapping("/api/v1/trainer/classes/{classId}/curriculum/lessons/{lessonId}/flashcards")
public class TrainerLessonFlashcardController {
    private final TrainerLessonFlashcardService trainerLessonFlashcardService;

    /** Tạo flashcard set cho lesson của lớp; TMO chỉ có quyền xem. */
    @PostMapping
    @PreAuthorize("hasAnyRole('TRAINER', 'ADMIN')")
    public ResponseEntity<ApiResponse<FlashcardLessonCreatedResponse>> createFlashcardSet(
            @PathVariable UUID classId,
            @PathVariable UUID lessonId,
            @Valid @RequestBody CreateFlashcardLessonRequest request
    ) {
        FlashcardLessonCreatedResponse response = trainerLessonFlashcardService.createFlashcardSet(classId, lessonId, request);
        URI location = URI.create("/api/v1/trainer/classes/" + classId
                + "/curriculum/lessons/" + lessonId
                + "/flashcards/set/" + response.setId());
        return ResponseEntity.created(location)
                .body(ApiResponse.success("Flashcard set created successfully", response));
    }

    /** Trả flashcard set của lesson cho role có quyền xem lớp. */
    @GetMapping("/set")
    public ApiResponse<FlashcardSetResponse> getSetByLesson(
            @PathVariable UUID classId,
            @PathVariable UUID lessonId
    ) {
        return ApiResponse.success(
                "Flashcard set loaded successfully",
                trainerLessonFlashcardService.getSetByLesson(classId, lessonId)
        );
    }

    /** Cập nhật metadata flashcard set trong class curriculum draft. */
    @PatchMapping("/set/{setId}")
    @PreAuthorize("hasAnyRole('TRAINER', 'ADMIN')")
    public ApiResponse<FlashcardSetResponse> updateSet(
            @PathVariable UUID classId,
            @PathVariable UUID lessonId,
            @PathVariable UUID setId,
            @Valid @RequestBody UpdateFlashcardSetRequest request
    ) {
        return ApiResponse.success(
                "Flashcard set updated successfully",
                trainerLessonFlashcardService.updateSet(classId, lessonId, setId, request)
        );
    }

    /** Xóa flashcard set khỏi lesson trong class curriculum draft. */
    @DeleteMapping("/set/{setId}")
    @PreAuthorize("hasAnyRole('TRAINER', 'ADMIN')")
    public ApiResponse<Void> deleteSet(
            @PathVariable UUID classId,
            @PathVariable UUID lessonId,
            @PathVariable UUID setId
    ) {
        trainerLessonFlashcardService.deleteSet(classId, lessonId, setId);
        return ApiResponse.success("Flashcard set deleted successfully");
    }

    /** Thêm card mới vào flashcard set của lớp. */
    @PostMapping("/set/{setId}/cards")
    @PreAuthorize("hasAnyRole('TRAINER', 'ADMIN')")
    public ResponseEntity<ApiResponse<FlashcardCardResponse>> addCard(
            @PathVariable UUID classId,
            @PathVariable UUID lessonId,
            @PathVariable UUID setId,
            @Valid @RequestBody CreateFlashcardCardRequest request
    ) {
        FlashcardCardResponse response = trainerLessonFlashcardService.addCard(classId, lessonId, setId, request);
        URI location = URI.create("/api/v1/trainer/classes/" + classId
                + "/curriculum/lessons/" + lessonId
                + "/flashcards/set/" + setId
                + "/cards/" + response.id());
        return ResponseEntity.created(location)
                .body(ApiResponse.success("Flashcard card created successfully", response));
    }

    /** Cập nhật card trong flashcard set của lớp. */
    @PatchMapping("/set/{setId}/cards/{cardId}")
    @PreAuthorize("hasAnyRole('TRAINER', 'ADMIN')")
    public ApiResponse<FlashcardCardResponse> updateCard(
            @PathVariable UUID classId,
            @PathVariable UUID lessonId,
            @PathVariable UUID setId,
            @PathVariable UUID cardId,
            @Valid @RequestBody UpdateFlashcardCardRequest request
    ) {
        return ApiResponse.success(
                "Flashcard card updated successfully",
                trainerLessonFlashcardService.updateCard(classId, lessonId, setId, cardId, request)
        );
    }

    /** Xóa card khỏi flashcard set của lớp. */
    @DeleteMapping("/set/{setId}/cards/{cardId}")
    @PreAuthorize("hasAnyRole('TRAINER', 'ADMIN')")
    public ApiResponse<Void> deleteCard(
            @PathVariable UUID classId,
            @PathVariable UUID lessonId,
            @PathVariable UUID setId,
            @PathVariable UUID cardId
    ) {
        trainerLessonFlashcardService.deleteCard(classId, lessonId, setId, cardId);
        return ApiResponse.success("Flashcard card deleted successfully");
    }

    /** Lưu thứ tự card mới trong flashcard set của lớp. */
    @PatchMapping("/set/{setId}/cards/reorder")
    @PreAuthorize("hasAnyRole('TRAINER', 'ADMIN')")
    public ApiResponse<FlashcardSetResponse> reorderCards(
            @PathVariable UUID classId,
            @PathVariable UUID lessonId,
            @PathVariable UUID setId,
            @Valid @RequestBody ReorderFlashcardCardsRequest request
    ) {
        return ApiResponse.success(
                "Flashcard cards reordered successfully",
                trainerLessonFlashcardService.reorderCards(classId, lessonId, setId, request)
        );
    }

    /** Tải ảnh cho card của class flashcard set; chỉ Trainer hoặc Admin được thay đổi. */
    @PostMapping(value = "/set/{setId}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('TRAINER', 'ADMIN')")
    public ApiResponse<FlashcardImageUploadResponse> uploadImage(
            @PathVariable UUID classId,
            @PathVariable UUID lessonId,
            @PathVariable UUID setId,
            @RequestPart(value = "file", required = false) MultipartFile file
    ) {
        return ApiResponse.success(
                "Flashcard image uploaded successfully",
                trainerLessonFlashcardService.uploadImage(classId, lessonId, setId, file)
        );
    }
}
