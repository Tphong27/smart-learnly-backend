package com.smartlearnly.backend.course.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.course.dto.PreviewLessonResponse;
import com.smartlearnly.backend.course.repository.CourseRepository;
import com.smartlearnly.backend.learning.lesson.repository.LessonRepository;
import com.smartlearnly.backend.learning.lesson.repository.PreviewLessonProjection;
import java.util.List;
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
        UUID courseId = UUID.randomUUID();
        UUID lessonId = UUID.randomUUID();
        PreviewLessonProjection lesson = previewLesson(courseId, lessonId);
        when(courseRepository.existsPublishedById(courseId))
                .thenReturn(true);
        when(lessonRepository.findPreviewLessons(courseId))
                .thenReturn(List.of(lesson));

        List<PreviewLessonResponse> response = previewLessonService.listPreviewLessons(courseId);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).lessonId()).isEqualTo(lessonId);
        assertThat(response.get(0).lessonType()).isEqualTo("RICH_TEXT");
    }

    @Test
    void listPreviewLessonsShouldHideDraftCourse() {
        UUID courseId = UUID.randomUUID();
        when(courseRepository.existsPublishedById(courseId))
                .thenReturn(false);

        assertThatThrownBy(() -> previewLessonService.listPreviewLessons(courseId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);

        verify(lessonRepository, never()).findPreviewLessons(courseId);
    }

    private PreviewLessonProjection previewLesson(UUID courseId, UUID lessonId) {
        PreviewLessonProjection lesson = mock(PreviewLessonProjection.class);
        when(lesson.getCourseId()).thenReturn(courseId);
        when(lesson.getSectionId()).thenReturn(UUID.randomUUID());
        when(lesson.getLessonId()).thenReturn(lessonId);
        when(lesson.getTitle()).thenReturn("Preview");
        when(lesson.getLessonType()).thenReturn("rich_text");
        when(lesson.getContent()).thenReturn("Preview content");
        when(lesson.getDurationSeconds()).thenReturn(600);
        when(lesson.getSectionSortOrder()).thenReturn(0);
        when(lesson.getLessonSortOrder()).thenReturn(0);
        return lesson;
    }
}
