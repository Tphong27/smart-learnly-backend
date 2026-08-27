package com.smartlearnly.backend.classroom.opening.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record OpeningScheduleDetailResponse(
        UUID classId,
        UUID courseId,
        String courseTitle,
        String courseSlug,
        String courseThumbnailUrl,
        String courseShortDescription,
        String courseDescription,
        String courseLanguage,
        String courseLevel,
        UUID courseCategoryId,
        String courseCategoryName,
        String courseCategorySlug,
        String className,
        UUID trainerId,
        String trainerName,
        LocalDate startDate,
        LocalDate endDate,
        String scheduleDescription,
        BigDecimal price,
        Integer maxStudents,
        Long activeEnrollmentCount,
        Long availableSlots,
        String status
) {
}