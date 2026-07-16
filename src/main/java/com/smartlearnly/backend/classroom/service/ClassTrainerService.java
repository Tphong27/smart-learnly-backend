package com.smartlearnly.backend.classroom.service;

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
    public PageResponse<ClassResponse> listMyAssignedClasses(
            String status,
            String keyword,
            int page,
            int size
    ) {
        UserAccount trainer = currentUserService.requireAuthenticatedUser();

        String normalizedStatus = normalizeStatusFilter(status);
        String keywordPattern = normalizeKeyword(keyword);

        Page<ClassAdminProjection> result = classOfferingRepository.findTrainerAssignedClasses(
                trainer.getId(),
                normalizedStatus,
                keywordPattern,
                PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE))
        );

        return new PageResponse<>(
                result.stream().map(this::toResponse).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public ClassResponse getMyAssignedClassDetail(UUID classId) {
        UserAccount trainer = currentUserService.requireAuthenticatedUser();

        ClassAdminProjection classDetail = classOfferingRepository
                .findTrainerAssignedClassDetail(classId, trainer.getId())
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Assigned class was not found"
                ));

        return toResponse(classDetail);
    }

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
                classOffering.getScheduleDescription(),
                classOffering.getPrice(),
                classOffering.getStartDate(),
                classOffering.getEndDate(),
                maxStudents,
                activeCount,
                Math.max(0, (long) maxStudents - activeCount),
                classOffering.getStatus(),
                classOffering.getCreatedAt(),
                classOffering.getUpdatedAt()
        );
    }

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
                    "Class status must be upcoming, ongoing, completed, or cancelled"
            );
        }
    }

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

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}