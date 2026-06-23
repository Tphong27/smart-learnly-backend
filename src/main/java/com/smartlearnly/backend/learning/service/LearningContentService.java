package com.smartlearnly.backend.learning.service;

import com.smartlearnly.backend.course.entity.Course;
import com.smartlearnly.backend.course.repository.CourseRepository;
import com.smartlearnly.backend.enrollment.service.EnrollmentAccessService;
import com.smartlearnly.backend.enrollment.entity.CourseEnrollment;
import com.smartlearnly.backend.learning.dto.*;
import com.smartlearnly.backend.learning.lesson.entity.Lesson;
import com.smartlearnly.backend.learning.lesson.entity.LessonResource;
import com.smartlearnly.backend.learning.lesson.entity.LessonType;
import com.smartlearnly.backend.learning.module.entity.CourseSection;
import com.smartlearnly.backend.learning.module.repository.CourseSectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LearningContentService {
    private final CourseRepository courseRepository;
    private final CourseSectionRepository courseSectionRepository;
    private final EnrollmentAccessService enrollmentAccessService;

    @Transactional(readOnly = true)
    public LearningContentResponse getLearningContent(UUID courseId) {
        enrollmentAccessService.requireCourseAccess(courseId);
        Course course = courseRepository.findByIdAndDeletedAtIsNull(courseId)
                .orElseThrow(() -> new RuntimeException("Course not found"));

        List<CourseSection> sections = courseSectionRepository
                .findByCourseIdOrderBySortOrderAscCreatedAtAsc(courseId);

        List<LearningSectionResponse> sectionResponses = sections.stream()
                .map(this::toSectionResponse)
                .toList();

        LearningStats stats = calculateStats(sections);

        return new LearningContentResponse(
                courseId,
                course.getTitle(),
                course.getThumbnailUrl(),
                sectionResponses,
                stats
        );
    }

    @Transactional(readOnly = true)
    public LearningContentResponse getPreviewContent(UUID courseId) {
        Course course = courseRepository.findByIdAndDeletedAtIsNull(courseId)
                .orElseThrow(() -> new RuntimeException("Course not found"));

        List<CourseSection> sections = courseSectionRepository
                .findByCourseIdOrderBySortOrderAscCreatedAtAsc(courseId);

        List<LearningSectionResponse> sectionResponses = sections.stream()
                .map(this::toPreviewSectionResponse)
                .filter(s -> s != null)
                .toList();

        LearningStats stats = calculateStats(sections);

        return new LearningContentResponse(
                courseId,
                course.getTitle(),
                course.getThumbnailUrl(),
                sectionResponses,
                stats
        );
    }

    @Transactional(readOnly = true)
    public LearningContentResponse getAdminPreviewContent(UUID courseId) {
        Course course = courseRepository.findByIdAndDeletedAtIsNull(courseId)
                .orElseThrow(() -> new RuntimeException("Course not found"));

        List<CourseSection> sections = courseSectionRepository
                .findByCourseIdOrderBySortOrderAscCreatedAtAsc(courseId);

        List<LearningSectionResponse> sectionResponses = sections.stream()
                .map(this::toSectionResponse)
                .toList();

        LearningStats stats = calculateStats(sections);

        return new LearningContentResponse(
                courseId,
                course.getTitle(),
                course.getThumbnailUrl(),
                sectionResponses,
                stats
        );
    }

    private LearningSectionResponse toPreviewSectionResponse(CourseSection section) {
        List<LearningLessonResponse> lessonResponses = section.getLessons().stream()
                .filter(lesson -> Boolean.TRUE.equals(lesson.getPreview()))
                .map(this::toLessonResponse)
                .toList();
        if (lessonResponses.isEmpty()) return null;
        return new LearningSectionResponse(
                section.getId(),
                section.getTitle(),
                section.getSortOrder(),
                lessonResponses
        );
    }

    private LearningSectionResponse toSectionResponse(CourseSection section) {
        List<LearningLessonResponse> lessonResponses = section.getLessons().stream()
                .map(this::toLessonResponse)
                .toList();

        return new LearningSectionResponse(
                section.getId(),
                section.getTitle(),
                section.getSortOrder(),
                lessonResponses
        );
    }

    private LearningLessonResponse toLessonResponse(Lesson lesson) {
        List<LearningResourceResponse> resourceResponses = lesson.getResources().stream()
                .map(r -> new LearningResourceResponse(r.getUrl(), r.getName(), r.getContentType()))
                .toList();

        return new LearningLessonResponse(
                lesson.getId(),
                lesson.getTitle(),
                lesson.getType().name(),
                lesson.getVideoUrl(),
                lesson.getContent(),
                lesson.getAttachmentUrl(),
                lesson.getDurationSeconds(),
                Boolean.TRUE.equals(lesson.getPreview()),
                lesson.getSortOrder(),
                resourceResponses
        );
    }

    private LearningStats calculateStats(List<CourseSection> sections) {
        int totalVideos = 0;
        int totalDocuments = 0;
        int totalQuizzes = 0;
        int totalDurationSeconds = 0;

        for (CourseSection section : sections) {
            for (Lesson lesson : section.getLessons()) {
                LessonType type = lesson.getType();
                if (type == LessonType.VIDEO) {
                    totalVideos++;
                } else if (type == LessonType.PDF) {
                    totalDocuments++;
                } else if (type == LessonType.QUIZ) {
                    totalQuizzes++;
                }
                if (lesson.getDurationSeconds() != null) {
                    totalDurationSeconds += lesson.getDurationSeconds();
                }
            }
        }

        return new LearningStats(
                sections.size(),
                sections.stream().mapToInt(s -> s.getLessons().size()).sum(),
                totalVideos,
                totalDocuments,
                totalQuizzes,
                totalDurationSeconds
        );
    }
}
