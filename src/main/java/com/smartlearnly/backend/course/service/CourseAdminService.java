package com.smartlearnly.backend.course.service;

import com.smartlearnly.backend.common.api.PageResponse;
import com.smartlearnly.backend.common.audit.AuditLogService;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.course.dto.CourseCreateRequest;
import com.smartlearnly.backend.course.dto.CourseResponse;
import com.smartlearnly.backend.course.dto.CourseUpdateRequest;
import com.smartlearnly.backend.course.entity.Category;
import com.smartlearnly.backend.course.entity.Course;
import com.smartlearnly.backend.course.repository.CategoryRepository;
import com.smartlearnly.backend.course.repository.CourseRepository;
import com.smartlearnly.backend.user.entity.UserAccount;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CourseAdminService {
    private final CourseRepository courseRepository;
    private final CategoryRepository categoryRepository;
    private final CurrentUserService currentUserService;
    private final AuditLogService auditLogService;

    @Transactional
    public CourseResponse createCourse(CourseCreateRequest request) {
        UserAccount admin = currentUserService.requireAdmin();
        Category category = findCategory(request.categoryId());

        Course course = new Course();
        course.setCategory(category);
        course.setTitle(normalizeRequiredText(request.title(), "Course title is required"));
        course.setSlug(resolveCreateSlug(request.title(), request.slug()));
        course.setDescription(normalizeNullable(request.description()));
        course.setPrice(normalizePrice(request.price()));
        course.setStatus(normalizeCourseStatus(request.status(), Course.STATUS_DRAFT));
        course.setAvatarUrl(normalizeNullable(request.avatarUrl()));
        course.setTags(normalizeTags(request.tags()));
        course.setFeatured(Boolean.TRUE.equals(request.featured()));
        course.setCreator(admin);

        Course savedCourse = courseRepository.save(course);
        auditLogService.record(admin.getEmail(), "COURSE_CREATED", "COURSE", savedCourse.getId().toString());
        return CourseDtoMapper.toCourseResponse(savedCourse);
    }

    @Transactional(readOnly = true)
    public PageResponse<CourseResponse> listCourses(int page, int size) {
        currentUserService.requireAdmin();
        Page<Course> coursePage = courseRepository.findAllByDeletedAtIsNull(
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        return new PageResponse<>(
                coursePage.getContent().stream().map(CourseDtoMapper::toCourseResponse).toList(),
                coursePage.getNumber(),
                coursePage.getSize(),
                coursePage.getTotalElements(),
                coursePage.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public CourseResponse getCourse(UUID courseId) {
        currentUserService.requireAdmin();
        return CourseDtoMapper.toCourseResponse(findCourse(courseId));
    }

    @Transactional
    public CourseResponse updateCourse(UUID courseId, CourseUpdateRequest request) {
        UserAccount admin = currentUserService.requireAdmin();
        Course course = findCourse(courseId);

        boolean changed = false;
        if (request.categoryId() != null) {
            course.setCategory(findCategory(request.categoryId()));
            changed = true;
        }
        if (request.title() != null) {
            course.setTitle(normalizeRequiredText(request.title(), "Course title must not be blank"));
            changed = true;
        }
        if (request.slug() != null) {
            String slug = request.slug().trim().toLowerCase(Locale.ROOT);
            if (!slug.equals(course.getSlug())) {
                ensureSlugAvailable(slug, course.getId());
                course.setSlug(slug);
                changed = true;
            }
        }
        if (request.description() != null) {
            course.setDescription(normalizeNullable(request.description()));
            changed = true;
        }
        if (request.price() != null) {
            course.setPrice(normalizePrice(request.price()));
            changed = true;
        }
        if (request.status() != null) {
            course.setStatus(normalizeCourseStatus(request.status(), course.getStatus()));
            changed = true;
        }
        if (request.avatarUrl() != null) {
            course.setAvatarUrl(normalizeNullable(request.avatarUrl()));
            changed = true;
        }
        if (request.tags() != null) {
            course.setTags(normalizeTags(request.tags()));
            changed = true;
        }
        if (request.featured() != null) {
            course.setFeatured(request.featured());
            changed = true;
        }

        if (!changed) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "At least one course field must be provided");
        }

        Course savedCourse = courseRepository.save(course);
        auditLogService.record(admin.getEmail(), "COURSE_UPDATED", "COURSE", savedCourse.getId().toString());
        return CourseDtoMapper.toCourseResponse(savedCourse);
    }

    @Transactional
    public void deleteCourse(UUID courseId) {
        UserAccount admin = currentUserService.requireAdmin();
        Course course = findCourse(courseId);
        course.setStatus(Course.STATUS_ARCHIVED);
        course.setDeletedAt(Instant.now());
        courseRepository.save(course);
        auditLogService.record(admin.getEmail(), "COURSE_DELETED", "COURSE", course.getId().toString());
    }

    private Course findCourse(UUID courseId) {
        return courseRepository.findByIdAndDeletedAtIsNull(courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Course was not found"));
    }

    private Category findCategory(UUID categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Category was not found"));
    }

    private String resolveCreateSlug(String title, String requestedSlug) {
        String slug = normalizeNullable(requestedSlug);
        if (slug != null) {
            slug = slug.toLowerCase(Locale.ROOT);
            ensureSlugAvailable(slug, null);
            return slug;
        }
        return nextAvailableSlug(slugify(title));
    }

    private String nextAvailableSlug(String baseSlug) {
        String candidate = baseSlug;
        int suffix = 2;
        while (courseRepository.existsBySlugAndDeletedAtIsNull(candidate)) {
            candidate = baseSlug + "-" + suffix;
            suffix++;
        }
        return candidate;
    }

    private void ensureSlugAvailable(String slug, UUID currentCourseId) {
        boolean exists = currentCourseId == null
                ? courseRepository.existsBySlugAndDeletedAtIsNull(slug)
                : courseRepository.existsBySlugAndIdNotAndDeletedAtIsNull(slug, currentCourseId);
        if (exists) {
            throw new BusinessException(ErrorCode.CONFLICT, "Course slug already exists");
        }
    }

    private String slugify(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replace('\u0111', 'd')
                .replace('\u0110', 'D')
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (normalized.isBlank()) {
            return "course";
        }
        return normalized.length() <= 160 ? normalized : normalized.substring(0, 160).replaceAll("-+$", "");
    }

    private String normalizeCourseStatus(String value, String defaultStatus) {
        if (value == null || value.isBlank()) {
            return defaultStatus;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (Course.STATUS_DRAFT.equals(normalized)
                || Course.STATUS_PUBLISHED.equals(normalized)
                || Course.STATUS_ARCHIVED.equals(normalized)) {
            return normalized;
        }
        throw new BusinessException(ErrorCode.INVALID_REQUEST, "Course status must be draft, published, or archived");
    }

    private BigDecimal normalizePrice(BigDecimal price) {
        return price == null ? BigDecimal.ZERO : price;
    }

    private String normalizeRequiredText(String value, String message) {
        if (value == null || value.trim().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, message);
        }
        return value.trim();
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String[] normalizeTags(java.util.List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return null;
        }
        Set<String> normalizedTags = new LinkedHashSet<>();
        for (String tag : tags) {
            String normalized = normalizeNullable(tag);
            if (normalized != null) {
                normalizedTags.add(normalized);
            }
        }
        if (normalizedTags.isEmpty()) {
            return null;
        }
        return normalizedTags.toArray(String[]::new);
    }
}
