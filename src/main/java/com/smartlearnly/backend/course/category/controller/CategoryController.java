package com.smartlearnly.backend.course.category.controller;

import com.smartlearnly.backend.common.api.ApiResponse;
import com.smartlearnly.backend.course.category.dto.CategoryResponse;
import com.smartlearnly.backend.course.category.service.CategoryService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/categories")
public class CategoryController {
    private final CategoryService categoryService;

    // Trả category đang hoạt động cho menu và bộ lọc catalog công khai.
    @GetMapping
    public ApiResponse<List<CategoryResponse>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UUID parentId
    ) {
        return ApiResponse.success(
                "Categories loaded successfully",
                categoryService.listPublic(keyword, parentId)
        );
    }
}
