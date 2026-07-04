package com.smartlearnly.backend.enrollment.dto;

import java.time.LocalDate;
import java.util.UUID;

public record MyCourseClassResponse(
        UUID id,
        String className,
        String status,
        String trainerName,
        String scheduleDescription,
        LocalDate startDate,
        LocalDate endDate,
        Integer maxStudents,
        Long activeEnrollmentCount,
        UUID classEnrollmentId
) {
}