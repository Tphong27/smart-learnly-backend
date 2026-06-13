package com.smartlearnly.backend.course.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.course.dto.PreviewLessonResponse;
import com.smartlearnly.backend.course.entity.Category;
import com.smartlearnly.backend.course.entity.Course;
import com.smartlearnly.backend.course.repository.CourseRepository;
import com.smartlearnly.backend.learning.lesson.entity.Lesson;
import com.smartlearnly.backend.learning.lesson.repository.LessonRepository;
import com.smartlearnly.backend.learning.module.entity.CourseModule;
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
class PreviewLessonServiceTest {
    @Mock
    private CourseRepository courseRepository;
    @Mock
    private LessonRepository lessonRepository;

    private PreviewLessonService previewLessonService;

    @BeforeEach
    void setUp() {
        previewLessonService = new PreviewLessonService(courseRepository, lessonRepository);
    }

    @Test
    void listPreviewLessonsShouldReturnPublishedPreviewLessons() {
        Course course = course(Course.STATUS_PUBLISHED);
        CourseModule section = section(course);
        Lesson lesson = lesson(section);
        when(courseRepository.findByIdAndDeletedAtIsNull(course.getId())).thenReturn(Optional.of(course));
        when(lessonRepository.findPreviewLessonsForPublishedCourse(course.getId())).thenReturn(List.of(lesson));

        List<PreviewLessonResponse> response = previewLessonService.listPreviewLessons(course.getId());

        assertThat(response).hasSize(1);
        assertThat(response.get(0).lessonId()).isEqualTo(lesson.getId());
        assertThat(response.get(0).courseId()).isEqualTo(course.getId());
    }

    @Test
    void listPreviewLessonsShouldHideDraftCourse() {
        Course course = course(Course.STATUS_DRAFT);
        when(courseRepository.findByIdAndDeletedAtIsNull(course.getId())).thenReturn(Optional.of(course));

        assertThatThrownBy(() -> previewLessonService.listPreviewLessons(course.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);

        verify(lessonRepository, never()).findPreviewLessonsForPublishedCourse(course.getId());
    }

    private Course course(String status) {
        Category category = new Category();
        category.setId(UUID.randomUUID());
        category.setName("Programming");

        Course course = new Course();
        course.setId(UUID.randomUUID());
        course.setCategory(category);
        course.setTitle("Course");
        course.setSlug("course");
        course.setPrice(BigDecimal.ZERO);
        course.setStatus(status);
        course.setCreatedAt(Instant.now());
        course.setUpdatedAt(Instant.now());
        return course;
    }

    private CourseModule section(Course course) {
        CourseModule section = new CourseModule();
        section.setId(UUID.randomUUID());
        section.setCourse(course);
        section.setTitle("Section");
        section.setOrderIndex(0);
        section.setStatus(CourseModule.STATUS_ACTIVE);
        section.setCreatedAt(Instant.now());
        section.setUpdatedAt(Instant.now());
        return section;
    }

    private Lesson lesson(CourseModule section) {
        Lesson lesson = new Lesson();
        lesson.setId(UUID.randomUUID());
        lesson.setModule(section);
        lesson.setTitle("Preview lesson");
        lesson.setContent("Sample content");
        lesson.setLessonType(Lesson.TYPE_RICH_TEXT);
        lesson.setOrderIndex(0);
        lesson.setPreview(true);
        lesson.setStatus(Lesson.STATUS_ACTIVE);
        lesson.setCreatedAt(Instant.now());
        lesson.setUpdatedAt(Instant.now());
        return lesson;
    }
}
