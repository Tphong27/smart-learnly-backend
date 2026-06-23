package com.smartlearnly.backend.course.controller;

import com.smartlearnly.backend.common.api.ApiResponse;
import com.smartlearnly.backend.learning.dto.LearningContentResponse;
import com.smartlearnly.backend.learning.service.LearningContentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/courses")
@PreAuthorize("hasAnyRole('ADMIN', 'TMO', 'SME')")
@Tag(name = "Admin Course Learning Preview", description = "Admin learning preview APIs for viewing courses as a learner")
@SecurityRequirement(name = "bearerAuth")
public class AdminLearningPreviewController {
    private final LearningContentService learningContentService;

    @GetMapping("/{courseId}/learning-preview")
    @Operation(summary = "Get full learning content preview for a course (admin only, no enrollment required)")
    public ApiResponse<LearningContentResponse> getAdminLearningPreview(@PathVariable UUID courseId) {
        return ApiResponse.success("Admin preview loaded",
                learningContentService.getAdminPreviewContent(courseId));
    }
}
