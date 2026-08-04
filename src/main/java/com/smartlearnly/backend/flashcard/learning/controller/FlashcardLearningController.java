package com.smartlearnly.backend.flashcard.learning.controller;

import com.smartlearnly.backend.common.api.ApiResponse;
import com.smartlearnly.backend.flashcard.learning.dto.FlashcardLearningDtos.FlashcardPracticeSetResponse;
import com.smartlearnly.backend.flashcard.learning.dto.FlashcardLearningDtos.LearningFlashcardSetResponse;
import com.smartlearnly.backend.flashcard.learning.dto.FlashcardLearningDtos.FlashcardProgressRequest;
import com.smartlearnly.backend.flashcard.learning.dto.FlashcardLearningDtos.FlashcardProgressResponse;
import com.smartlearnly.backend.flashcard.learning.service.FlashcardLearningService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/learning")
@Tag(name = "Flashcard Learning", description = "Student flashcard practice APIs.")
@SecurityRequirement(name = "bearerAuth")
public class FlashcardLearningController {
    private final FlashcardLearningService flashcardLearningService;

    /** Trả danh sách lesson flashcard mà học viên hiện tại có thể luyện tập. */
    @GetMapping("/flashcards")
    @PreAuthorize("hasRole('TRAINEE')")
    @Operation(summary = "List available flashcard lessons")
    public ApiResponse<List<LearningFlashcardSetResponse>> listLearningFlashcards() {
        return ApiResponse.success(
                "Flashcards loaded successfully",
                flashcardLearningService.listLearningFlashcards());
    }

    /** Trả bộ flashcard hiệu lực của lesson trong course online hoặc lớp học. */
    @GetMapping("/lessons/{lessonId}/flashcards")
    @PreAuthorize("hasRole('TRAINEE')")
    @Operation(summary = "Get flashcards for a class curriculum lesson")
    public ApiResponse<FlashcardPracticeSetResponse> getLessonFlashcards(
            @PathVariable UUID lessonId,
            @RequestParam(required = false) UUID classId) {
        return ApiResponse.success("Flashcards loaded successfully",flashcardLearningService.getLessonFlashcards(lessonId, classId));
    }

    /** Trả dữ liệu luyện tập của một flashcard set khi học viên có quyền truy cập. */
    @GetMapping("/flashcard-sets/{setId}")
    @PreAuthorize("hasRole('TRAINEE')")
    @Operation(summary = "Get flashcard practice data")
    public ApiResponse<FlashcardPracticeSetResponse> getSetPractice(@PathVariable UUID setId) {
        return ApiResponse.success(
                "Flashcards loaded successfully",
                flashcardLearningService.getSetPractice(setId));
    }

    /** Lưu kết quả ôn một thẻ để cập nhật lịch spaced repetition của học viên. */
    @PostMapping("/flashcards/{cardId}/progress")
    @PreAuthorize("hasRole('TRAINEE')")
    @Operation(summary = "Submit flashcard review progress")
    public ApiResponse<FlashcardProgressResponse> submitProgress(
            @PathVariable UUID cardId,
            @Valid @RequestBody FlashcardProgressRequest request) {
        return ApiResponse.success(
                "Flashcard progress saved successfully",
                flashcardLearningService.submitProgress(cardId, request));
    }
}
