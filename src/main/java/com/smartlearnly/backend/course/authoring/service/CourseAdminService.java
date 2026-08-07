package com.smartlearnly.backend.course.authoring.service;

import com.smartlearnly.backend.common.api.PageResponse;
import com.smartlearnly.backend.common.audit.AuditAction;
import com.smartlearnly.backend.common.audit.AuditDomain;
import com.smartlearnly.backend.common.audit.AuditLogService;
import com.smartlearnly.backend.common.audit.AuditResult;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.course.access.service.CourseAccessService;
import com.smartlearnly.backend.classroom.entity.ClassOffering;
import com.smartlearnly.backend.course.authoring.dto.CourseResponse;
import com.smartlearnly.backend.course.authoring.dto.CreateCourseRequest;
import com.smartlearnly.backend.course.authoring.dto.UpdateCourseRequest;
import com.smartlearnly.backend.course.authoring.mapper.CourseDtoMapper;
import com.smartlearnly.backend.course.entity.Category;
import com.smartlearnly.backend.course.entity.Course;
import com.smartlearnly.backend.course.entity.CourseStatus;
import com.smartlearnly.backend.course.repository.CategoryRepository;
import com.smartlearnly.backend.course.repository.CourseRepository;
import com.smartlearnly.backend.curriculum.entity.CurriculumScope;
import com.smartlearnly.backend.curriculum.entity.CurriculumStatus;
import com.smartlearnly.backend.curriculum.entity.CurriculumVersion;
import com.smartlearnly.backend.curriculum.repository.CurriculumVersionRepository;
import com.smartlearnly.backend.enrollment.repository.CourseEnrollmentRepository;
import com.smartlearnly.backend.file.config.StorageProperties;
import com.smartlearnly.backend.notification.dto.NotificationCreateCommand;
import com.smartlearnly.backend.notification.entity.NotificationType;
import com.smartlearnly.backend.notification.service.NotificationPayloads;
import com.smartlearnly.backend.notification.service.NotificationService;
import com.smartlearnly.backend.user.entity.UserAccount;
import com.smartlearnly.backend.user.repository.UserRepository;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;
    private final AuditLogService auditLogService;
    private final StorageProperties storageProperties;
    private final CourseAccessService courseAccessService;
    private final CurriculumVersionRepository curriculumVersionRepository;
    private final CourseEnrollmentRepository courseEnrollmentRepository;
    private NotificationService notificationService;

    // Gắn dịch vụ thông báo khi module notification được bật trong Spring context.
    @Autowired(required = false)
    void setNotificationService(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    // Liệt kê khóa học theo phạm vi vai trò, phân công và bộ lọc trước khi phân trang.
    @Transactional(readOnly = true)
    public PageResponse<CourseResponse> list(
            int page,
            int size,
            String keyword,
            String status,
            UUID categoryId,
            String level) {
        CourseStatus resolvedStatus = parseCourseStatus(status, null);
        String resolvedKeyword = normalizeNullable(keyword);
        String resolvedLevel = normalizeNullable(level);
        Specification<Course> filters = buildListFilters(
                resolvedKeyword,
                resolvedStatus,
                categoryId,
                resolvedLevel);

        if (!courseAccessService.isCurrentUserCourseManager()) {
            UUID currentUserId = courseAccessService.getCurrentUserId();

            if (courseAccessService.isCurrentUserSme()) {
                filters = filters.and(assignedToSme(currentUserId));
            } else if (courseAccessService.isCurrentUserTrainer()) {
                filters = filters.and(assignedToTrainer(currentUserId));
            }
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

    // Kết hợp điều kiện tìm kiếm metadata với phạm vi khóa học người dùng được quản lý.
    private Specification<Course> buildListFilters(
            String keyword,
            CourseStatus status,
            UUID categoryId,
            String level) {
        Specification<Course> filters = (root, query, criteriaBuilder) -> criteriaBuilder.isNull(root.get("deletedAt"));

        if (keyword != null) {
            String pattern = "%" + keyword.toLowerCase(Locale.ROOT) + "%";
            filters = filters.and((root, query, criteriaBuilder) -> criteriaBuilder.or(
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("title")), pattern),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("slug")), pattern),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("shortDescription")), pattern)));
        }
        if (status != null) {
            filters = filters.and((root, query, criteriaBuilder) -> criteriaBuilder.equal(
                    root.get("status").cast(String.class),
                    status.name().toLowerCase(Locale.ROOT)));
        }
        if (categoryId != null) {
            filters = filters.and((root, query, criteriaBuilder) -> criteriaBuilder
                    .equal(root.get("category").get("id"), categoryId));
        }
        if (level != null) {
            filters = filters.and((root, query, criteriaBuilder) -> criteriaBuilder.equal(
                    criteriaBuilder.lower(root.get("level")),
                    level.toLowerCase(Locale.ROOT)));
        }
        return filters;
    }

    // Tạo điều kiện chỉ lấy khóa học được phân công cho SME hiện tại.
    private Specification<Course> assignedToSme(UUID smeId) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(
                root.get("assignedSme").get("id"),
                smeId);
    }

    // Tạo điều kiện chỉ lấy khóa học có lớp được phân công cho giảng viên hiện tại.
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

    // Lấy chi tiết khóa học sau khi kiểm tra quyền đọc theo phân công.
    @Transactional(readOnly = true)
    public CourseResponse get(UUID courseId) {
        courseAccessService.requireReadableCourse(courseId);

        return CourseDtoMapper.toCourseResponse(findCourse(courseId));
    }

    // Tạo khóa học nháp với slug, giá, ảnh và người tạo đã được kiểm tra hợp lệ.
    @Transactional
    public CourseResponse create(CreateCourseRequest request) {
        courseAccessService.requireCourseManager();
        UserAccount creator = currentUserService.requireAuthenticatedUser();
        Course course = new Course();
        course.setCategory(findCategory(request.categoryId()));
        course.setCreator(creator);
        course.setAssignedSme(findAssignedSme(request.assignedSmeId()));
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
        course.setStatus(CourseStatus.DRAFT);
        validatePrices(course.getPrice(), course.getDiscountedPrice(), course.getFree());

        Course saved = courseRepository.save(course);
        findOrCreateLatestMasterCurriculum(saved, creator);
        auditLogService.record(creator.getEmail(), "COURSE_CREATED", "COURSE", saved.getId().toString());
        return CourseDtoMapper.toCourseResponse(saved);
    }

    // Áp dụng các trường PATCH được cung cấp và đồng bộ trạng thái xuất bản khi cần.
    @Transactional
    public CourseResponse update(UUID courseId, UpdateCourseRequest request) {
        if (courseAccessService.isCurrentUserSme()) {
            throw new BusinessException(
                    ErrorCode.FORBIDDEN,
                    "SME can view course details but cannot update them");
        }
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
        if (request.isAssignedSmeIdProvided()) {
            courseAccessService.requireCourseManager();
            course.setAssignedSme(findAssignedSme(request.getAssignedSmeId()));
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
        UserAccount actor = currentUserService.requireAuthenticatedUser();
        if (saved.getStatus() == CourseStatus.PUBLISHED) {
            publishLatestMasterCurriculum(saved, actor);
        }
        if (previousStatus != saved.getStatus()) {
            AuditAction action = saved.getStatus() == CourseStatus.PUBLISHED
                    ? AuditAction.COURSE_PUBLISHED
                    : saved.getStatus() == CourseStatus.INACTIVE
                            ? AuditAction.COURSE_DEACTIVATED
                            : AuditAction.COURSE_UPDATED;
            auditLogService.recordUser(
                    actor, action, AuditDomain.COURSE, AuditResult.SUCCESS,
                    "COURSE", saved.getId().toString(), "Course status was changed",
                    java.util.Map.of("status", previousStatus.name()),
                    java.util.Map.of("status", saved.getStatus().name()),
                    java.util.Map.of("courseTitle", saved.getTitle()));
        } else {
            auditLogService.record(actor.getEmail(), "COURSE_UPDATED", "COURSE", saved.getId().toString());
        }
        emitCourseNotificationIfNeeded(saved, previousStatus, request, actor.getId());
        return CourseDtoMapper.toCourseResponse(saved);
    }

    // Xuất bản chương trình master mới nhất cùng lúc với khóa học để người học luôn thấy nội dung.
    private void publishLatestMasterCurriculum(Course course, UserAccount actor) {
        CurriculumVersion latest = findOrCreateLatestMasterCurriculum(course, actor);
        publishMasterCurriculum(course, latest);
    }

    // Lấy chương trình master mới nhất hoặc tạo phiên bản ban đầu khi khóa học chưa có nội dung.
    private CurriculumVersion findOrCreateLatestMasterCurriculum(Course course, UserAccount actor) {
        return curriculumVersionRepository
                .findFirstByCourseIdAndScopeOrderByVersionNumberDescCreatedAtDesc(
                        course.getId(), CurriculumScope.MASTER)
                .orElseGet(() -> createInitialMasterCurriculum(course, actor));
    }

    // Chuyển phiên bản master hiện tại sang trạng thái xuất bản và lưu thời điểm công khai.
    private void publishMasterCurriculum(Course course, CurriculumVersion latest) {
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

    // Khởi tạo chương trình master nháp đầu tiên cho khóa học chưa có curriculum.
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

    // Lưu trữ mềm khóa học và thông báo cho học viên đang ghi danh.
    @Transactional
    public void delete(UUID courseId) {
        courseAccessService.requireCourseManager();
        Course course = findCourse(courseId);
        UserAccount actor = currentUserService.requireAuthenticatedUser();
        course.setStatus(CourseStatus.INACTIVE);
        course.setDeletedAt(Instant.now());
        courseRepository.save(course);
        auditLogService.record(actor.getEmail(), "COURSE_DELETED", "COURSE", course.getId().toString());
        emitCourseNotificationToLearners(
                course,
                "Course archived",
                course.getTitle() + " is no longer available.",
                "deleted",
                actor.getId());
    }

    // Chỉ phát thông báo khi metadata công khai của khóa học đang mở có thay đổi.
    private void emitCourseNotificationIfNeeded(
            Course course,
            CourseStatus previousStatus,
            UpdateCourseRequest request,
            UUID actorId) {
        if (notificationService == null || course == null || course.getId() == null) {
            return;
        }
        if (previousStatus != course.getStatus()) {
            if (course.getStatus() == CourseStatus.PUBLISHED) {
                emitCourseNotificationToLearners(
                        course,
                        "Course published",
                        course.getTitle() + " is now available.",
                        "published",
                        actorId);
            } else if (course.getStatus() == CourseStatus.INACTIVE) {
                emitCourseNotificationToLearners(
                        course,
                        "Course archived",
                        course.getTitle() + " is no longer available.",
                        "inactive",
                        actorId);
            }
            return;
        }
        if (course.getStatus() == CourseStatus.PUBLISHED && hasPublicCourseDetailChange(request)) {
            emitCourseNotificationToLearners(
                    course,
                    "Course updated",
                    course.getTitle() + " has been updated.",
                    "updated",
                    actorId);
        }
    }

    // Xác định yêu cầu PATCH có chứa trường công khai cần thông báo hay không.
    private boolean hasPublicCourseDetailChange(UpdateCourseRequest request) {
        return request.isCategoryIdProvided()
                || request.isTitleProvided()
                || request.isSlugProvided()
                || request.isShortDescriptionProvided()
                || request.isDescriptionProvided()
                || request.isOutcomesProvided()
                || request.isRequirementsProvided()
                || request.isLanguageProvided()
                || request.isLevelProvided()
                || request.isThumbnailUrlProvided()
                || request.isPriceProvided()
                || request.isDiscountedPriceProvided()
                || request.isFreeProvided();
    }

    // Gửi thông báo khóa học theo lô cho các học viên còn ghi danh hợp lệ.
    private void emitCourseNotificationToLearners(
            Course course,
            String title,
            String body,
            String eventSuffix,
            UUID actorId) {
        if (notificationService == null || course == null || course.getId() == null) {
            return;
        }
        String eventKeySuffix = Long.toString(Instant.now().toEpochMilli());
        for (UUID studentId : courseEnrollmentRepository.findActiveOrCompletedStudentIdsByCourseId(course.getId())) {
            notificationService.emit(new NotificationCreateCommand(
                    studentId,
                    NotificationType.COURSE,
                    title,
                    body,
                    "COURSE",
                    course.getId(),
                    "/courses/" + course.getId(),
                    actorId,
                    "course:" + course.getId() + ":" + eventSuffix + ":" + eventKeySuffix,
                    NotificationPayloads.of(
                            "courseId", course.getId(),
                            "status", course.getStatus() == null ? null : course.getStatus().name(),
                            "title", course.getTitle())));
        }
    }

    // Tìm khóa học chưa bị xóa hoặc báo lỗi không tồn tại thống nhất.
    private Course findCourse(UUID courseId) {
        return courseRepository.findByIdAndDeletedAtIsNull(courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Course was not found"));
    }

    // Kiểm tra tài khoản SME được phân công tồn tại và có đúng vai trò.
    private UserAccount findAssignedSme(UUID assignedSmeId) {
        if (assignedSmeId == null) {
            return null;
        }

        return userRepository.findActiveUserByIdAndRole(
                assignedSmeId,
                "SME",
                "active")
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.INVALID_REQUEST,
                        "Assigned SME must be an active SME account"));
    }

    // Tìm danh mục còn hoạt động cho thao tác tạo hoặc cập nhật khóa học.
    private Category findCategory(UUID categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
    }

    // Sinh slug từ giá trị yêu cầu hoặc tiêu đề và đảm bảo không trùng khi tạo mới.
    private String resolveCreateSlug(String requestedSlug, String title) {
        String slug = slugify(requestedSlug == null || requestedSlug.isBlank() ? title : requestedSlug);
        if (courseRepository.existsBySlugIgnoreCaseAndDeletedAtIsNull(slug)) {
            throw new BusinessException(ErrorCode.CONFLICT, "Course slug already exists");
        }
        return slug;
    }

    // Chuẩn hóa slug cập nhật và cho phép giữ slug hiện tại của chính khóa học.
    private String resolveUpdateSlug(String requestedSlug, UUID courseId) {
        String slug = slugify(requestedSlug);
        if (courseRepository.existsBySlugIgnoreCaseAndIdNotAndDeletedAtIsNull(slug, courseId)) {
            throw new BusinessException(ErrorCode.CONFLICT, "Course slug already exists");
        }
        return slug;
    }

    // Chuyển chuỗi có dấu và ký tự đặc biệt thành slug URL ổn định.
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

    // Chuyển trạng thái văn bản sang enum và từ chối giá trị ngoài hợp đồng.
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

    // Kiểm tra tính nhất quán giữa khóa học miễn phí, giá gốc và giá giảm.
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

    // Kiểm tra URL ảnh đại diện thuộc đúng vùng lưu trữ đã cấu hình.
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

    // Kiểm tra URL có bắt đầu bằng tiền tố public hợp lệ của vùng lưu trữ.
    private String validateUrlPrefix(String url, String expectedPrefix, String message) {
        String normalizedPrefix = expectedPrefix.replaceAll("/+$", "") + "/";
        if (!url.startsWith(normalizedPrefix)) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    message);
        }
        return url;
    }

    // Chuẩn hóa chuỗi bắt buộc và báo lỗi khi không còn nội dung sau khi cắt khoảng trắng.
    private String normalizeRequired(String value, String message) {
        String normalized = normalizeNullable(value);
        if (normalized == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, message);
        }
        return normalized;
    }

    // Chuẩn hóa chuỗi tùy chọn, biến giá trị rỗng thành null.
    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
