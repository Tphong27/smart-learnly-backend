package com.smartlearnly.backend.videoai.controller;

import com.smartlearnly.backend.common.api.ApiResponse;
import com.smartlearnly.backend.videoai.dto.LearnerVideoAiToolsDtos.FlashcardDeckResponse;
import com.smartlearnly.backend.videoai.dto.LearnerVideoAiToolsDtos.GenerateToolRequest;
import com.smartlearnly.backend.videoai.dto.LearnerVideoAiToolsDtos.QuizResponse;
import com.smartlearnly.backend.videoai.dto.LearnerVideoAiToolsDtos.ToolsResponse;
import com.smartlearnly.backend.videoai.service.LearnerVideoAiToolsService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/learning/courses/{courseId}/lessons/{lessonId}/video-ai/tools")
@PreAuthorize("hasRole('TRAINEE')")
public class LearningVideoAiToolsController {
    private final LearnerVideoAiToolsService service;

    @GetMapping
    public ApiResponse<ToolsResponse> tools(
            @PathVariable UUID courseId,
            @PathVariable UUID lessonId,
            @RequestParam(required = false) UUID classId
    ) {
        return ApiResponse.success(service.getTools(courseId, lessonId, classId));
    }

    @PostMapping("/flashcards")
    public ApiResponse<FlashcardDeckResponse> flashcards(
            @PathVariable UUID courseId,
            @PathVariable UUID lessonId,
            @RequestParam(required = false) UUID classId,
            @Valid @RequestBody(required = false) GenerateToolRequest request
    ) {
        return ApiResponse.success(service.generateFlashcards(courseId, lessonId, classId, request));
    }

    @PostMapping("/quiz")
    public ApiResponse<QuizResponse> quiz(
            @PathVariable UUID courseId,
            @PathVariable UUID lessonId,
            @RequestParam(required = false) UUID classId,
            @Valid @RequestBody(required = false) GenerateToolRequest request
    ) {
        return ApiResponse.success(service.generateQuiz(courseId, lessonId, classId, request));
    }
}
