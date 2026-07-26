package com.smartlearnly.backend.course.controller;

import com.smartlearnly.backend.common.api.ApiResponse;
import com.smartlearnly.backend.course.dto.LessonRequest;
import com.smartlearnly.backend.course.dto.LessonResponse;
import com.smartlearnly.backend.course.dto.ModuleRequest;
import com.smartlearnly.backend.course.dto.ModuleResponse;
import com.smartlearnly.backend.course.dto.ReorderRequest;
import com.smartlearnly.backend.course.dto.SectionRequest;
import com.smartlearnly.backend.course.dto.SectionResponse;
import com.smartlearnly.backend.course.service.CourseContentAdminService;
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
@PreAuthorize("hasAnyRole('ADMIN', 'TMO', 'SME', 'TRAINER')")
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin Course Content", description = "Administrator module and lesson authoring APIs.")
@SecurityRequirement(name = "bearerAuth")
public class AdminCourseContentController {
    private final CourseContentAdminService courseContentAdminService;

    @GetMapping("/courses/{courseId}/sections")
    @Operation(summary = "List course sections")
    public ApiResponse<List<SectionResponse>> listSections(@PathVariable UUID courseId) {
        return ApiResponse.success("Sections loaded successfully", courseContentAdminService.listSections(courseId));
    }

    @GetMapping("/courses/{courseId}/modules")
    @Operation(summary = "List course modules")
    public ApiResponse<List<ModuleResponse>> listModules(@PathVariable UUID courseId) {
        return ApiResponse.success("Modules loaded successfully", courseContentAdminService.listModules(courseId));
    }

    @PostMapping("/courses/{courseId}/sections")
    @Operation(summary = "Create a course section")
    public ResponseEntity<ApiResponse<SectionResponse>> createSection(
            @PathVariable UUID courseId,
            @Valid @RequestBody SectionRequest request
    ) {
        SectionResponse section = courseContentAdminService.createSection(courseId, request);
        return ResponseEntity.created(URI.create("/api/v1/admin/sections/" + section.id()))
                .body(ApiResponse.success("Section created successfully", section));
    }

    @PostMapping("/courses/{courseId}/modules")
    @Operation(summary = "Create a course module")
    public ResponseEntity<ApiResponse<ModuleResponse>> createModule(
            @PathVariable UUID courseId,
            @Valid @RequestBody ModuleRequest request
    ) {
        ModuleResponse module = courseContentAdminService.createModule(courseId, request);
        return ResponseEntity.created(URI.create("/api/v1/admin/courses/" + courseId + "/modules/" + module.moduleId()))
                .body(ApiResponse.success("Module created successfully", module));
    }

    @PutMapping("/courses/{courseId}/sections/order")
    @Operation(summary = "Reorder all course sections")
    public ApiResponse<List<SectionResponse>> reorderSections(
            @PathVariable UUID courseId,
            @Valid @RequestBody ReorderRequest request
    ) {
        return ApiResponse.success("Sections reordered successfully", courseContentAdminService.reorderSections(courseId, request));
    }

    @PutMapping("/courses/{courseId}/modules/order")
    @Operation(summary = "Reorder all course modules")
    public ApiResponse<List<ModuleResponse>> reorderModules(
            @PathVariable UUID courseId,
            @Valid @RequestBody ReorderRequest request
    ) {
        return ApiResponse.success("Modules reordered successfully", courseContentAdminService.reorderModules(courseId, request));
    }

    @GetMapping("/sections/{sectionId}")
    @Operation(summary = "Get section details")
    public ApiResponse<SectionResponse> getSection(@PathVariable UUID sectionId) {
        return ApiResponse.success("Section loaded successfully", courseContentAdminService.getSection(sectionId));
    }

    @GetMapping("/modules/{moduleId}")
    @Operation(summary = "Get module details")
    public ApiResponse<ModuleResponse> getModule(@PathVariable UUID moduleId) {
        return ApiResponse.success("Module loaded successfully", courseContentAdminService.getModule(moduleId));
    }

    @PutMapping("/sections/{sectionId}")
    @Operation(summary = "Update a section")
    public ApiResponse<SectionResponse> updateSection(
            @PathVariable UUID sectionId,
            @Valid @RequestBody SectionRequest request
    ) {
        return ApiResponse.success("Section updated successfully", courseContentAdminService.updateSection(sectionId, request));
    }

    @PutMapping("/modules/{moduleId}")
    @Operation(summary = "Update a module")
    public ApiResponse<ModuleResponse> updateModule(
            @PathVariable UUID moduleId,
            @Valid @RequestBody ModuleRequest request
    ) {
        return ApiResponse.success("Module updated successfully", courseContentAdminService.updateModule(moduleId, request));
    }

    @DeleteMapping("/sections/{sectionId}")
    @Operation(summary = "Delete a section and its lessons")
    public ApiResponse<Void> deleteSection(@PathVariable UUID sectionId) {
        courseContentAdminService.deleteSection(sectionId);
        return ApiResponse.success("Section deleted successfully");
    }

    @DeleteMapping("/modules/{moduleId}")
    @Operation(summary = "Delete a module and its lessons")
    public ApiResponse<Void> deleteModule(@PathVariable UUID moduleId) {
        courseContentAdminService.deleteModule(moduleId);
        return ApiResponse.success("Module deleted successfully");
    }

    @GetMapping("/sections/{sectionId}/lessons")
    @Operation(summary = "List section lessons")
    public ApiResponse<List<LessonResponse>> listLessons(@PathVariable UUID sectionId) {
        return ApiResponse.success("Lessons loaded successfully", courseContentAdminService.listLessons(sectionId));
    }

    @GetMapping("/courses/{courseId}/modules/{moduleId}/lessons")
    @Operation(summary = "List module lessons")
    public ApiResponse<List<LessonResponse>> listModuleLessons(
            @PathVariable UUID courseId,
            @PathVariable UUID moduleId
    ) {
        return ApiResponse.success("Lessons loaded successfully", courseContentAdminService.listModuleLessons(moduleId));
    }

    @GetMapping("/modules/{moduleId}/lessons")
    @Operation(summary = "List module lessons")
    public ApiResponse<List<LessonResponse>> listModuleLessons(@PathVariable UUID moduleId) {
        return ApiResponse.success("Lessons loaded successfully", courseContentAdminService.listModuleLessons(moduleId));
    }

    @PostMapping("/sections/{sectionId}/lessons")
    @Operation(summary = "Create a lesson")
    public ResponseEntity<ApiResponse<LessonResponse>> createLesson(
            @PathVariable UUID sectionId,
            @Valid @RequestBody LessonRequest request
    ) {
        LessonResponse lesson = courseContentAdminService.createLesson(sectionId, request);
        return ResponseEntity.created(URI.create("/api/v1/admin/lessons/" + lesson.id()))
                .body(ApiResponse.success("Lesson created successfully", lesson));
    }

    @PostMapping("/courses/{courseId}/modules/{moduleId}/lessons")
    @Operation(summary = "Create a lesson in a module")
    public ResponseEntity<ApiResponse<LessonResponse>> createModuleLesson(
            @PathVariable UUID courseId,
            @PathVariable UUID moduleId,
            @Valid @RequestBody LessonRequest request
    ) {
        LessonResponse lesson = courseContentAdminService.createModuleLesson(moduleId, request);
        return ResponseEntity.created(URI.create("/api/v1/admin/lessons/" + lesson.id()))
                .body(ApiResponse.success("Lesson created successfully", lesson));
    }

    @PostMapping("/modules/{moduleId}/lessons")
    @Operation(summary = "Create a lesson in a module")
    public ResponseEntity<ApiResponse<LessonResponse>> createModuleLesson(
            @PathVariable UUID moduleId,
            @Valid @RequestBody LessonRequest request
    ) {
        LessonResponse lesson = courseContentAdminService.createModuleLesson(moduleId, request);
        return ResponseEntity.created(URI.create("/api/v1/admin/lessons/" + lesson.id()))
                .body(ApiResponse.success("Lesson created successfully", lesson));
    }

    @PutMapping("/sections/{sectionId}/lessons/order")
    @Operation(summary = "Reorder all lessons in a section")
    public ApiResponse<List<LessonResponse>> reorderLessons(
            @PathVariable UUID sectionId,
            @Valid @RequestBody ReorderRequest request
    ) {
        return ApiResponse.success("Lessons reordered successfully", courseContentAdminService.reorderLessons(sectionId, request));
    }

    @PutMapping("/courses/{courseId}/modules/{moduleId}/lessons/order")
    @Operation(summary = "Reorder all lessons in a module")
    public ApiResponse<List<LessonResponse>> reorderModuleLessons(
            @PathVariable UUID courseId,
            @PathVariable UUID moduleId,
            @Valid @RequestBody ReorderRequest request
    ) {
        return ApiResponse.success("Lessons reordered successfully", courseContentAdminService.reorderModuleLessons(moduleId, request));
    }

    @PutMapping("/modules/{moduleId}/lessons/order")
    @Operation(summary = "Reorder all lessons in a module")
    public ApiResponse<List<LessonResponse>> reorderModuleLessons(
            @PathVariable UUID moduleId,
            @Valid @RequestBody ReorderRequest request
    ) {
        return ApiResponse.success("Lessons reordered successfully", courseContentAdminService.reorderModuleLessons(moduleId, request));
    }

    @GetMapping("/lessons/{lessonId}")
    @Operation(summary = "Get lesson details")
    public ApiResponse<LessonResponse> getLesson(@PathVariable UUID lessonId) {
        return ApiResponse.success("Lesson loaded successfully", courseContentAdminService.getLesson(lessonId));
    }

    @PutMapping("/lessons/{lessonId}")
    @Operation(summary = "Update a lesson")
    public ApiResponse<LessonResponse> updateLesson(
            @PathVariable UUID lessonId,
            @Valid @RequestBody LessonRequest request
    ) {
        return ApiResponse.success("Lesson updated successfully", courseContentAdminService.updateLesson(lessonId, request));
    }

    @DeleteMapping("/lessons/{lessonId}")
    @Operation(summary = "Deactivate a lesson")
    public ApiResponse<Void> deleteLesson(@PathVariable UUID lessonId) {
        courseContentAdminService.deleteLesson(lessonId);
        return ApiResponse.success("Lesson deactivated successfully");
    }
}
