package com.smartlearnly.backend.course.service;

import com.smartlearnly.backend.common.audit.AuditLogService;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.AuthenticatedUserResolver;
import com.smartlearnly.backend.common.security.CurrentUser;
import com.smartlearnly.backend.course.dto.CategoryResponse;
import com.smartlearnly.backend.course.dto.CreateCategoryRequest;
import com.smartlearnly.backend.course.dto.UpdateCategoryRequest;
import com.smartlearnly.backend.course.entity.Category;
import com.smartlearnly.backend.course.repository.CategoryRepository;
import com.smartlearnly.backend.course.repository.CourseRepository;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CategoryService {
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_SLUG = Pattern.compile("[^a-z0-9]+");

    private final CategoryRepository categoryRepository;
    private final CourseRepository courseRepository;
    private final AuditLogService auditLogService;
    private final AuthenticatedUserResolver authenticatedUserResolver;

    @Transactional(readOnly = true)
    public List<CategoryResponse> list(String keyword, Boolean active, UUID parentId) {
        return categoryRepository.search(normalizeNullable(keyword), active, parentId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> listPublic(String keyword, UUID parentId) {
        return categoryRepository.searchPublicActive(normalizeNullable(keyword), parentId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CategoryResponse get(UUID categoryId) {
        return toResponse(findCategory(categoryId));
    }

    @Transactional
    public CategoryResponse create(CreateCategoryRequest request) {
        Category category = new Category();
        category.setName(normalizeRequired(request.name(), "Category name is required"));
        category.setSlug(resolveCreateSlug(request.slug(), category.getName()));
        category.setDescription(normalizeNullable(request.description()));
        category.setParent(resolveParent(request.parentId(), null));
        category.setActive(request.isActive() == null ? true : request.isActive());
        category.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());

        Category saved = categoryRepository.save(category);
        audit("CATEGORY_CREATED", saved.getId());
        return toResponse(saved);
    }

    @Transactional
    public CategoryResponse update(UUID categoryId, UpdateCategoryRequest request) {
        if (!request.hasAnyField()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "At least one category field must be provided");
        }

        Category category = findCategory(categoryId);
        if (request.getName() != null) {
            category.setName(normalizeRequired(request.getName(), "Category name must not be blank"));
        }
        if (request.getSlug() != null) {
            category.setSlug(resolveUpdateSlug(request.getSlug(), categoryId));
        }
        if (request.getDescription() != null) {
            category.setDescription(normalizeNullable(request.getDescription()));
        }
        if (request.isParentIdProvided()) {
            category.setParent(resolveParent(request.getParentId(), categoryId));
        }
        if (request.getIsActive() != null) {
            category.setActive(request.getIsActive());
        }
        if (request.getSortOrder() != null) {
            category.setSortOrder(request.getSortOrder());
        }

        Category saved = categoryRepository.save(category);
        audit("CATEGORY_UPDATED", saved.getId());
        return toResponse(saved);
    }

    @Transactional
    public void delete(UUID categoryId) {
        Category category = findCategory(categoryId);
        if (categoryRepository.existsByParentId(categoryId)) {
            throw new BusinessException(ErrorCode.CATEGORY_IN_USE, "Category has child categories");
        }
        if (courseRepository.existsByCategoryId(categoryId)) {
            throw new BusinessException(ErrorCode.CATEGORY_IN_USE, "Category is assigned to one or more courses");
        }
        categoryRepository.delete(category);
        audit("CATEGORY_DELETED", categoryId);
    }

    private Category resolveParent(UUID parentId, UUID categoryId) {
        if (parentId == null) {
            return null;
        }
        if (parentId.equals(categoryId)) {
            throw new BusinessException(ErrorCode.CATEGORY_HIERARCHY_INVALID, "Category cannot be its own parent");
        }
        Category parent = findCategory(parentId);
        if (parent.getParent() != null) {
            throw new BusinessException(
                    ErrorCode.CATEGORY_HIERARCHY_INVALID,
                    "Categories support a maximum of two hierarchy levels"
            );
        }
        if (categoryId != null && categoryRepository.existsByParentId(categoryId)) {
            throw new BusinessException(
                    ErrorCode.CATEGORY_HIERARCHY_INVALID,
                    "A category with children cannot become a child category"
            );
        }
        return parent;
    }

    private String resolveCreateSlug(String requestedSlug, String name) {
        String slug = slugify(requestedSlug == null || requestedSlug.isBlank() ? name : requestedSlug);
        if (categoryRepository.existsBySlugIgnoreCase(slug)) {
            throw new BusinessException(ErrorCode.CATEGORY_SLUG_CONFLICT);
        }
        return slug;
    }

    private String resolveUpdateSlug(String requestedSlug, UUID categoryId) {
        String slug = slugify(requestedSlug);
        if (categoryRepository.existsBySlugIgnoreCaseAndIdNot(slug, categoryId)) {
            throw new BusinessException(ErrorCode.CATEGORY_SLUG_CONFLICT);
        }
        return slug;
    }

    private String slugify(String value) {
        String normalized = normalizeRequired(value, "Category slug must not be blank")
                .replace('đ', 'd')
                .replace('Đ', 'D');
        normalized = DIACRITICS.matcher(Normalizer.normalize(normalized, Normalizer.Form.NFD)).replaceAll("");
        String slug = NON_SLUG.matcher(normalized.toLowerCase(Locale.ROOT)).replaceAll("-");
        slug = slug.replaceAll("^-+|-+$", "");
        if (slug.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Category slug must contain letters or numbers");
        }
        if (slug.length() > 180) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Category slug must not exceed 180 characters");
        }
        return slug;
    }

    private Category findCategory(UUID categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
    }

    private CategoryResponse toResponse(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getSlug(),
                category.getDescription(),
                category.getParent() == null ? null : category.getParent().getId(),
                Boolean.TRUE.equals(category.getActive()),
                category.getSortOrder(),
                category.getCreatedAt(),
                category.getUpdatedAt()
        );
    }

    private void audit(String action, UUID categoryId) {
        String actor = authenticatedUserResolver.resolve()
                .map(CurrentUser::email)
                .filter(email -> !email.isBlank())
                .orElse("unknown");
        auditLogService.record(actor, action, "CATEGORY", categoryId.toString());
    }

    private String normalizeRequired(String value, String message) {
        String normalized = normalizeNullable(value);
        if (normalized == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, message);
        }
        return normalized;
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
