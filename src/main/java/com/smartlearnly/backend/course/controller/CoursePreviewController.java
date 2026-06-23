package com.smartlearnly.backend.course.controller;

import com.smartlearnly.backend.common.api.ApiResponse;
import com.smartlearnly.backend.learning.dto.LearningContentResponse;
import com.smartlearnly.backend.learning.service.LearningContentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/courses")
@Tag(name = "Course Preview", description = "Public course preview APIs for non-enrolled users")
public class CoursePreviewController {
    private final LearningContentService learningContentService;

    @GetMapping("/{courseId}/preview")
    @Operation(summary = "Get preview content for a published course (for guests/non-enrolled users)")
    public ApiResponse<LearningContentResponse> getPreviewContent(@PathVariable UUID courseId) {
        return ApiResponse.success("Preview content loaded successfully",
                learningContentService.getPreviewContent(courseId));
    }
}
