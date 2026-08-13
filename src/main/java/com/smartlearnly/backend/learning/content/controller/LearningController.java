package com.smartlearnly.backend.learning.content.controller;

import com.smartlearnly.backend.common.api.ApiResponse;
import com.smartlearnly.backend.learning.content.dto.LearningContentResponse;
import com.smartlearnly.backend.learning.content.dto.LearningFlashcardPracticeDtos.FlashcardPracticeSetResponse;
import com.smartlearnly.backend.learning.content.dto.LearningFlashcardPracticeDtos.FlashcardProgressRequest;
import com.smartlearnly.backend.learning.content.dto.LearningFlashcardPracticeDtos.FlashcardProgressResponse;
import com.smartlearnly.backend.learning.content.service.LearningContentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/learning")
public class LearningController {
    private final LearningContentService learningContentService;

    /** Trả về curriculum và trạng thái hoàn thành của khóa học mà học viên được phép học. */
    @GetMapping("/courses/{courseId}")
    @PreAuthorize("hasRole('TRAINEE')")
    public ApiResponse<LearningContentResponse> getLearningContent(
            @PathVariable UUID courseId,
            @RequestParam(required = false) UUID classId) {
        return ApiResponse.success(
                "Learning content loaded successfully",
                learningContentService.getLearningContent(courseId, classId));
    }

    @GetMapping("/courses/{courseId}/lessons/{lessonId}/flashcards")
    @PreAuthorize("hasRole('TRAINEE')")
    public ApiResponse<FlashcardPracticeSetResponse> getLearningFlashcards(
            @PathVariable UUID courseId,
            @PathVariable UUID lessonId,
            @RequestParam(required = false) UUID classId) {
        return ApiResponse.success(
                "Flashcards loaded successfully",
                learningContentService.getLearningFlashcards(courseId, classId, lessonId));
    }

    @PostMapping("/flashcards/{cardId}/progress")
    @PreAuthorize("hasRole('TRAINEE')")
    public ApiResponse<FlashcardProgressResponse> submitFlashcardProgress(
            @PathVariable UUID cardId,
            @RequestParam(required = false) UUID classId,
            @Valid @RequestBody FlashcardProgressRequest request) {
        return ApiResponse.success(
                "Flashcard progress saved successfully",
                learningContentService.submitFlashcardProgress(cardId, classId, request));
    }
}
