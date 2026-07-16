package com.smartlearnly.backend.classroom.service;

import com.smartlearnly.backend.classroom.dto.OpeningScheduleItemResponse;
import com.smartlearnly.backend.classroom.repository.ClassOfferingRepository;
import com.smartlearnly.backend.classroom.repository.OpeningScheduleProjection;
import com.smartlearnly.backend.common.api.PageResponse;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OpeningScheduleService {

    private static final int MAX_PAGE_SIZE = 100;

    private final ClassOfferingRepository classOfferingRepository;

    /**
     * Lấy danh sách các class sắp khai giảng và đang mở đăng ký.
     */
    public PageResponse<OpeningScheduleItemResponse> list(
            String keyword,
            UUID courseId,
            LocalDate startFrom,
            LocalDate startTo,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            int page,
            int size) {
        validatePageRequest(page, size);
        validateDateRange(startFrom, startTo);
        validatePriceRange(minPrice, maxPrice);

        String searchPattern = toSearchPattern(keyword);

        Page<OpeningScheduleProjection> schedules = classOfferingRepository.findOpeningSchedules(
                searchPattern,
                courseId,
                startFrom,
                startTo,
                minPrice,
                maxPrice,
                PageRequest.of(
                        page,
                        Math.min(size, MAX_PAGE_SIZE)));

        return new PageResponse<>(
                schedules.getContent()
                        .stream()
                        .map(this::toResponse)
                        .toList(),
                schedules.getNumber(),
                schedules.getSize(),
                schedules.getTotalElements(),
                schedules.getTotalPages());
    }

    /**
     * Lấy chi tiết một class trong Opening Schedule.
     */
    public OpeningScheduleItemResponse getDetail(UUID classId) {
        if (classId == null) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Class ID is required");
        }

        OpeningScheduleProjection classOffering = classOfferingRepository
                .findOpeningScheduleDetail(classId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Opening class was not found"));

        return toResponse(classOffering);
    }

    /**
     * Chuyển projection lấy từ database thành response trả cho frontend.
     */
    private OpeningScheduleItemResponse toResponse(
            OpeningScheduleProjection projection) {
        long activeEnrollmentCount = projection.getActiveEnrollmentCount() == null
                ? 0L
                : projection.getActiveEnrollmentCount();

        int maxStudents = projection.getMaxStudents() == null
                ? 0
                : projection.getMaxStudents();

        long availableSlots = Math.max(
                0L,
                (long) maxStudents - activeEnrollmentCount);

        return new OpeningScheduleItemResponse(
                projection.getClassId(),
                projection.getCourseId(),
                projection.getCourseTitle(),
                projection.getCourseSlug(),
                projection.getCourseThumbnailUrl(),
                projection.getClassName(),
                projection.getTrainerId(),
                projection.getTrainerName(),
                projection.getStartDate(),
                projection.getEndDate(),
                projection.getScheduleDescription(),
                projection.getPrice(),
                maxStudents,
                activeEnrollmentCount,
                availableSlots,
                projection.getStatus());
    }

    private void validatePageRequest(int page, int size) {
        if (page < 0) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Page index cannot be negative");
        }

        if (size < 1) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Page size must be greater than zero");
        }
    }

    private void validateDateRange(
            LocalDate startFrom,
            LocalDate startTo) {
        if (startFrom != null
                && startTo != null
                && startFrom.isAfter(startTo)) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Start-from date cannot be after start-to date");
        }
    }

    private void validatePriceRange(
            BigDecimal minPrice,
            BigDecimal maxPrice) {
        if (minPrice != null
                && minPrice.signum() < 0) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Minimum price cannot be negative");
        }

        if (maxPrice != null
                && maxPrice.signum() < 0) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Maximum price cannot be negative");
        }

        if (minPrice != null
                && maxPrice != null
                && minPrice.compareTo(maxPrice) > 0) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Minimum price cannot exceed maximum price");
        }
    }

    private String toSearchPattern(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }

        String normalizedKeyword = keyword.trim();

        return "%"
                + escapeLikePattern(normalizedKeyword)
                + "%";
    }

    private String escapeLikePattern(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}