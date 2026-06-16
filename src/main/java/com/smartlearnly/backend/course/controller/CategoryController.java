package com.smartlearnly.backend.course.controller;

import com.smartlearnly.backend.common.api.ApiResponse;
import com.smartlearnly.backend.course.dto.CategoryResponse;
import com.smartlearnly.backend.course.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Categories", description = "Public course-category browsing endpoints.")
public class CategoryController {
    private final CategoryService categoryService;

    @GetMapping
    @Operation(summary = "List active public categories")
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
