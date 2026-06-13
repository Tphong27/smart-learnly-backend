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
import com.smartlearnly.backend.course.entity.CourseStatus;
import com.smartlearnly.backend.course.repository.CourseRepository;
import com.smartlearnly.backend.learning.lesson.entity.Lesson;
import com.smartlearnly.backend.learning.lesson.entity.LessonStatus;
import com.smartlearnly.backend.learning.lesson.entity.LessonType;
import com.smartlearnly.backend.learning.lesson.repository.LessonRepository;
import com.smartlearnly.backend.learning.module.entity.CourseSection;
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
    void listPreviewLessonsShouldReturnPublishedPreviewLessonsOnly() {
        Course course = course(CourseStatus.PUBLISHED);
        CourseSection section = section(course);
        Lesson lesson = lesson(course, section);
        when(courseRepository.findByIdAndStatusAndDeletedAtIsNull(course.getId(), CourseStatus.PUBLISHED))
                .thenReturn(Optional.of(course));
        when(lessonRepository.findPreviewLessons(course.getId(), CourseStatus.PUBLISHED, LessonStatus.PUBLISHED))
                .thenReturn(List.of(lesson));

        List<PreviewLessonResponse> response = previewLessonService.listPreviewLessons(course.getId());

        assertThat(response).hasSize(1);
        assertThat(response.get(0).lessonId()).isEqualTo(lesson.getId());
        assertThat(response.get(0).lessonType()).isEqualTo("rich_text");
    }

    @Test
    void listPreviewLessonsShouldHideDraftCourse() {
        UUID courseId = UUID.randomUUID();
        when(courseRepository.findByIdAndStatusAndDeletedAtIsNull(courseId, CourseStatus.PUBLISHED))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> previewLessonService.listPreviewLessons(courseId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);

        verify(lessonRepository, never()).findPreviewLessons(courseId, CourseStatus.PUBLISHED, LessonStatus.PUBLISHED);
    }

    private Course course(CourseStatus status) {
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
        course.setStatus(status);
        course.setCreatedAt(Instant.now());
        course.setUpdatedAt(Instant.now());
        return course;
    }

    private CourseSection section(Course course) {
        CourseSection section = new CourseSection();
        section.setId(UUID.randomUUID());
        section.setCourse(course);
        section.setTitle("Section");
        section.setSortOrder(0);
        section.setCreatedAt(Instant.now());
        section.setUpdatedAt(Instant.now());
        return section;
    }

    private Lesson lesson(Course course, CourseSection section) {
        Lesson lesson = new Lesson();
        lesson.setId(UUID.randomUUID());
        lesson.setCourse(course);
        lesson.setSection(section);
        lesson.setTitle("Preview");
        lesson.setType(LessonType.RICH_TEXT);
        lesson.setContent("Preview content");
        lesson.setPreview(true);
        lesson.setStatus(LessonStatus.PUBLISHED);
        lesson.setSortOrder(0);
        lesson.setCreatedAt(Instant.now());
        lesson.setUpdatedAt(Instant.now());
        return lesson;
    }
}
