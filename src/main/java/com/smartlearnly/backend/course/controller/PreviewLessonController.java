package com.smartlearnly.backend.course.controller;

import com.smartlearnly.backend.common.api.ApiResponse;
import com.smartlearnly.backend.course.dto.PreviewLessonResponse;
import com.smartlearnly.backend.course.service.PreviewLessonService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/courses/{courseId}/preview-lessons")
@Tag(name = "Course Preview Lessons", description = "Public sample lesson preview APIs.")
public class PreviewLessonController {
    private final PreviewLessonService previewLessonService;

    @GetMapping
    @Operation(summary = "List preview lessons for a published course")
    public ApiResponse<List<PreviewLessonResponse>> list(@PathVariable UUID courseId) {
        return ApiResponse.success("Preview lessons loaded successfully", previewLessonService.listPreviewLessons(courseId));
    }

    @GetMapping("/{lessonId}")
    @Operation(summary = "Get a preview lesson for a published course")
    public ApiResponse<PreviewLessonResponse> get(
            @PathVariable UUID courseId,
            @PathVariable UUID lessonId
    ) {
        return ApiResponse.success("Preview lesson loaded successfully", previewLessonService.getPreviewLesson(courseId, lessonId));
    }
}
