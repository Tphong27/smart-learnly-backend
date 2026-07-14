package com.smartlearnly.backend.course.controller;

import com.smartlearnly.backend.common.api.ApiResponse;
import com.smartlearnly.backend.course.dto.CategoryResponse;
import com.smartlearnly.backend.course.dto.CreateCategoryRequest;
import com.smartlearnly.backend.course.dto.UpdateCategoryRequest;
import com.smartlearnly.backend.course.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'TMO', 'SME', 'TRAINER')")
@RequestMapping("/api/v1/admin/categories")
@Tag(name = "Admin Categories", description = "Course-category management APIs.")
@SecurityRequirement(name = "bearerAuth")
public class AdminCategoryController {
    private final CategoryService categoryService;

    @GetMapping
    @Operation(summary = "List categories as a flat collection")
    public ApiResponse<List<CategoryResponse>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) UUID parentId) {
        return ApiResponse.success("Categories loaded successfully", categoryService.list(keyword, active, parentId));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TMO')")
    @Operation(summary = "Create a root or child category")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Category created"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Slug conflict"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Invalid hierarchy")
    })
    public ResponseEntity<ApiResponse<CategoryResponse>> create(@Valid @RequestBody CreateCategoryRequest request) {
        CategoryResponse category = categoryService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/admin/categories/" + category.id()))
                .body(ApiResponse.success("Category created successfully", category));
    }

    @GetMapping("/{categoryId}")
    @Operation(summary = "Get category details")
    public ApiResponse<CategoryResponse> get(@PathVariable UUID categoryId) {
        return ApiResponse.success("Category loaded successfully", categoryService.get(categoryId));
    }

    @PatchMapping("/{categoryId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TMO')")
    @Operation(summary = "Update selected category fields")
    public ApiResponse<CategoryResponse> update(@PathVariable UUID categoryId, @Valid @RequestBody UpdateCategoryRequest request) {
        return ApiResponse.success("Category updated successfully", categoryService.update(categoryId, request));
    }

    @DeleteMapping("/{categoryId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TMO')")
    @Operation(summary = "Delete an unused category")
    public ApiResponse<Void> delete(@PathVariable UUID categoryId) {
        categoryService.delete(categoryId);
        return ApiResponse.success("Category deleted successfully");
    }
}