package com.smartlearnly.backend.videoai.controller;

import com.smartlearnly.backend.common.api.ApiResponse;
import com.smartlearnly.backend.videoai.dto.VideoAiDtos.ContentResponse;
import com.smartlearnly.backend.videoai.service.VideoAiLearningService;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/learning/courses/{courseId}/lessons/{lessonId}/video-ai")
@PreAuthorize("hasRole('TRAINEE')")
public class LearningVideoAiController {
    private final VideoAiLearningService service;

    @GetMapping
    public ApiResponse<Object> content(
            @PathVariable UUID courseId,
            @PathVariable UUID lessonId,
            @RequestParam(required = false) UUID classId) {
        Optional<ContentResponse> content = service.getPublished(courseId, lessonId, classId);
        return ApiResponse.success(content.<Object>map(value -> value).orElseGet(() -> Map.of("available", false)));
    }
}
