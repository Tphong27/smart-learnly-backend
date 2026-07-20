package com.smartlearnly.backend.classroom.dto;

import java.time.LocalDate;
import java.util.List;

public record TraineeScheduleResponse(
        LocalDate weekStart,
        LocalDate weekEnd,
        List<TraineeScheduleSessionResponse> sessions
) {
}