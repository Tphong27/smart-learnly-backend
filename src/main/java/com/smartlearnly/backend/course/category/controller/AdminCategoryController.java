package com.smartlearnly.backend.course.category.controller;

import com.smartlearnly.backend.common.api.ApiResponse;
import com.smartlearnly.backend.course.category.dto.CategoryResponse;
import com.smartlearnly.backend.course.category.dto.CreateCategoryRequest;
import com.smartlearnly.backend.course.category.dto.UpdateCategoryRequest;
import com.smartlearnly.backend.course.category.service.CategoryService;
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
public class AdminCategoryController {
    private final CategoryService categoryService;

    // Tải danh sách category phẳng cho màn hình quản trị.
    @GetMapping
    public ApiResponse<List<CategoryResponse>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) UUID parentId) {
        return ApiResponse.success("Categories loaded successfully", categoryService.list(keyword, active, parentId));
    }

    // Tạo category gốc hoặc category con và trả URL resource vừa tạo.
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CategoryResponse>> create(@Valid @RequestBody CreateCategoryRequest request) {
        CategoryResponse category = categoryService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/admin/categories/" + category.id()))
                .body(ApiResponse.success("Category created successfully", category));
    }

    // Trả chi tiết một category quản trị.
    @GetMapping("/{categoryId}")
    public ApiResponse<CategoryResponse> get(@PathVariable UUID categoryId) {
        return ApiResponse.success("Category loaded successfully", categoryService.get(categoryId));
    }

    // Cập nhật các trường category được gửi lên.
    @PatchMapping("/{categoryId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<CategoryResponse> update(@PathVariable UUID categoryId, @Valid @RequestBody UpdateCategoryRequest request) {
        return ApiResponse.success("Category updated successfully", categoryService.update(categoryId, request));
    }

    // Xóa category khi chưa có category con hoặc khóa học sử dụng.
    @DeleteMapping("/{categoryId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> delete(@PathVariable UUID categoryId) {
        categoryService.delete(categoryId);
        return ApiResponse.success("Category deleted successfully");
    }
}
