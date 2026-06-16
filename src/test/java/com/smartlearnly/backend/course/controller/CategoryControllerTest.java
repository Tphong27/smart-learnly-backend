package com.smartlearnly.backend.course.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.common.api.ApiResponse;
import com.smartlearnly.backend.course.dto.CategoryResponse;
import com.smartlearnly.backend.course.service.CategoryService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CategoryControllerTest {
    @Mock
    private CategoryService categoryService;

    @Test
    void listShouldReturnOnlyActivePublicCategories() {
        CategoryController controller = new CategoryController(categoryService);
        UUID parentId = UUID.randomUUID();
        List<CategoryResponse> categories = List.of(new CategoryResponse(
                UUID.randomUUID(),
                "Programming",
                "programming",
                null,
                parentId,
                true,
                1,
                Instant.now(),
                Instant.now()
        ));
        when(categoryService.listPublic("program", parentId)).thenReturn(categories);

        ApiResponse<List<CategoryResponse>> response = controller.list("program", parentId);

        assertThat(response.success()).isTrue();
        assertThat(response.data()).isEqualTo(categories);
        verify(categoryService).listPublic("program", parentId);
    }
}
