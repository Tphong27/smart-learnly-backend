package com.smartlearnly.backend.course.service;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.course.dto.PreviewLessonResponse;
import com.smartlearnly.backend.course.repository.CourseRepository;
import com.smartlearnly.backend.learning.lesson.repository.LessonRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PreviewLessonService {
    private final CourseRepository courseRepository;
    private final LessonRepository lessonRepository;

    @Transactional(readOnly = true)
    public List<PreviewLessonResponse> listPreviewLessons(UUID courseId) {
        ensurePublishedCourse(courseId);
        return lessonRepository.findPreviewLessons(courseId)
                .stream()
                .map(CourseDtoMapper::toPreviewLessonResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PreviewLessonResponse getPreviewLesson(UUID courseId, UUID lessonId) {
        ensurePublishedCourse(courseId);
        return lessonRepository.findPreviewLesson(courseId, lessonId)
                .map(CourseDtoMapper::toPreviewLessonResponse)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Preview lesson was not found"));
    }

    private void ensurePublishedCourse(UUID courseId) {
        if (!courseRepository.existsPublishedById(courseId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Course was not found");
        }
    }
}
