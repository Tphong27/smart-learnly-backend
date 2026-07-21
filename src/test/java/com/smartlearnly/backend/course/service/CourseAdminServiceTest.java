package com.smartlearnly.backend.course.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.common.audit.AuditLogService;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.CurrentUserService;
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
    @Mock
    private CurriculumVersionRepository curriculumVersionRepository;

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
                courseAccessService,
                curriculumVersionRepository
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

        ArgumentCaptor<CurriculumVersion> curriculumCaptor = ArgumentCaptor.forClass(CurriculumVersion.class);
        verify(curriculumVersionRepository).save(curriculumCaptor.capture());
        assertThat(curriculumCaptor.getValue().getCourseId()).isEqualTo(response.id());
        assertThat(curriculumCaptor.getValue().getScope()).isEqualTo(CurriculumScope.MASTER);
        assertThat(curriculumCaptor.getValue().getStatus()).isEqualTo(CurriculumStatus.DRAFT);
        assertThat(curriculumCaptor.getValue().getVersionNumber()).isEqualTo(1);
        assertThat(curriculumCaptor.getValue().getCreatedBy()).isEqualTo(admin.getId());
        verify(auditLogService).record(admin.getEmail(), "COURSE_CREATED", "COURSE", response.id().toString());
    }

    @Test
    void createPublishedCourseShouldAlsoCreatePublishedMasterCurriculum() {
        UUID categoryId = UUID.randomUUID();
        UserAccount admin = admin();
        when(currentUserService.requireAuthenticatedUser()).thenReturn(admin);
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category(categoryId)));
        when(courseRepository.existsBySlugIgnoreCaseAndDeletedAtIsNull("published-course")).thenReturn(false);
        when(courseRepository.save(any(Course.class))).thenAnswer(invocation -> persist(invocation.getArgument(0)));
        when(curriculumVersionRepository
                .findFirstByCourseIdAndScopeOrderByVersionNumberDescCreatedAtDesc(
                        any(UUID.class), org.mockito.ArgumentMatchers.eq(CurriculumScope.MASTER)))
                .thenReturn(Optional.empty());
        when(curriculumVersionRepository.findMaxMasterVersionNumber(
                any(UUID.class), org.mockito.ArgumentMatchers.eq(CurriculumScope.MASTER)))
                .thenReturn(0);
        when(curriculumVersionRepository.save(any(CurriculumVersion.class))).thenAnswer(invocation -> {
            CurriculumVersion version = invocation.getArgument(0);
            if (version.getId() == null) {
                version.setId(UUID.randomUUID());
            }
            return version;
        });
        when(curriculumVersionRepository
                .findFirstByCourseIdAndScopeAndStatusOrderByVersionNumberDescCreatedAtDesc(
                        any(UUID.class),
                        org.mockito.ArgumentMatchers.eq(CurriculumScope.MASTER),
                        org.mockito.ArgumentMatchers.eq(CurriculumStatus.PUBLISHED)))
                .thenReturn(Optional.empty());

        courseAdminService.create(new CreateCourseRequest(
                categoryId,
                "Published course",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                BigDecimal.ZERO,
                null,
                true,
                "published"
        ));

        ArgumentCaptor<CurriculumVersion> versionCaptor = ArgumentCaptor.forClass(CurriculumVersion.class);
        verify(curriculumVersionRepository, times(2)).save(versionCaptor.capture());
        CurriculumVersion published = versionCaptor.getAllValues().get(1);
        assertThat(published.getStatus()).isEqualTo(CurriculumStatus.PUBLISHED);
        assertThat(published.getPublishedAt()).isNotNull();
        assertThat(published.getCreatedBy()).isEqualTo(admin.getId());
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

    @Test
    void updateShouldPublishLatestMasterCurriculumWhenCourseIsPublished() {
        Course course = existingCourse(CourseStatus.DRAFT);
        CurriculumVersion draft = new CurriculumVersion();
        draft.setId(UUID.randomUUID());
        draft.setCourseId(course.getId());
        draft.setScope(CurriculumScope.MASTER);
        draft.setStatus(CurriculumStatus.DRAFT);
        draft.setVersionNumber(2);
        draft.setTitle(course.getTitle());
        UserAccount actor = admin();
        UpdateCourseRequest request = new UpdateCourseRequest();
        request.setStatus("published");

        when(courseRepository.findByIdAndDeletedAtIsNull(course.getId())).thenReturn(Optional.of(course));
        when(courseRepository.save(course)).thenReturn(course);
        when(currentUserService.requireAuthenticatedUser()).thenReturn(actor);
        when(curriculumVersionRepository
                .findFirstByCourseIdAndScopeOrderByVersionNumberDescCreatedAtDesc(
                        course.getId(), CurriculumScope.MASTER))
                .thenReturn(Optional.of(draft));
        when(curriculumVersionRepository
                .findFirstByCourseIdAndScopeAndStatusOrderByVersionNumberDescCreatedAtDesc(
                        course.getId(), CurriculumScope.MASTER, CurriculumStatus.PUBLISHED))
                .thenReturn(Optional.empty());

        CourseResponse response = courseAdminService.update(course.getId(), request);

        assertThat(response.status()).isEqualTo("published");
        assertThat(draft.getStatus()).isEqualTo(CurriculumStatus.PUBLISHED);
        assertThat(draft.getPublishedAt()).isNotNull();
        verify(curriculumVersionRepository).save(draft);
    }

    private Course persist(Course course) {
        course.setId(UUID.randomUUID());
        course.setCreatedAt(Instant.now());
        course.setUpdatedAt(Instant.now());
        return course;
    }

    private Course existingCourse(CourseStatus status) {
        Course course = new Course();
        course.setId(UUID.randomUUID());
        course.setCategory(category(UUID.randomUUID()));
        course.setCreator(admin());
        course.setTitle("Course");
        course.setSlug("course");
        course.setPrice(BigDecimal.ZERO);
        course.setFree(true);
        course.setFeatured(false);
        course.setStatus(status);
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
