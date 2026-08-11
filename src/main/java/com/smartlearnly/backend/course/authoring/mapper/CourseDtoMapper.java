package com.smartlearnly.backend.course.authoring.mapper;

import com.smartlearnly.backend.course.authoring.dto.CourseResponse;
import com.smartlearnly.backend.course.entity.Course;
import java.util.Locale;
import java.util.UUID;

public final class CourseDtoMapper {
    // Ngăn khởi tạo vì mapper chỉ cung cấp các phép chuyển đổi tĩnh.
    private CourseDtoMapper() {
    }

    // Chuyển entity khóa học thành DTO quản trị, chỉ lộ mã của các quan hệ liên quan.
    public static CourseResponse toCourseResponse(Course course) {
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
                course.getStatus() == null
                        ? null
                        : course.getStatus().name().toLowerCase(Locale.ROOT),
                course.getCreatedAt(),
                course.getUpdatedAt(),
                assignedSmeId);
    }

}
