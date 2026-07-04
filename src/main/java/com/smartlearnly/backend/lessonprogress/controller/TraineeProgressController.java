package com.smartlearnly.backend.lessonprogress.controller;

import com.smartlearnly.backend.common.api.ApiResponse;
import com.smartlearnly.backend.lessonprogress.dto.LessonProgressResponse;
import com.smartlearnly.backend.lessonprogress.dto.TraineeProgressResponse;
import com.smartlearnly.backend.lessonprogress.dto.UpdateLessonProgressRequest;
import com.smartlearnly.backend.lessonprogress.service.TraineeProgressService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/v1/learning/progress")
@RequiredArgsConstructor
@Tag(name = "Trainee Progress", description = "Progress APIs for trainee learning")
public class TraineeProgressController {
    private final TraineeProgressService traineeProgressService;

    @GetMapping("/my")
    @PreAuthorize("hasRole('TRAINEE')")
    @Operation(summary = "Get authenticated trainee learning progress")
    public ApiResponse<TraineeProgressResponse> getMyProgress() {
        return ApiResponse.success(
                "Trainee progress loaded successfully",
                traineeProgressService.getMyProgress());
    }

    @PatchMapping("/lessons/{lessonId}")
    @PreAuthorize("hasRole('TRAINEE')")
    @Operation(summary = "Mark lesson, quiz, or flashcard as completed/incomplete")
    public ApiResponse<LessonProgressResponse> updateLessonProgress(
            @PathVariable UUID lessonId,
            @RequestParam UUID classId,
            @Valid @RequestBody UpdateLessonProgressRequest request) {
        return ApiResponse.success(
                "Lesson progress updated successfully",
                traineeProgressService.updateLessonProgress(lessonId, classId, request.completed()));
    }
}
