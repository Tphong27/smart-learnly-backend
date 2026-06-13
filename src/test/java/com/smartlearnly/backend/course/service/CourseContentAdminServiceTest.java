package com.smartlearnly.backend.course.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.common.audit.AuditLogService;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.course.dto.ReorderRequest;
import com.smartlearnly.backend.course.entity.Category;
import com.smartlearnly.backend.course.entity.Course;
import com.smartlearnly.backend.course.repository.CourseRepository;
import com.smartlearnly.backend.learning.lesson.repository.LessonRepository;
import com.smartlearnly.backend.learning.module.entity.CourseModule;
import com.smartlearnly.backend.learning.module.repository.CourseModuleRepository;
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
class CourseContentAdminServiceTest {
    @Mock
    private CourseRepository courseRepository;
    @Mock
    private CourseModuleRepository courseModuleRepository;
    @Mock
    private LessonRepository lessonRepository;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private AuditLogService auditLogService;

    private CourseContentAdminService courseContentAdminService;

    @BeforeEach
    void setUp() {
        courseContentAdminService = new CourseContentAdminService(
                courseRepository,
                courseModuleRepository,
                lessonRepository,
                currentUserService,
                auditLogService
        );
    }

    @Test
    void reorderSectionsShouldRequireEveryActiveSectionExactlyOnce() {
        UUID courseId = UUID.randomUUID();
        Course course = course(courseId);
        CourseModule first = section(course, 0);
        CourseModule second = section(course, 1);
        when(currentUserService.requireAdmin()).thenReturn(admin());
        when(courseRepository.findByIdAndDeletedAtIsNull(courseId)).thenReturn(Optional.of(course));
        when(courseModuleRepository.findByCourse_IdAndStatusOrderByOrderIndexAscCreatedAtAsc(
                courseId,
                CourseModule.STATUS_ACTIVE
        )).thenReturn(List.of(first, second));

        assertThatThrownBy(() -> courseContentAdminService.reorderSections(
                courseId,
                new ReorderRequest(List.of(first.getId()))
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REQUEST);

        verify(courseModuleRepository, never()).saveAll(org.mockito.ArgumentMatchers.anyList());
    }

    private Course course(UUID courseId) {
        Category category = new Category();
        category.setId(UUID.randomUUID());
        category.setName("Programming");

        Course course = new Course();
        course.setId(courseId);
        course.setCategory(category);
        course.setTitle("Course");
        course.setSlug("course");
        course.setPrice(BigDecimal.ZERO);
        course.setStatus(Course.STATUS_DRAFT);
        course.setCreatedAt(Instant.now());
        course.setUpdatedAt(Instant.now());
        return course;
    }

    private CourseModule section(Course course, int orderIndex) {
        CourseModule section = new CourseModule();
        section.setId(UUID.randomUUID());
        section.setCourse(course);
        section.setTitle("Section " + orderIndex);
        section.setOrderIndex(orderIndex);
        section.setStatus(CourseModule.STATUS_ACTIVE);
        section.setCreatedAt(Instant.now());
        section.setUpdatedAt(Instant.now());
        return section;
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
