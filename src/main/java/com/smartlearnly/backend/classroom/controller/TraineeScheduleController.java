package com.smartlearnly.backend.classroom.controller;

import com.smartlearnly.backend.classroom.dto.TraineeScheduleResponse;
import com.smartlearnly.backend.classroom.service.TraineeScheduleService;
import com.smartlearnly.backend.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/learning/schedule")
@RequiredArgsConstructor
@Tag(name = "Trainee Schedule", description = "Weekly class schedule for trainees")
public class TraineeScheduleController {

    private final TraineeScheduleService traineeScheduleService;

    @GetMapping
    @PreAuthorize("hasRole('TRAINEE')")
    @Operation(summary = "Get authenticated trainee weekly schedule")
    public ApiResponse<TraineeScheduleResponse> getMySchedule(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart) {
        return ApiResponse.success(
                "Trainee schedule loaded successfully",
                traineeScheduleService.getMySchedule(weekStart));
    }
}