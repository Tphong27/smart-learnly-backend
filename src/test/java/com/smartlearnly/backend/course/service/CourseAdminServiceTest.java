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
import com.smartlearnly.backend.course.dto.CourseCreateRequest;
import com.smartlearnly.backend.course.dto.CourseResponse;
import com.smartlearnly.backend.course.dto.CourseUpdateRequest;
import com.smartlearnly.backend.course.entity.Category;
import com.smartlearnly.backend.course.entity.Course;
import com.smartlearnly.backend.course.repository.CategoryRepository;
import com.smartlearnly.backend.course.repository.CourseRepository;
import com.smartlearnly.backend.user.entity.UserAccount;
import java.math.BigDecimal;
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
class CourseAdminServiceTest {
    @Mock
    private CourseRepository courseRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private AuditLogService auditLogService;

    private CourseAdminService courseAdminService;

    @BeforeEach
    void setUp() {
        courseAdminService = new CourseAdminService(
                courseRepository,
                categoryRepository,
                currentUserService,
                auditLogService
        );
    }

    @Test
    void createCourseShouldGenerateUniqueSlugAndDefaultPrice() {
        UUID categoryId = UUID.randomUUID();
        UserAccount admin = admin();
        Category category = category(categoryId);
        when(currentUserService.requireAdmin()).thenReturn(admin);
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(courseRepository.existsBySlugAndDeletedAtIsNull("intro-java")).thenReturn(true);
        when(courseRepository.existsBySlugAndDeletedAtIsNull("intro-java-2")).thenReturn(false);
        when(courseRepository.save(any(Course.class))).thenAnswer(invocation -> persist(invocation.getArgument(0)));

        CourseResponse response = courseAdminService.createCourse(new CourseCreateRequest(
                categoryId,
                "Intro Java",
                null,
                "Basic Java course",
                null,
                null,
                null,
                List.of("java", "backend"),
                true
        ));

        assertThat(response.slug()).isEqualTo("intro-java-2");
        assertThat(response.price()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.status()).isEqualTo(Course.STATUS_DRAFT);
        assertThat(response.featured()).isTrue();
        verify(auditLogService).record(admin.getEmail(), "COURSE_CREATED", "COURSE", response.id().toString());
    }

    @Test
    void createCourseShouldRejectDuplicateProvidedSlug() {
        UUID categoryId = UUID.randomUUID();
        when(currentUserService.requireAdmin()).thenReturn(admin());
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category(categoryId)));
        when(courseRepository.existsBySlugAndDeletedAtIsNull("intro-java")).thenReturn(true);

        assertThatThrownBy(() -> courseAdminService.createCourse(new CourseCreateRequest(
                categoryId,
                "Intro Java",
                "intro-java",
                null,
                BigDecimal.ZERO,
                Course.STATUS_DRAFT,
                null,
                null,
                false
        )))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CONFLICT);

        verify(courseRepository, never()).save(any());
    }

    @Test
    void updateCourseShouldRejectEmptyPayload() {
        UUID courseId = UUID.randomUUID();
        when(currentUserService.requireAdmin()).thenReturn(admin());
        when(courseRepository.findByIdAndDeletedAtIsNull(courseId)).thenReturn(Optional.of(course(courseId)));

        assertThatThrownBy(() -> courseAdminService.updateCourse(
                courseId,
                new CourseUpdateRequest(null, null, null, null, null, null, null, null, null)
        ))
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

    private Course course(UUID courseId) {
        Course course = new Course();
        course.setId(courseId);
        course.setCategory(category(UUID.randomUUID()));
        course.setTitle("Existing Course");
        course.setSlug("existing-course");
        course.setPrice(BigDecimal.ZERO);
        course.setStatus(Course.STATUS_DRAFT);
        course.setCreatedAt(Instant.now());
        course.setUpdatedAt(Instant.now());
        return course;
    }

    private Category category(UUID categoryId) {
        Category category = new Category();
        category.setId(categoryId);
        category.setName("Programming");
        category.setSlug("programming");
        category.setCreatedAt(Instant.now());
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
