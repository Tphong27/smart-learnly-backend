package com.smartlearnly.backend.classroom.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ClassResponse(
        UUID id,
        UUID courseId,
        String courseTitle,
        String className,
        UUID trainerId,
        String trainerName,
        String scheduleDescription,
        LocalDate startDate,
        LocalDate endDate,
        int maxStudents,
        long activeEnrollmentCount,
        long availableSeats,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
}
