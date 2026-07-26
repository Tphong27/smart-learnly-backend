package com.smartlearnly.backend.classroom.dto;

import java.time.LocalDate;
import java.util.List;

public record ScheduleResponse(
        LocalDate weekStart,
        LocalDate weekEnd,
        List<ScheduleSessionResponse> sessions
) {
}