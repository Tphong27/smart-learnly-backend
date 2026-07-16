package com.smartlearnly.backend.classroom.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.smartlearnly.backend.classroom.dto.OpeningScheduleItemResponse;
import com.smartlearnly.backend.classroom.service.OpeningScheduleService;
import com.smartlearnly.backend.common.api.PageResponse;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import com.smartlearnly.backend.common.api.ApiResponse;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/opening-schedules")
public class OpeningScheduleController {

    private final OpeningScheduleService openingScheduleService;

    @GetMapping
    public ApiResponse<PageResponse<OpeningScheduleItemResponse>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UUID courseId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startTo,
            @RequestParam(required = false) @DecimalMin("0.0") BigDecimal minPrice,
            @RequestParam(required = false) @DecimalMin("0.0") BigDecimal maxPrice,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "12") @Min(1) @Max(100) int size) {
        return ApiResponse.success(
                openingScheduleService.list(
                        keyword,
                        courseId,
                        startFrom,
                        startTo,
                        minPrice,
                        maxPrice,
                        page,
                        size));
    }

    @GetMapping("/{classId}")
    public ApiResponse<OpeningScheduleItemResponse> getDetail(
            @PathVariable UUID classId) {
        return ApiResponse.success(
                openingScheduleService.getDetail(classId));
    }
}
