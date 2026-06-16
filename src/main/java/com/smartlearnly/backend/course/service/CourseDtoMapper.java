package com.smartlearnly.backend.course.service;

import com.smartlearnly.backend.course.dto.CourseResponse;
import com.smartlearnly.backend.course.dto.LessonResponse;
import com.smartlearnly.backend.course.dto.PreviewLessonResponse;
import com.smartlearnly.backend.course.dto.SectionResponse;
import com.smartlearnly.backend.course.entity.Course;
import com.smartlearnly.backend.learning.lesson.entity.Lesson;
import com.smartlearnly.backend.learning.lesson.repository.PreviewLessonProjection;
import com.smartlearnly.backend.learning.module.entity.CourseSection;
import java.util.Locale;
import java.util.UUID;

final class CourseDtoMapper {
    private CourseDtoMapper() {
    }

    static CourseResponse toCourseResponse(Course course) {
        UUID creatorId = course.getCreator() == null ? null : course.getCreator().getId();
        return new CourseResponse(
                course.getId(),
                course.getCategory().getId(),
                course.getCategory().getName(),
                creatorId,
                course.getTitle(),
                course.getSlug(),
                course.getShortDescription(),
                course.getDescription(),
                course.getOutcomes(),
                course.getRequirements(),
                course.getLanguage(),
                course.getLevel(),
                course.getThumbnailUrl(),
                course.getPrice(),
                course.getDiscountedPrice(),
                Boolean.TRUE.equals(course.getFree()),
                enumValue(course.getStatus()),
                course.getCreatedAt(),
                course.getUpdatedAt()
        );
    }

    static SectionResponse toSectionResponse(CourseSection section) {
        return new SectionResponse(
                section.getId(),
                section.getCourse().getId(),
                section.getTitle(),
                section.getSortOrder(),
                section.getCreatedAt(),
                section.getUpdatedAt()
        );
    }

    static LessonResponse toLessonResponse(Lesson lesson) {
        return new LessonResponse(
                lesson.getId(),
                lesson.getCourse().getId(),
                lesson.getSection().getId(),
                lesson.getTitle(),
                enumValue(lesson.getType()),
                lesson.getVideoUrl(),
                lesson.getContent(),
                lesson.getAttachmentUrl(),
                lesson.getDurationSeconds(),
                Boolean.TRUE.equals(lesson.getPreview()),
                enumValue(lesson.getStatus()),
                lesson.getSortOrder(),
                lesson.getCreatedAt(),
                lesson.getUpdatedAt()
        );
    }

    static PreviewLessonResponse toPreviewLessonResponse(Lesson lesson) {
        return new PreviewLessonResponse(
                lesson.getCourse().getId(),
                lesson.getSection().getId(),
                lesson.getId(),
                lesson.getTitle(),
                enumValue(lesson.getType()),
                lesson.getVideoUrl(),
                lesson.getContent(),
                lesson.getAttachmentUrl(),
                lesson.getDurationSeconds(),
                lesson.getSection().getSortOrder(),
                lesson.getSortOrder()
        );
    }

    static PreviewLessonResponse toPreviewLessonResponse(PreviewLessonProjection lesson) {
        return new PreviewLessonResponse(
                lesson.getCourseId(),
                lesson.getSectionId(),
                lesson.getLessonId(),
                lesson.getTitle(),
                lesson.getLessonType(),
                lesson.getVideoUrl(),
                lesson.getContent(),
                lesson.getAttachmentUrl(),
                lesson.getDurationSeconds(),
                lesson.getSectionSortOrder(),
                lesson.getLessonSortOrder()
        );
    }

    private static String enumValue(Enum<?> value) {
        return value == null ? null : value.name().toLowerCase(Locale.ROOT);
    }
}
