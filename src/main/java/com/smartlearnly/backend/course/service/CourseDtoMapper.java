package com.smartlearnly.backend.course.service;

import com.smartlearnly.backend.course.dto.CourseResponse;
import com.smartlearnly.backend.course.entity.Course;
import java.util.Locale;
import java.util.UUID;

final class CourseDtoMapper {
    private CourseDtoMapper() {
    }

    static CourseResponse toCourseResponse(Course course) {
        UUID creatorId = course.getCreator() == null ? null : course.getCreator().getId();
        UUID assignedSmeId = course.getAssignedSme() == null ? null : course.getAssignedSme().getId();
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
                course.getUpdatedAt(),
                assignedSmeId);
    }

    private static String enumValue(Enum<?> value) {
        return value == null ? null : value.name().toLowerCase(Locale.ROOT);
    }

}
