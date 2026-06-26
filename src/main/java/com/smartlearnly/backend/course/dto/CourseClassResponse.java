package com.smartlearnly.backend.course.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CourseClassResponse(
        UUID id,
        UUID courseId,
        String className,
        UUID trainerId,
        String trainerName,
        String scheduleDescription,
        LocalDate startDate,
        LocalDate endDate,
        Integer maxStudents,
        BigDecimal price,
        Long activeEnrollmentCount,
        Long availableSlots,
        String status
) {
}