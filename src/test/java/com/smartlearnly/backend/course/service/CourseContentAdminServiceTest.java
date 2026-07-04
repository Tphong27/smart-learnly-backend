package com.smartlearnly.backend.course.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.common.audit.AuditLogService;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.course.dto.LessonRequest;
import com.smartlearnly.backend.course.dto.LessonResourceRequest;
import com.smartlearnly.backend.course.dto.LessonResponse;
import com.smartlearnly.backend.course.dto.ReorderRequest;
import com.smartlearnly.backend.course.entity.Category;
import com.smartlearnly.backend.course.entity.Course;
import com.smartlearnly.backend.course.entity.CourseStatus;
import com.smartlearnly.backend.course.repository.CourseRepository;
import com.smartlearnly.backend.learning.lesson.entity.Lesson;
import com.smartlearnly.backend.learning.lesson.entity.LessonStatus;
import com.smartlearnly.backend.learning.lesson.entity.LessonType;
import com.smartlearnly.backend.learning.lesson.repository.LessonRepository;
import com.smartlearnly.backend.learning.lesson.service.QuizContentValidator;
import com.smartlearnly.backend.learning.module.entity.CourseSection;
import com.smartlearnly.backend.learning.module.repository.CourseSectionRepository;
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
    private CourseSectionRepository courseSectionRepository;
    @Mock
    private LessonRepository lessonRepository;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private QuizContentValidator quizContentValidator;

    private CourseContentAdminService courseContentAdminService;

    @BeforeEach
    void setUp() {
        courseContentAdminService = new CourseContentAdminService(
                courseRepository,
                courseSectionRepository,
                lessonRepository,
                currentUserService,
                auditLogService,
                quizContentValidator
        );
    }

    @Test
    void reorderSectionsShouldRequireEverySectionExactlyOnce() {
        Course course = course();
        CourseSection first = section(course, 0);
        CourseSection second = section(course, 1);
        when(courseRepository.findByIdAndDeletedAtIsNull(course.getId())).thenReturn(Optional.of(course));
        when(courseSectionRepository.findByCourseIdOrderBySortOrderAscCreatedAtAsc(course.getId()))
                .thenReturn(List.of(first, second));

        assertThatThrownBy(() -> courseContentAdminService.reorderSections(
                course.getId(),
                new ReorderRequest(List.of(first.getId()))
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REQUEST);

        verify(courseSectionRepository, never()).saveAll(anyList());
    }

    @Test
    void listLessonsShouldIncludeInactiveLessonsForAdmin() {
        Course course = course();
        CourseSection section = section(course, 0);
        Lesson draft = lesson(course, section);
        Lesson inactive = lesson(course, section);
        inactive.setStatus(LessonStatus.INACTIVE);
        inactive.setSortOrder(1);
        when(courseSectionRepository.findById(section.getId())).thenReturn(Optional.of(section));
        when(lessonRepository.findBySectionIdOrderBySortOrderAscCreatedAtAsc(section.getId()))
                .thenReturn(List.of(draft, inactive));

        List<LessonResponse> response = courseContentAdminService.listLessons(section.getId());

        assertThat(response).hasSize(2);
        assertThat(response).extracting(LessonResponse::status).containsExactly("draft", "inactive");
    }

    @Test
    void reorderLessonsShouldIncludeInactiveLessonsForAdmin() {
        Course course = course();
        CourseSection section = section(course, 0);
        Lesson draft = lesson(course, section);
        Lesson inactive = lesson(course, section);
        inactive.setStatus(LessonStatus.INACTIVE);
        inactive.setSortOrder(1);
        UserAccount actor = new UserAccount();
        actor.setEmail("admin@smartlearnly.dev");
        when(courseSectionRepository.findById(section.getId())).thenReturn(Optional.of(section));
        when(lessonRepository.findBySectionIdOrderBySortOrderAscCreatedAtAsc(section.getId()))
                .thenReturn(List.of(draft, inactive));
        when(lessonRepository.saveAll(List.of(draft, inactive))).thenReturn(List.of(draft, inactive));
        when(currentUserService.requireAuthenticatedUser()).thenReturn(actor);

        List<LessonResponse> response = courseContentAdminService.reorderLessons(
                section.getId(),
                new ReorderRequest(List.of(inactive.getId(), draft.getId()))
        );

        assertThat(response).extracting(LessonResponse::id).containsExactly(inactive.getId(), draft.getId());
        assertThat(inactive.getSortOrder()).isZero();
        assertThat(draft.getSortOrder()).isEqualTo(1);
    }

    @Test
    void updateLessonShouldAcceptTypeAliasAndReplaceResources() {
        Course course = course();
        CourseSection section = section(course, 0);
        Lesson lesson = lesson(course, section);
        UserAccount actor = new UserAccount();
        actor.setEmail("admin@smartlearnly.dev");
        when(lessonRepository.findById(lesson.getId())).thenReturn(Optional.of(lesson));
        when(lessonRepository.save(lesson)).thenReturn(lesson);
        when(currentUserService.requireAuthenticatedUser()).thenReturn(actor);

        LessonResponse response = courseContentAdminService.updateLesson(
                lesson.getId(),
                new LessonRequest(
                        "Document lesson",
                        null,
                        "DOCUMENT",
                        null,
                        "Summary",
                        "https://storage.test/material.pdf",
                        0,
                        false,
                        "PUBLISHED",
                        List.of(new LessonResourceRequest(
                                "https://storage.test/resource.pdf",
                                "2026/06/resource.pdf",
                                null,
                                "resource.pdf",
                                123L,
                                "application/pdf",
                                null
                        )),
                        null
                )
        );

        assertThat(response.lessonType()).isEqualTo("PDF");
        assertThat(response.resources()).hasSize(1);
        assertThat(response.resources().get(0).name()).isEqualTo("resource.pdf");
        assertThat(response.resources().get(0).sortOrder()).isZero();
    }

    private Course course() {
        Category category = new Category();
        category.setId(UUID.randomUUID());
        category.setName("Programming");

        Course course = new Course();
        course.setId(UUID.randomUUID());
        course.setTitle("Course");
        course.setSlug("course");
        course.setCategory(category);
        course.setPrice(BigDecimal.ZERO);
        course.setFree(true);
        course.setStatus(CourseStatus.DRAFT);
        course.setCreatedAt(Instant.now());
        course.setUpdatedAt(Instant.now());
        return course;
    }

    private CourseSection section(Course course, int sortOrder) {
        CourseSection section = new CourseSection();
        section.setId(UUID.randomUUID());
        section.setCourse(course);
        section.setTitle("Section " + sortOrder);
        section.setSortOrder(sortOrder);
        section.setCreatedAt(Instant.now());
        section.setUpdatedAt(Instant.now());
        return section;
    }

    private Lesson lesson(Course course, CourseSection section) {
        Lesson lesson = new Lesson();
        lesson.setId(UUID.randomUUID());
        lesson.setCourse(course);
        lesson.setSection(section);
        lesson.setTitle("Lesson");
        lesson.setType(LessonType.RICH_TEXT);
        lesson.setPreview(false);
        lesson.setStatus(LessonStatus.DRAFT);
        lesson.setSortOrder(0);
        lesson.setCreatedAt(Instant.now());
        lesson.setUpdatedAt(Instant.now());
        return lesson;
    }
}
