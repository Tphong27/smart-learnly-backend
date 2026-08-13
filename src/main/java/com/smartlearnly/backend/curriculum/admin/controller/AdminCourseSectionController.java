package com.smartlearnly.backend.curriculum.admin.controller;

import com.smartlearnly.backend.common.api.ApiResponse;
import com.smartlearnly.backend.curriculum.dto.ModuleRequest;
import com.smartlearnly.backend.curriculum.dto.ModuleResponse;
import com.smartlearnly.backend.curriculum.dto.ReorderRequest;
import com.smartlearnly.backend.curriculum.dto.SectionRequest;
import com.smartlearnly.backend.curriculum.dto.SectionResponse;
import com.smartlearnly.backend.curriculum.admin.service.CurriculumSectionAdminService;
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
public class AdminCourseSectionController {
    private final CurriculumSectionAdminService curriculumSectionAdminService;

    // Trả danh sách section của master curriculum theo thứ tự hiện tại.
    @GetMapping("/courses/{courseId}/sections")
    public ApiResponse<List<SectionResponse>> listSections(@PathVariable UUID courseId) {
        return ApiResponse.success("Sections loaded successfully", curriculumSectionAdminService.listSections(courseId));
    }

    // Trả danh sách module của khóa học theo contract admin mới.
    @GetMapping("/courses/{courseId}/modules")
    public ApiResponse<List<ModuleResponse>> listModules(@PathVariable UUID courseId) {
        return ApiResponse.success("Modules loaded successfully", curriculumSectionAdminService.listModules(courseId));
    }

    // Tạo section trong master curriculum và trả URL resource vừa tạo.
    @PostMapping("/courses/{courseId}/sections")
    @PreAuthorize("hasAnyRole('ADMIN', 'SME', 'TRAINER')")
    public ResponseEntity<ApiResponse<SectionResponse>> createSection(
            @PathVariable UUID courseId,
            @Valid @RequestBody SectionRequest request) {
        SectionResponse section = curriculumSectionAdminService.createSection(courseId, request);
        return ResponseEntity.created(URI.create("/api/v1/admin/sections/" + section.id()))
                .body(ApiResponse.success("Section created successfully", section));
    }

    // Tạo module trong khóa học và trả URL resource vừa tạo.
    @PostMapping("/courses/{courseId}/modules")
    @PreAuthorize("hasAnyRole('ADMIN', 'SME', 'TRAINER')")
    public ResponseEntity<ApiResponse<ModuleResponse>> createModule(
            @PathVariable UUID courseId,
            @Valid @RequestBody ModuleRequest request) {
        ModuleResponse module = curriculumSectionAdminService.createModule(courseId, request);
        return ResponseEntity.created(URI.create("/api/v1/admin/courses/" + courseId + "/modules/" + module.moduleId()))
                .body(ApiResponse.success("Module created successfully", module));
    }

    // Lưu thứ tự đầy đủ của section trong khóa học.
    @PutMapping("/courses/{courseId}/sections/order")
    @PreAuthorize("hasAnyRole('ADMIN', 'SME', 'TRAINER')")
    public ApiResponse<List<SectionResponse>> reorderSections(
            @PathVariable UUID courseId,
            @Valid @RequestBody ReorderRequest request) {
        return ApiResponse.success(
                "Sections reordered successfully",
                curriculumSectionAdminService.reorderSections(courseId, request));
    }

    // Lưu thứ tự đầy đủ của module trong khóa học.
    @PutMapping("/courses/{courseId}/modules/order")
    @PreAuthorize("hasAnyRole('ADMIN', 'SME', 'TRAINER')")
    public ApiResponse<List<ModuleResponse>> reorderModules(
            @PathVariable UUID courseId,
            @Valid @RequestBody ReorderRequest request) {
        return ApiResponse.success(
                "Modules reordered successfully",
                curriculumSectionAdminService.reorderModules(courseId, request));
    }

    // Trả chi tiết một section mà người dùng có quyền đọc.
    @GetMapping("/sections/{sectionId}")
    public ApiResponse<SectionResponse> getSection(@PathVariable UUID sectionId) {
        return ApiResponse.success("Section loaded successfully", curriculumSectionAdminService.getSection(sectionId));
    }

    // Trả chi tiết một module snapshot mà người dùng có quyền đọc.
    @GetMapping("/modules/{moduleId}")
    public ApiResponse<ModuleResponse> getModule(@PathVariable UUID moduleId) {
        return ApiResponse.success("Module loaded successfully", curriculumSectionAdminService.getModule(moduleId));
    }

    // Cập nhật section trong master curriculum.
    @PutMapping("/sections/{sectionId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SME', 'TRAINER')")
    public ApiResponse<SectionResponse> updateSection(
            @PathVariable UUID sectionId,
            @Valid @RequestBody SectionRequest request) {
        return ApiResponse.success(
                "Section updated successfully",
                curriculumSectionAdminService.updateSection(sectionId, request));
    }

    // Cập nhật module và đồng bộ module canonical tương ứng.
    @PutMapping("/modules/{moduleId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SME', 'TRAINER')")
    public ApiResponse<ModuleResponse> updateModule(
            @PathVariable UUID moduleId,
            @Valid @RequestBody ModuleRequest request) {
        return ApiResponse.success(
                "Module updated successfully",
                curriculumSectionAdminService.updateModule(moduleId, request));
    }

    // Xóa section cùng lesson trực thuộc theo nghiệp vụ hiện tại.
    @DeleteMapping("/sections/{sectionId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SME', 'TRAINER')")
    public ApiResponse<Void> deleteSection(@PathVariable UUID sectionId) {
        curriculumSectionAdminService.deleteSection(sectionId);
        return ApiResponse.success("Section deleted successfully");
    }

    // Xóa module và vô hiệu module canonical tương ứng.
    @DeleteMapping("/modules/{moduleId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SME', 'TRAINER')")
    public ApiResponse<Void> deleteModule(@PathVariable UUID moduleId) {
        curriculumSectionAdminService.deleteModule(moduleId);
        return ApiResponse.success("Module deleted successfully");
    }
}
