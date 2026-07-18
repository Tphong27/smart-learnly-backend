package com.smartlearnly.backend.learning.controller;

import com.smartlearnly.backend.common.api.ApiResponse;
import com.smartlearnly.backend.learning.dto.LearningContentResponse;
import com.smartlearnly.backend.learning.service.LearningContentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/learning")
@Tag(name = "Learning", description = "Learning workspace APIs for enrolled students")
public class LearningController {
    private final LearningContentService learningContentService;

    @GetMapping("/courses/{courseId}")
    @PreAuthorize("hasRole('TRAINEE')")
    @Operation(summary = "Get full learning content for an enrolled course and class")
    public ApiResponse<LearningContentResponse> getLearningContent(
            @PathVariable UUID courseId,
            @RequestParam(required = false) UUID classId) {
        return ApiResponse.success(
                "Learning content loaded successfully",
                learningContentService.getLearningContent(courseId, classId));
    }
}
