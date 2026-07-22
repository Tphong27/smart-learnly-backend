package com.smartlearnly.backend.classroom.controller;

import com.smartlearnly.backend.classroom.dto.ScheduleResponse;
import com.smartlearnly.backend.classroom.service.ScheduleService;
import com.smartlearnly.backend.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Schedule", description = "Weekly schedules for trainees, trainers, and TMO")
public class ScheduleController {

    private final ScheduleService scheduleService;

    @GetMapping("/learning/schedule")
    @PreAuthorize("hasRole('TRAINEE')")
    @Operation(summary = "Get authenticated trainee weekly schedule")
    public ApiResponse<ScheduleResponse> getMySchedule(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart) {

        return ApiResponse.success(
                "Trainee schedule loaded successfully",
                scheduleService.getMySchedule(weekStart));
    }

    @GetMapping("/staff/schedule")
    @PreAuthorize("hasAnyRole('TRAINER', 'TMO')")
    @Operation(summary = "Get trainer teaching schedule", description = "Trainer can only view their own schedule")
    public ApiResponse<ScheduleResponse> getStaffSchedule(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart,
            @RequestParam(required = false) UUID trainerId) {

        return ApiResponse.success(
                "Staff schedule loaded successfully",
                scheduleService.getStaffSchedule(weekStart, trainerId));
    }
}