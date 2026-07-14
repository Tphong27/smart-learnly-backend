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
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.course.dto.CourseResponse;
import com.smartlearnly.backend.course.dto.CreateCourseRequest;
import com.smartlearnly.backend.course.entity.Category;
import com.smartlearnly.backend.course.entity.Course;
import com.smartlearnly.backend.course.entity.CourseStatus;
import com.smartlearnly.backend.course.repository.CategoryRepository;
import com.smartlearnly.backend.course.repository.CourseRepository;
import com.smartlearnly.backend.file.config.StorageProperties;
import com.smartlearnly.backend.user.entity.UserAccount;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class CourseAdminServiceTest {
    @Mock
    private CourseRepository courseRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private CourseAccessService courseAccessService;

    private CourseAdminService courseAdminService;
    private StorageProperties storageProperties;

    @BeforeEach
    void setUp() {
        storageProperties = new StorageProperties();
        storageProperties.setSupabaseUrl("https://project.supabase.co");
        storageProperties.setCourseThumbnailBucket("course-thumbnails");
        courseAdminService = new CourseAdminService(
                courseRepository,
                categoryRepository,
                currentUserService,
                auditLogService,
                storageProperties,
                courseAccessService
        );
    }

    @Test
    void createShouldUseDevASchemaFieldsAndAuthenticatedCreator() {
        UUID categoryId = UUID.randomUUID();
        UserAccount admin = admin();
        Category category = category(categoryId);
        when(currentUserService.requireAuthenticatedUser()).thenReturn(admin);
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(courseRepository.existsBySlugIgnoreCaseAndDeletedAtIsNull("java-backend-co-ban")).thenReturn(false);
        when(courseRepository.save(any(Course.class))).thenAnswer(invocation -> persist(invocation.getArgument(0)));

        CourseResponse response = courseAdminService.create(new CreateCourseRequest(
                categoryId,
                "Java Backend cơ bản",
                null,
                "Short",
                "Long description",
                "Outcomes",
                "Requirements",
                "vi",
                "beginner",
                "https://project.supabase.co/storage/v1/object/public/course-thumbnails/2026/06/thumb.webp",
                BigDecimal.ZERO,
                null,
                true,
                "draft"
        ));

        assertThat(response.slug()).isEqualTo("java-backend-co-ban");
        assertThat(response.categoryId()).isEqualTo(categoryId);
        assertThat(response.creatorId()).isEqualTo(admin.getId());
        assertThat(response.thumbnailUrl()).contains("course-thumbnails");
        assertThat(response.status()).isEqualTo("draft");

        ArgumentCaptor<Course> courseCaptor = ArgumentCaptor.forClass(Course.class);
        verify(courseRepository).save(courseCaptor.capture());
        assertThat(courseCaptor.getValue().getCreator()).isEqualTo(admin);
        assertThat(courseCaptor.getValue().getStatus()).isEqualTo(CourseStatus.DRAFT);
        verify(auditLogService).record(admin.getEmail(), "COURSE_CREATED", "COURSE", response.id().toString());
    }

    @Test
    void listShouldApplyServerSideFiltersBeforePagination() {
        UUID categoryId = UUID.randomUUID();
        when(courseAccessService.isCurrentUserTrainer()).thenReturn(false);
        when(courseRepository.findAll(
                org.mockito.ArgumentMatchers.<Specification<Course>>any(),
                any(Pageable.class)))
                .thenReturn(Page.empty(PageRequest.of(0, 20)));

        var response = courseAdminService.list(
                0,
                20,
                "  react  ",
                "published",
                categoryId,
                "beginner");

        assertThat(response.totalItems()).isZero();
        assertThat(response.page()).isZero();
        verify(courseRepository).findAll(
                org.mockito.ArgumentMatchers.<Specification<Course>>any(),
                any(Pageable.class));
    }

    @Test
    void listShouldRejectUnknownStatus() {
        assertThatThrownBy(() -> courseAdminService.list(
                0,
                20,
                null,
                "archived",
                null,
                null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REQUEST);

        verify(courseRepository, never()).findAll(
                org.mockito.ArgumentMatchers.<Specification<Course>>any(),
                any(Pageable.class));
    }

    @Test
    void createShouldRejectFreeCourseWithPositivePrice() {
        UUID categoryId = UUID.randomUUID();
        when(currentUserService.requireAuthenticatedUser()).thenReturn(admin());
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category(categoryId)));

        assertThatThrownBy(() -> courseAdminService.create(new CreateCourseRequest(
                categoryId,
                "Paid Free Course",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                BigDecimal.TEN,
                null,
                true,
                null
        )))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REQUEST);

        verify(courseRepository, never()).save(any());
    }

    @Test
    void createShouldRejectThumbnailOutsideConfiguredBucket() {
        UUID categoryId = UUID.randomUUID();
        when(currentUserService.requireAuthenticatedUser()).thenReturn(admin());
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category(categoryId)));

        assertThatThrownBy(() -> courseAdminService.create(new CreateCourseRequest(
                categoryId,
                "Course",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "https://cdn.example.com/image.webp",
                BigDecimal.ZERO,
                null,
                false,
                null
        )))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REQUEST);

        verify(courseRepository, never()).save(any());
    }

    @Test
    void createShouldAcceptR2ThumbnailFromConfiguredBucketPublicUrl() {
        storageProperties.setProvider("r2");
        storageProperties.setR2CourseThumbnailPublicUrl("https://course-thumbnails.example.com");
        UUID categoryId = UUID.randomUUID();
        UserAccount admin = admin();
        Category category = category(categoryId);
        when(currentUserService.requireAuthenticatedUser()).thenReturn(admin);
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(courseRepository.existsBySlugIgnoreCaseAndDeletedAtIsNull("r2-course")).thenReturn(false);
        when(courseRepository.save(any(Course.class))).thenAnswer(invocation -> persist(invocation.getArgument(0)));

        CourseResponse response = courseAdminService.create(new CreateCourseRequest(
                categoryId,
                "R2 Course",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "https://course-thumbnails.example.com/2026/06/thumb.webp",
                BigDecimal.ZERO,
                null,
                false,
                null
        ));

        assertThat(response.thumbnailUrl()).isEqualTo("https://course-thumbnails.example.com/2026/06/thumb.webp");
    }

    @Test
    void createShouldRejectR2ThumbnailOutsideConfiguredBucketPublicUrl() {
        storageProperties.setProvider("r2");
        storageProperties.setR2CourseThumbnailPublicUrl("https://course-thumbnails.example.com");
        UUID categoryId = UUID.randomUUID();
        when(currentUserService.requireAuthenticatedUser()).thenReturn(admin());
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category(categoryId)));

        assertThatThrownBy(() -> courseAdminService.create(new CreateCourseRequest(
                categoryId,
                "Course",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "https://lesson-materials.example.com/2026/06/thumb.webp",
                BigDecimal.ZERO,
                null,
                false,
                null
        )))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REQUEST);

        verify(courseRepository, never()).save(any());
    }

    private Course persist(Course course) {
        course.setId(UUID.randomUUID());
        course.setCreatedAt(Instant.now());
        course.setUpdatedAt(Instant.now());
        return course;
    }

    private Category category(UUID id) {
        Category category = new Category();
        category.setId(id);
        category.setName("Programming");
        category.setSlug("programming");
        category.setActive(true);
        category.setSortOrder(0);
        category.setCreatedAt(Instant.now());
        category.setUpdatedAt(Instant.now());
        return category;
    }

    private UserAccount admin() {
        UserAccount admin = new UserAccount();
        admin.setId(UUID.randomUUID());
        admin.setEmail("admin@slp.vn");
        admin.setFullName("Admin");
        admin.setRole("ADMIN");
        admin.setStatus("active");
        return admin;
    }
}
