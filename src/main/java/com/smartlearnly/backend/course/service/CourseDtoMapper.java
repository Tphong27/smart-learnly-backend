package com.smartlearnly.backend.course.service;

import com.smartlearnly.backend.course.dto.CourseResponse;
import com.smartlearnly.backend.course.dto.LessonResponse;
import com.smartlearnly.backend.course.dto.PreviewLessonResponse;
import com.smartlearnly.backend.course.dto.SectionResponse;
import com.smartlearnly.backend.course.entity.Category;
import com.smartlearnly.backend.course.entity.Course;
import com.smartlearnly.backend.learning.lesson.entity.Lesson;
import com.smartlearnly.backend.learning.module.entity.CourseModule;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

final class CourseDtoMapper {
    private CourseDtoMapper() {
    }

    static CourseResponse toCourseResponse(Course course) {
        Category category = course.getCategory();
        UUID creatorId = course.getCreator() == null ? null : course.getCreator().getId();
        return new CourseResponse(
                course.getId(),
                category.getId(),
                category.getName(),
                course.getTitle(),
                course.getSlug(),
                course.getDescription(),
                course.getPrice(),
                course.getStatus(),
                course.getAvatarUrl(),
                creatorId,
                toTagList(course.getTags()),
                course.isFeatured(),
                course.getCreatedAt(),
                course.getUpdatedAt()
        );
    }

    static SectionResponse toSectionResponse(CourseModule section) {
        return new SectionResponse(
                section.getId(),
                section.getCourse().getId(),
                section.getTitle(),
                section.getOrderIndex(),
                section.getStatus(),
                section.getCreatedAt(),
                section.getUpdatedAt()
        );
    }

    static LessonResponse toLessonResponse(Lesson lesson) {
        CourseModule section = lesson.getModule();
        return new LessonResponse(
                lesson.getId(),
                section.getId(),
                section.getCourse().getId(),
                lesson.getTitle(),
                lesson.getContent(),
                lesson.getLessonType(),
                lesson.getOrderIndex(),
                lesson.isPreview(),
                lesson.getStatus(),
                lesson.getCreatedAt(),
                lesson.getUpdatedAt()
        );
    }

    static PreviewLessonResponse toPreviewLessonResponse(Lesson lesson) {
        CourseModule section = lesson.getModule();
        return new PreviewLessonResponse(
                section.getCourse().getId(),
                section.getId(),
                lesson.getId(),
                lesson.getTitle(),
                lesson.getContent(),
                lesson.getLessonType(),
                section.getOrderIndex(),
                lesson.getOrderIndex()
        );
    }

    private static List<String> toTagList(String[] tags) {
        if (tags == null || tags.length == 0) {
            return List.of();
        }
        return Arrays.asList(tags);
    }
}
