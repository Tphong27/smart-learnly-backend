package com.smartlearnly.backend.course.service;

import com.smartlearnly.backend.common.api.PageResponse;
import com.smartlearnly.backend.common.audit.AuditAction;
import com.smartlearnly.backend.common.audit.AuditDomain;
import com.smartlearnly.backend.common.audit.AuditLogService;
import com.smartlearnly.backend.common.audit.AuditResult;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.classroom.entity.ClassOffering;
import com.smartlearnly.backend.course.dto.CourseResponse;
import com.smartlearnly.backend.course.dto.CreateCourseRequest;
import com.smartlearnly.backend.course.dto.UpdateCourseRequest;
import com.smartlearnly.backend.course.entity.Category;
import com.smartlearnly.backend.course.entity.Course;
import com.smartlearnly.backend.course.entity.CourseStatus;
import com.smartlearnly.backend.course.repository.CategoryRepository;
import com.smartlearnly.backend.course.repository.CourseRepository;
import com.smartlearnly.backend.curriculum.entity.CurriculumScope;
import com.smartlearnly.backend.curriculum.entity.CurriculumStatus;
import com.smartlearnly.backend.curriculum.entity.CurriculumVersion;
import com.smartlearnly.backend.curriculum.repository.CurriculumVersionRepository;
import com.smartlearnly.backend.file.config.StorageProperties;
import com.smartlearnly.backend.user.entity.UserAccount;
import com.smartlearnly.backend.course.service.CourseAccessService;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CourseAdminService {
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_SLUG = Pattern.compile("[^a-z0-9]+");

    private final CourseRepository courseRepository;
    private final CategoryRepository categoryRepository;
    private final CurrentUserService currentUserService;
    private final AuditLogService auditLogService;
    private final StorageProperties storageProperties;
    private final CourseAccessService courseAccessService;
    private final CurriculumVersionRepository curriculumVersionRepository;

    @Transactional(readOnly = true)
    public PageResponse<CourseResponse> list(
            int page,
            int size,
            String keyword,
            String status,
            UUID categoryId,
            String level
    ) {
        CourseStatus resolvedStatus = parseCourseStatus(status, null);
        String resolvedKeyword = normalizeNullable(keyword);
        String resolvedLevel = normalizeNullable(level);
        Specification<Course> filters = buildListFilters(
                resolvedKeyword,
                resolvedStatus,
                categoryId,
                resolvedLevel);

        if (courseAccessService.isCurrentUserTrainer()) {
            UUID trainerId = courseAccessService.getCurrentUserId();
            filters = filters.and(assignedToTrainer(trainerId));
        }

        Page<Course> coursePage = courseRepository.findAll(
                filters,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));

        return new PageResponse<>(
                coursePage.getContent().stream().map(CourseDtoMapper::toCourseResponse).toList(),
                coursePage.getNumber(),
                coursePage.getSize(),
                coursePage.getTotalElements(),
                coursePage.getTotalPages());
    }

    private Specification<Course> buildListFilters(
            String keyword,
            CourseStatus status,
            UUID categoryId,
            String level
    ) {
        Specification<Course> filters = (root, query, criteriaBuilder) ->
                criteriaBuilder.isNull(root.get("deletedAt"));

        if (keyword != null) {
            String pattern = "%" + keyword.toLowerCase(Locale.ROOT) + "%";
            filters = filters.and((root, query, criteriaBuilder) -> criteriaBuilder.or(
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("title")), pattern),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("slug")), pattern),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("shortDescription")), pattern)));
        }
        if (status != null) {
            filters = filters.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(
                            root.get("status").cast(String.class),
                            status.name().toLowerCase(Locale.ROOT)));
        }
        if (categoryId != null) {
            filters = filters.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("category").get("id"), categoryId));
        }
        if (level != null) {
            filters = filters.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(
                            criteriaBuilder.lower(root.get("level")),
                            level.toLowerCase(Locale.ROOT)));
        }
        return filters;
    }

    private Specification<Course> assignedToTrainer(UUID trainerId) {
        return (root, query, criteriaBuilder) -> {
            var assignment = query.subquery(UUID.class);
            var classOffering = assignment.from(ClassOffering.class);
            assignment.select(classOffering.get("id"));
            assignment.where(
                    criteriaBuilder.equal(classOffering.get("courseId"), root.get("id")),
                    criteriaBuilder.equal(classOffering.get("trainerId"), trainerId),
                    criteriaBuilder.isNull(classOffering.get("deletedAt")));
            return criteriaBuilder.exists(assignment);
        };
    }

    @Transactional(readOnly = true)
    public CourseResponse get(UUID courseId) {
        courseAccessService.requireReadableCourse(courseId);

        return CourseDtoMapper.toCourseResponse(findCourse(courseId));
    }

    @Transactional
    public CourseResponse create(CreateCourseRequest request) {
        UserAccount creator = currentUserService.requireAuthenticatedUser();
        Course course = new Course();
        course.setCategory(findCategory(request.categoryId()));
        course.setCreator(creator);
        course.setTitle(normalizeRequired(request.title(), "Course title is required"));
        course.setSlug(resolveCreateSlug(request.slug(), course.getTitle()));
        course.setShortDescription(normalizeNullable(request.shortDescription()));
        course.setDescription(normalizeNullable(request.description()));
        course.setOutcomes(normalizeNullable(request.outcomes()));
        course.setRequirements(normalizeNullable(request.requirements()));
        course.setLanguage(normalizeNullable(request.language()));
        course.setLevel(normalizeNullable(request.level()));
        course.setThumbnailUrl(validateThumbnailUrl(normalizeNullable(request.thumbnailUrl())));
        course.setPrice(request.price() == null ? BigDecimal.ZERO : request.price());
        course.setDiscountedPrice(request.discountedPrice());
        course.setFree(Boolean.TRUE.equals(request.isFree()));
        course.setStatus(parseCourseStatus(request.status(), CourseStatus.DRAFT));
        validatePrices(course.getPrice(), course.getDiscountedPrice(), course.getFree());

        Course saved = courseRepository.save(course);
        if (saved.getStatus() == CourseStatus.PUBLISHED) {
            publishLatestMasterCurriculum(saved, creator);
        }
        auditLogService.record(creator.getEmail(), "COURSE_CREATED", "COURSE", saved.getId().toString());
        return CourseDtoMapper.toCourseResponse(saved);
    }

    @Transactional
    public CourseResponse update(UUID courseId, UpdateCourseRequest request) {
        courseAccessService.requireUpdatableCourse(courseId);

        if (!request.hasAnyField()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "At least one course field must be provided");
        }

        Course course = findCourse(courseId);
        CourseStatus previousStatus = course.getStatus();
        if (request.isCategoryIdProvided()) {
            if (request.getCategoryId() == null) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST, "Category is required");
            }
            course.setCategory(findCategory(request.getCategoryId()));
        }
        if (request.isTitleProvided()) {
            course.setTitle(normalizeRequired(request.getTitle(), "Course title must not be blank"));
        }
        if (request.isSlugProvided()) {
            course.setSlug(resolveUpdateSlug(request.getSlug(), courseId));
        }
        if (request.isShortDescriptionProvided()) {
            course.setShortDescription(normalizeNullable(request.getShortDescription()));
        }
        if (request.isDescriptionProvided()) {
            course.setDescription(normalizeNullable(request.getDescription()));
        }
        if (request.isOutcomesProvided()) {
            course.setOutcomes(normalizeNullable(request.getOutcomes()));
        }
        if (request.isRequirementsProvided()) {
            course.setRequirements(normalizeNullable(request.getRequirements()));
        }
        if (request.isLanguageProvided()) {
            course.setLanguage(normalizeNullable(request.getLanguage()));
        }
        if (request.isLevelProvided()) {
            course.setLevel(normalizeNullable(request.getLevel()));
        }
        if (request.isThumbnailUrlProvided()) {
            course.setThumbnailUrl(validateThumbnailUrl(normalizeNullable(request.getThumbnailUrl())));
        }
        if (request.isPriceProvided()) {
            if (request.getPrice() == null) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST, "Course price is required when provided");
            }
            course.setPrice(request.getPrice());
        }
        if (request.isDiscountedPriceProvided()) {
            course.setDiscountedPrice(request.getDiscountedPrice());
        }
        if (request.isFreeProvided()) {
            course.setFree(Boolean.TRUE.equals(request.getFree()));
        }
        if (request.isStatusProvided()) {
            course.setStatus(parseCourseStatus(request.getStatus(), course.getStatus()));
        }
        validatePrices(course.getPrice(), course.getDiscountedPrice(), course.getFree());

        Course saved = courseRepository.save(course);
        if (saved.getStatus() == CourseStatus.PUBLISHED) {
            publishLatestMasterCurriculum(saved, currentUserService.requireAuthenticatedUser());
        }
        if (previousStatus != saved.getStatus()) {
            AuditAction action = saved.getStatus() == CourseStatus.PUBLISHED
                    ? AuditAction.COURSE_PUBLISHED
                    : saved.getStatus() == CourseStatus.INACTIVE
                            ? AuditAction.COURSE_DEACTIVATED
                            : AuditAction.COURSE_UPDATED;
            UserAccount actor = currentUserService.requireAuthenticatedUser();
            auditLogService.recordUser(
                    actor, action, AuditDomain.COURSE, AuditResult.SUCCESS,
                    "COURSE", saved.getId().toString(), "Course status was changed",
                    java.util.Map.of("status", previousStatus.name()),
                    java.util.Map.of("status", saved.getStatus().name()),
                    java.util.Map.of("courseTitle", saved.getTitle()));
        } else {
            audit("COURSE_UPDATED", saved.getId());
        }
        return CourseDtoMapper.toCourseResponse(saved);
    }

    /**
     * Keeps the learner-facing curriculum in sync with the course status. The admin UI exposes one
     * publish action for a course, so leaving its latest MASTER version in DRAFT makes the course
     * visible while its lessons disappear from Learning Workspace.
     */
    private void publishLatestMasterCurriculum(Course course, UserAccount actor) {
        CurriculumVersion latest = curriculumVersionRepository
                .findFirstByCourseIdAndScopeOrderByVersionNumberDescCreatedAtDesc(
                        course.getId(), CurriculumScope.MASTER)
                .orElseGet(() -> createInitialMasterCurriculum(course, actor));

        if (latest.getStatus() == CurriculumStatus.PUBLISHED) {
            return;
        }

        Instant now = Instant.now();
        curriculumVersionRepository
                .findFirstByCourseIdAndScopeAndStatusOrderByVersionNumberDescCreatedAtDesc(
                        course.getId(), CurriculumScope.MASTER, CurriculumStatus.PUBLISHED)
                .filter(published -> !published.getId().equals(latest.getId()))
                .ifPresent(published -> {
                    published.setStatus(CurriculumStatus.ARCHIVED);
                    published.setArchivedAt(now);
                    curriculumVersionRepository.save(published);
                    curriculumVersionRepository.flush();
                });

        latest.setStatus(CurriculumStatus.PUBLISHED);
        latest.setPublishedAt(now);
        latest.setArchivedAt(null);
        latest.setTitle(course.getTitle());
        curriculumVersionRepository.save(latest);
    }

    private CurriculumVersion createInitialMasterCurriculum(Course course, UserAccount actor) {
        CurriculumVersion version = new CurriculumVersion();
        version.setCourseId(course.getId());
        version.setScope(CurriculumScope.MASTER);
        version.setStatus(CurriculumStatus.DRAFT);
        version.setVersionNumber(curriculumVersionRepository.findMaxMasterVersionNumber(
                course.getId(), CurriculumScope.MASTER) + 1);
        version.setTitle(course.getTitle());
        version.setCreatedBy(actor.getId());
        return curriculumVersionRepository.save(version);
    }

    @Transactional
    public void delete(UUID courseId) {
        Course course = findCourse(courseId);
        course.setStatus(CourseStatus.INACTIVE);
        course.setDeletedAt(Instant.now());
        courseRepository.save(course);
        audit("COURSE_DELETED", course.getId());
    }

    private Course findCourse(UUID courseId) {
        return courseRepository.findByIdAndDeletedAtIsNull(courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Course was not found"));
    }

    private Category findCategory(UUID categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
    }

    private String resolveCreateSlug(String requestedSlug, String title) {
        String slug = slugify(requestedSlug == null || requestedSlug.isBlank() ? title : requestedSlug);
        if (courseRepository.existsBySlugIgnoreCaseAndDeletedAtIsNull(slug)) {
            throw new BusinessException(ErrorCode.CONFLICT, "Course slug already exists");
        }
        return slug;
    }

    private String resolveUpdateSlug(String requestedSlug, UUID courseId) {
        String slug = slugify(requestedSlug);
        if (courseRepository.existsBySlugIgnoreCaseAndIdNotAndDeletedAtIsNull(slug, courseId)) {
            throw new BusinessException(ErrorCode.CONFLICT, "Course slug already exists");
        }
        return slug;
    }

    private String slugify(String value) {
        String normalized = normalizeRequired(value, "Course slug must not be blank")
                .replace('đ', 'd')
                .replace('Đ', 'D');
        normalized = DIACRITICS.matcher(Normalizer.normalize(normalized, Normalizer.Form.NFD)).replaceAll("");
        String slug = NON_SLUG.matcher(normalized.toLowerCase(Locale.ROOT)).replaceAll("-");
        slug = slug.replaceAll("^-+|-+$", "");
        if (slug.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Course slug must contain letters or numbers");
        }
        if (slug.length() > 280) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Course slug must not exceed 280 characters");
        }
        return slug;
    }

    private CourseStatus parseCourseStatus(String value, CourseStatus defaultStatus) {
        if (value == null || value.isBlank()) {
            return defaultStatus;
        }
        try {
            return CourseStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "Course status must be draft, published, or inactive");
        }
    }

    private void validatePrices(BigDecimal price, BigDecimal discountedPrice, Boolean free) {
        BigDecimal resolvedPrice = price == null ? BigDecimal.ZERO : price;
        if (resolvedPrice.signum() < 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Course price must be greater than or equal to 0");
        }
        if (Boolean.TRUE.equals(free) && resolvedPrice.compareTo(BigDecimal.ZERO) != 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Free courses must have price 0");
        }
        if (discountedPrice != null) {
            if (discountedPrice.signum() < 0 || discountedPrice.compareTo(resolvedPrice) > 0) {
                throw new BusinessException(
                        ErrorCode.INVALID_REQUEST,
                        "Discounted price must be between 0 and the course price");
            }
        }
    }

    private String validateThumbnailUrl(String thumbnailUrl) {
        if (thumbnailUrl == null) {
            return null;
        }
        if ("r2".equalsIgnoreCase(normalizeNullable(storageProperties.getProvider()))) {
            String expectedPrefix = normalizeNullable(storageProperties.getR2CourseThumbnailPublicUrl());
            if (expectedPrefix == null) {
                expectedPrefix = normalizeNullable(storageProperties.getR2PublicUrl());
            }
            if (expectedPrefix == null) {
                return thumbnailUrl;
            }
            return validateUrlPrefix(thumbnailUrl, expectedPrefix,
                    "Course thumbnail URL must come from the configured R2 course thumbnail bucket");
        }
        String supabaseUrl = normalizeNullable(storageProperties.getSupabaseUrl());
        if (supabaseUrl == null) {
            return thumbnailUrl;
        }
        String expectedPrefix = supabaseUrl.replaceAll("/+$", "")
                + "/storage/v1/object/public/"
                + storageProperties.getCourseThumbnailBucket()
                + "/";
        return validateUrlPrefix(thumbnailUrl, expectedPrefix,
                "Course thumbnail URL must come from the configured course thumbnail storage bucket");
    }

    private String validateUrlPrefix(String url, String expectedPrefix, String message) {
        String normalizedPrefix = expectedPrefix.replaceAll("/+$", "") + "/";
        if (!url.startsWith(normalizedPrefix)) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    message);
        }
        return url;
    }

    private void audit(String action, UUID courseId) {
        UserAccount actor = currentUserService.requireAuthenticatedUser();
        auditLogService.record(actor.getEmail(), action, "COURSE", courseId.toString());
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
