package com.smartlearnly.backend.classroom.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record OpeningScheduleItemResponse(
        UUID classId,
        UUID courseId,
        String courseTitle,
        String courseSlug,
        String courseThumbnailUrl,
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