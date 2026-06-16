package com.smartlearnly.backend.course.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.common.audit.AuditLogService;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.AuthenticatedUserResolver;
import com.smartlearnly.backend.course.dto.CategoryResponse;
import com.smartlearnly.backend.course.dto.CreateCategoryRequest;
import com.smartlearnly.backend.course.dto.UpdateCategoryRequest;
import com.smartlearnly.backend.course.entity.Category;
import com.smartlearnly.backend.course.repository.CategoryRepository;
import com.smartlearnly.backend.course.repository.CourseRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private CourseRepository courseRepository;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private AuthenticatedUserResolver authenticatedUserResolver;

    private CategoryService categoryService;

    @BeforeEach
    void setUp() {
        categoryService = new CategoryService(
                categoryRepository,
                courseRepository,
                auditLogService,
                authenticatedUserResolver
        );
    }

    @Test
    void listPublicShouldUsePublicActiveCategoryQuery() {
        UUID parentId = UUID.randomUUID();
        Category category = category(UUID.randomUUID(), "Programming", "programming", null);
        when(categoryRepository.searchPublicActive("program", parentId)).thenReturn(List.of(category));

        List<CategoryResponse> response = categoryService.listPublic(" program ", parentId);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).slug()).isEqualTo("programming");
        verify(categoryRepository).searchPublicActive("program", parentId);
    }

    @Test
    void createShouldGenerateVietnameseSafeSlugAndDefaults() {
        when(categoryRepository.existsBySlugIgnoreCase("data-ai")).thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> {
            Category category = invocation.getArgument(0);
            category.setId(UUID.randomUUID());
            category.setCreatedAt(Instant.now());
            category.setUpdatedAt(Instant.now());
            return category;
        });

        CategoryResponse response = categoryService.create(
                new CreateCategoryRequest("  Data & AI  ", null, null, null, null, null)
        );

        assertThat(response.name()).isEqualTo("Data & AI");
        assertThat(response.slug()).isEqualTo("data-ai");
        assertThat(response.isActive()).isTrue();
        assertThat(response.sortOrder()).isZero();
    }

    @Test
    void updateNameShouldKeepStableSlug() {
        UUID categoryId = UUID.randomUUID();
        Category category = category(categoryId, "Cloud", "cloud", null);
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(categoryRepository.save(category)).thenReturn(category);

        UpdateCategoryRequest request = new UpdateCategoryRequest();
        request.setName("Cloud Computing");
        CategoryResponse response = categoryService.update(categoryId, request);

        assertThat(response.name()).isEqualTo("Cloud Computing");
        assertThat(response.slug()).isEqualTo("cloud");
        verify(categoryRepository, never()).existsBySlugIgnoreCaseAndIdNot(any(), any());
    }

    @Test
    void createShouldRejectThirdHierarchyLevel() {
        Category root = category(UUID.randomUUID(), "Root", "root", null);
        Category child = category(UUID.randomUUID(), "Child", "child", root);
        when(categoryRepository.findById(child.getId())).thenReturn(Optional.of(child));

        assertThatThrownBy(() -> categoryService.create(
                new CreateCategoryRequest("Third", null, null, child.getId(), true, 0)
        ))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.CATEGORY_HIERARCHY_INVALID));
    }

    @Test
    void deleteShouldRejectCategoryWithChildren() {
        UUID categoryId = UUID.randomUUID();
        Category category = category(categoryId, "Cloud", "cloud", null);
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(categoryRepository.existsByParentId(categoryId)).thenReturn(true);

        assertThatThrownBy(() -> categoryService.delete(categoryId))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.CATEGORY_IN_USE));
        verify(categoryRepository, never()).delete(any());
    }

    @Test
    void deleteShouldRejectCategoryUsedByCourse() {
        UUID categoryId = UUID.randomUUID();
        Category category = category(categoryId, "Cloud", "cloud", null);
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(categoryRepository.existsByParentId(categoryId)).thenReturn(false);
        when(courseRepository.existsByCategoryId(categoryId)).thenReturn(true);

        assertThatThrownBy(() -> categoryService.delete(categoryId))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.CATEGORY_IN_USE));
    }

    private Category category(UUID id, String name, String slug, Category parent) {
        Category category = new Category();
        category.setId(id);
        category.setName(name);
        category.setSlug(slug);
        category.setParent(parent);
        category.setActive(true);
        category.setSortOrder(0);
        category.setCreatedAt(Instant.now());
        category.setUpdatedAt(Instant.now());
        return category;
    }
}
