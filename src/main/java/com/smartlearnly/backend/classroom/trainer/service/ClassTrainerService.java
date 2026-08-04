package com.smartlearnly.backend.classroom.trainer.service;

import com.smartlearnly.backend.classroom.dto.ClassResponse;
import com.smartlearnly.backend.classroom.entity.ClassStatus;
import com.smartlearnly.backend.classroom.repository.ClassAdminProjection;
import com.smartlearnly.backend.classroom.repository.ClassOfferingRepository;
import com.smartlearnly.backend.common.api.PageResponse;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.user.entity.UserAccount;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ClassTrainerService {
    private static final int MAX_PAGE_SIZE = 100;

    private final ClassOfferingRepository classOfferingRepository;
    private final CurrentUserService currentUserService;

    @Transactional(readOnly = true)
    // Liệt kê các lớp thuộc giảng viên hiện tại theo bộ lọc và phân trang.
    public PageResponse<ClassResponse> listMyAssignedClasses(
            String status,
            String keyword,
            UUID courseId,
            int page,
            int size) {
        UserAccount trainer = currentUserService.requireAuthenticatedUser();

        String normalizedStatus = normalizeStatusFilter(status);
        String keywordPattern = normalizeKeyword(keyword);

        Page<ClassAdminProjection> result = classOfferingRepository.findTrainerAssignedClasses(
                trainer.getId(),
                courseId,
                normalizedStatus,
                keywordPattern,
                PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE)));

        return new PageResponse<>(
                result.stream().map(this::toResponse).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    @Transactional(readOnly = true)
    // Trả chi tiết lớp khi giảng viên hiện tại thực sự được phân công.
    public ClassResponse getMyAssignedClassDetail(UUID classId) {
        UserAccount trainer = currentUserService.requireAuthenticatedUser();

        ClassAdminProjection classDetail = classOfferingRepository
                .findTrainerAssignedClassDetail(classId, trainer.getId())
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Assigned class was not found"));

        return toResponse(classDetail);
    }

    // Chuyển projection lớp được phân công sang DTO hiển thị cho giảng viên.
    private ClassResponse toResponse(ClassAdminProjection classOffering) {
        long activeCount = classOffering.getActiveEnrollmentCount() == null
                ? 0
                : classOffering.getActiveEnrollmentCount();

        int maxStudents = classOffering.getMaxStudents() == null
                ? 0
                : classOffering.getMaxStudents();

        return new ClassResponse(
                classOffering.getId(),
                classOffering.getCourseId(),
                classOffering.getCourseTitle(),
                classOffering.getClassName(),
                classOffering.getTrainerId(),
                classOffering.getTrainerName(),
                classOffering.getMeetingUrl(),
                classOffering.getScheduleDescription(),
                classOffering.getPrice(),
                classOffering.getStartDate(),
                classOffering.getEndDate(),
                maxStudents,
                activeCount,
                Math.max(0, (long) maxStudents - activeCount),
                classOffering.getStatus(),
                classOffering.getCreatedAt(),
                classOffering.getUpdatedAt());
    }

    // Chuẩn hóa trạng thái lọc và từ chối giá trị ngoài hợp đồng.
    private String normalizeStatusFilter(String status) {
        String normalized = normalizeNullable(status);
        if (normalized == null) {
            return null;
        }

        try {
            return ClassStatus.valueOf(normalized.toUpperCase(Locale.ROOT))
                    .name()
                    .toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Class status must be upcoming, ongoing, completed, or cancelled");
        }
    }

    // Chuẩn hóa từ khóa tìm kiếm, bỏ chuỗi chỉ có khoảng trắng.
    private String normalizeKeyword(String keyword) {
        String normalized = normalizeNullable(keyword);
        if (normalized == null) {
            return null;
        }

        return "%" + normalized
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_") + "%";
    }

    // Chuẩn hóa chuỗi tùy chọn, biến giá trị rỗng thành null.
    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
