package com.smartlearnly.backend.curriculum.controller;

import com.smartlearnly.backend.common.api.ApiResponse;
import com.smartlearnly.backend.course.dto.LessonRequest;
import com.smartlearnly.backend.course.dto.LessonResourceRequest;
import com.smartlearnly.backend.course.dto.LessonResourceResponse;
import com.smartlearnly.backend.course.dto.LessonResponse;
import com.smartlearnly.backend.course.dto.ReorderRequest;
import com.smartlearnly.backend.course.dto.SectionRequest;
import com.smartlearnly.backend.course.dto.SectionResponse;
import com.smartlearnly.backend.curriculum.dto.ClassCurriculumEditorResponse;
import com.smartlearnly.backend.curriculum.service.TrainerClassCurriculumService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('TRAINER', 'ADMIN', 'TMO')")
@RequestMapping("/api/v1/trainer/classes/{classId}/curriculum")
@Tag(name = "Trainer Class Curriculum", description = "Trainer class curriculum draft and publish APIs")
@SecurityRequirement(name = "bearerAuth")
public class TrainerClassCurriculumController {
    private final TrainerClassCurriculumService trainerClassCurriculumService;

    @GetMapping
    @Operation(summary = "Get trainer class curriculum editor view")
    public ApiResponse<ClassCurriculumEditorResponse> getCurriculum(@PathVariable UUID classId) {
        return ApiResponse.success(
                "Class curriculum loaded successfully",
                trainerClassCurriculumService.getEditorCurriculum(classId)
        );
    }

    @PostMapping("/draft")
    @Operation(summary = "Initialize class curriculum draft")
    public ResponseEntity<ApiResponse<ClassCurriculumEditorResponse>> initializeDraft(@PathVariable UUID classId) {
        ClassCurriculumEditorResponse response = trainerClassCurriculumService.initializeDraft(classId);
        return ResponseEntity.created(URI.create("/api/v1/trainer/classes/" + classId + "/curriculum"))
                .body(ApiResponse.success("Class curriculum draft initialized successfully", response));
    }

    @PostMapping("/publish")
    @Operation(summary = "Publish active class curriculum draft")
    public ApiResponse<ClassCurriculumEditorResponse> publishDraft(@PathVariable UUID classId) {
        return ApiResponse.success(
                "Class curriculum published successfully",
                trainerClassCurriculumService.publishDraft(classId)
        );
    }

    @PostMapping("/sections")
    @Operation(summary = "Create draft curriculum section")
    public ResponseEntity<ApiResponse<SectionResponse>> createSection(
            @PathVariable UUID classId,
            @Valid @RequestBody SectionRequest request
    ) {
        SectionResponse section = trainerClassCurriculumService.createSection(classId, request);
        return ResponseEntity.created(URI.create("/api/v1/trainer/classes/" + classId + "/curriculum/sections/" + section.id()))
                .body(ApiResponse.success("Section created successfully", section));
    }

    @PutMapping("/sections/order")
    @Operation(summary = "Reorder all draft curriculum sections")
    public ApiResponse<List<SectionResponse>> reorderSections(
            @PathVariable UUID classId,
            @Valid @RequestBody ReorderRequest request
    ) {
        return ApiResponse.success(
                "Sections reordered successfully",
                trainerClassCurriculumService.reorderSections(classId, request)
        );
    }

    @PutMapping("/sections/{sectionId}")
    @Operation(summary = "Update draft curriculum section")
    public ApiResponse<SectionResponse> updateSection(
            @PathVariable UUID classId,
            @PathVariable UUID sectionId,
            @Valid @RequestBody SectionRequest request
    ) {
        return ApiResponse.success(
                "Section updated successfully",
                trainerClassCurriculumService.updateSection(classId, sectionId, request)
        );
    }

    @DeleteMapping("/sections/{sectionId}")
    @Operation(summary = "Delete draft curriculum section")
    public ApiResponse<Void> deleteSection(
            @PathVariable UUID classId,
            @PathVariable UUID sectionId
    ) {
        trainerClassCurriculumService.deleteSection(classId, sectionId);
        return ApiResponse.success("Section deleted successfully");
    }

    @PostMapping("/sections/{sectionId}/lessons")
    @Operation(summary = "Create draft curriculum lesson")
    public ResponseEntity<ApiResponse<LessonResponse>> createLesson(
            @PathVariable UUID classId,
            @PathVariable UUID sectionId,
            @Valid @RequestBody LessonRequest request
    ) {
        LessonResponse lesson = trainerClassCurriculumService.createLesson(classId, sectionId, request);
        return ResponseEntity.created(URI.create("/api/v1/trainer/classes/" + classId + "/curriculum/lessons/" + lesson.id()))
                .body(ApiResponse.success("Lesson created successfully", lesson));
    }

    @PutMapping("/sections/{sectionId}/lessons/order")
    @Operation(summary = "Reorder all lessons in a draft curriculum section")
    public ApiResponse<List<LessonResponse>> reorderLessons(
            @PathVariable UUID classId,
            @PathVariable UUID sectionId,
            @Valid @RequestBody ReorderRequest request
    ) {
        return ApiResponse.success(
                "Lessons reordered successfully",
                trainerClassCurriculumService.reorderLessons(classId, sectionId, request)
        );
    }

    @GetMapping("/lessons/{lessonId}")
    @Operation(summary = "Get draft/published curriculum lesson detail")
    public ApiResponse<LessonResponse> getLesson(
            @PathVariable UUID classId,
            @PathVariable UUID lessonId
    ) {
        return ApiResponse.success(
                "Lesson loaded successfully",
                trainerClassCurriculumService.getLesson(classId, lessonId)
        );
    }

    @PutMapping("/lessons/{lessonId}")
    @Operation(summary = "Update draft curriculum lesson")
    public ApiResponse<LessonResponse> updateLesson(
            @PathVariable UUID classId,
            @PathVariable UUID lessonId,
            @Valid @RequestBody LessonRequest request
    ) {
        return ApiResponse.success(
                "Lesson updated successfully",
                trainerClassCurriculumService.updateLesson(classId, lessonId, request)
        );
    }

    @DeleteMapping("/lessons/{lessonId}")
    @Operation(summary = "Delete draft curriculum lesson")
    public ApiResponse<Void> deleteLesson(
            @PathVariable UUID classId,
            @PathVariable UUID lessonId
    ) {
        trainerClassCurriculumService.deleteLesson(classId, lessonId);
        return ApiResponse.success("Lesson deleted successfully");
    }

    @PostMapping("/lessons/{lessonId}/resources")
    @Operation(summary = "Add draft lesson resource")
    public ResponseEntity<ApiResponse<LessonResourceResponse>> addResource(
            @PathVariable UUID classId,
            @PathVariable UUID lessonId,
            @Valid @RequestBody LessonResourceRequest request
    ) {
        LessonResourceResponse resource = trainerClassCurriculumService.addResource(classId, lessonId, request);
        return ResponseEntity.created(URI.create("/api/v1/trainer/classes/" + classId + "/curriculum/resources/" + resource.id()))
                .body(ApiResponse.success("Resource added successfully", resource));
    }

    @PutMapping("/lessons/{lessonId}/resources")
    @Operation(summary = "Replace all draft lesson resources")
    public ApiResponse<List<LessonResourceResponse>> replaceResources(
            @PathVariable UUID classId,
            @PathVariable UUID lessonId,
            @Valid @RequestBody List<@Valid LessonResourceRequest> request
    ) {
        return ApiResponse.success(
                "Resources replaced successfully",
                trainerClassCurriculumService.replaceResources(classId, lessonId, request)
        );
    }

    @PutMapping("/lessons/{lessonId}/resources/order")
    @Operation(summary = "Reorder all draft lesson resources")
    public ApiResponse<List<LessonResourceResponse>> reorderResources(
            @PathVariable UUID classId,
            @PathVariable UUID lessonId,
            @Valid @RequestBody ReorderRequest request
    ) {
        return ApiResponse.success(
                "Resources reordered successfully",
                trainerClassCurriculumService.reorderResources(classId, lessonId, request)
        );
    }

    @DeleteMapping("/lessons/{lessonId}/resources/{resourceId}")
    @Operation(summary = "Remove draft lesson resource")
    public ApiResponse<Void> removeResource(
            @PathVariable UUID classId,
            @PathVariable UUID lessonId,
            @PathVariable UUID resourceId
    ) {
        trainerClassCurriculumService.removeResource(classId, lessonId, resourceId);
        return ApiResponse.success("Resource removed successfully");
    }
}
