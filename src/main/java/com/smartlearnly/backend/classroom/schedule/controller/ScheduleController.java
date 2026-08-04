package com.smartlearnly.backend.classroom.schedule.controller;

import com.smartlearnly.backend.classroom.schedule.dto.ScheduleResponse;
import com.smartlearnly.backend.classroom.schedule.service.ScheduleService;
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

    @GetMapping("/staff/schedule")
    @PreAuthorize("hasAnyRole('TRAINER', 'TMO')")
    @Operation(summary = "Get trainer teaching schedule", description = "Trainer can only view their own schedule")
    // Trả lịch tuần của giảng viên theo quyền của người dùng nhân sự hiện tại.
    public ApiResponse<ScheduleResponse> getStaffSchedule(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart,
            @RequestParam(required = false) UUID trainerId) {

        return ApiResponse.success(
                "Staff schedule loaded successfully",
                scheduleService.getStaffSchedule(weekStart, trainerId));
    }
}
