package com.smartlearnly.backend.course.service;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.course.dto.PreviewLessonResponse;
import com.smartlearnly.backend.course.repository.CourseRepository;
import com.smartlearnly.backend.curriculum.entity.CurriculumLesson;
import com.smartlearnly.backend.curriculum.entity.CurriculumSection;
import com.smartlearnly.backend.curriculum.entity.CurriculumVersion;
import com.smartlearnly.backend.curriculum.service.CurriculumResolutionService;
import com.smartlearnly.backend.learning.lesson.entity.LessonStatus;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PreviewLessonService {
    private final CourseRepository courseRepository;
    private final CurriculumResolutionService curriculumResolutionService;

    @Transactional(readOnly = true)
    public List<PreviewLessonResponse> listPreviewLessons(UUID courseId) {
        ensurePublishedCourse(courseId);
        CurriculumVersion version = curriculumResolutionService.resolvePublicMaster(courseId).version();
        return orderedSections(version).stream()
                .flatMap(section -> orderedPreviewLessons(section).stream()
                        .map(lesson -> toPreviewLessonResponse(version.getCourseId(), section, lesson)))
                .toList();
    }

    @Transactional(readOnly = true)
    public PreviewLessonResponse getPreviewLesson(UUID courseId, UUID lessonId) {
        ensurePublishedCourse(courseId);
        CurriculumVersion version = curriculumResolutionService.resolvePublicMaster(courseId).version();
        return orderedSections(version).stream()
                .flatMap(section -> orderedPreviewLessons(section).stream()
                        .filter(lesson -> lesson.getId().equals(lessonId)
                                || lessonId.equals(lesson.getSourceLessonId())
                                || lessonId.equals(lesson.getLessonIdentityId()))
                        .map(lesson -> toPreviewLessonResponse(version.getCourseId(), section, lesson)))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Preview lesson was not found"));
    }

    private void ensurePublishedCourse(UUID courseId) {
        if (!courseRepository.existsPublishedById(courseId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Course was not found");
        }
    }

    private PreviewLessonResponse toPreviewLessonResponse(
            UUID courseId,
            CurriculumSection section,
            CurriculumLesson lesson) {
        return new PreviewLessonResponse(
                courseId,
                section.getId(),
                lesson.getId(),
                lesson.getTitle(),
                lesson.getType() == null ? null : lesson.getType().name(),
                lesson.getVideoUrl(),
                lesson.getContent(),
                lesson.getAttachmentUrl(),
                lesson.getDurationSeconds(),
                safeOrder(section.getSortOrder()),
                safeOrder(lesson.getSortOrder()));
    }

    private List<CurriculumSection> orderedSections(CurriculumVersion version) {
        return version.getSections().stream()
                .sorted(Comparator
                        .comparing(CurriculumSection::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(CurriculumSection::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(CurriculumSection::getId, Comparator.nullsLast(UUID::compareTo)))
                .toList();
    }

    private List<CurriculumLesson> orderedPreviewLessons(CurriculumSection section) {
        return section.getLessons().stream()
                .filter(lesson -> lesson.getStatus() == LessonStatus.PUBLISHED)
                .filter(lesson -> Boolean.TRUE.equals(lesson.getPreview()))
                .sorted(Comparator
                        .comparing(CurriculumLesson::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(CurriculumLesson::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(CurriculumLesson::getId, Comparator.nullsLast(UUID::compareTo)))
                .toList();
    }

    private int safeOrder(Integer sortOrder) {
        return sortOrder == null ? 0 : sortOrder;
    }
}
