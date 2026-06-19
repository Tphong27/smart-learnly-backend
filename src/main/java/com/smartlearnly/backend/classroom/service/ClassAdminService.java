package com.smartlearnly.backend.classroom.service;

import com.smartlearnly.backend.classroom.dto.ClassResponse;
import com.smartlearnly.backend.classroom.dto.CreateClassRequest;
import com.smartlearnly.backend.classroom.dto.UpdateClassRequest;
import com.smartlearnly.backend.classroom.entity.ClassOffering;
import com.smartlearnly.backend.classroom.entity.ClassStatus;
import com.smartlearnly.backend.classroom.repository.ClassAdminProjection;
import com.smartlearnly.backend.classroom.repository.ClassOfferingRepository;
import com.smartlearnly.backend.common.api.PageResponse;
import com.smartlearnly.backend.common.audit.AuditLogService;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.course.entity.Course;
import com.smartlearnly.backend.course.repository.CourseRepository;
import com.smartlearnly.backend.enrollment.entity.EnrollmentStatus;
import com.smartlearnly.backend.enrollment.repository.ClassEnrollmentRepository;
import com.smartlearnly.backend.user.entity.UserAccount;
import com.smartlearnly.backend.user.repository.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ClassAdminService {
    private static final int MAX_PAGE_SIZE = 100;

    private final ClassOfferingRepository classOfferingRepository;
    private final ClassEnrollmentRepository classEnrollmentRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public PageResponse<ClassResponse> list(
            UUID courseId,
            UUID trainerId,
            String status,
            String keyword,
            int page,
            int size
    ) {
        String normalizedStatus = normalizeStatusFilter(status);
        String keywordPattern = normalizeKeyword(keyword);
        Page<ClassAdminProjection> result = classOfferingRepository.findAdminClasses(
                courseId,
                trainerId,
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
    public ClassResponse get(UUID classId) {
        return toResponse(findClass(classId));
    }

    @Transactional
    public ClassResponse create(CreateClassRequest request) {
        UserAccount actor = currentUserService.requireAuthenticatedUser();
        Course course = requireCourse(request.courseId());
        UserAccount trainer = requireTrainer(request.trainerId());
        validateDates(request.startDate(), request.endDate());

        ClassOffering classOffering = new ClassOffering();
        classOffering.setCourseId(course.getId());
        classOffering.setClassName(normalizeRequired(request.className(), "Class name is required"));
        classOffering.setTrainerId(trainer == null ? null : trainer.getId());
        classOffering.setScheduleDescription(normalizeNullable(request.scheduleDescription()));
        classOffering.setStartDate(request.startDate());
        classOffering.setEndDate(request.endDate());
        classOffering.setMaxStudents(request.maxStudents());
        classOffering.setStatus(ClassStatus.UPCOMING);
        classOffering.setCreatedBy(actor.getId());

        ClassOffering saved = classOfferingRepository.save(classOffering);
        auditLogService.record(actor.getEmail(), "CLASS_CREATED", "CLASS", saved.getId().toString());
        return toResponse(saved, course, trainer, 0);
    }

    @Transactional
    public ClassResponse update(UUID classId, UpdateClassRequest request) {
        if (!request.hasAnyField()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "At least one class field must be provided");
        }

        ClassOffering classOffering = findClassForUpdate(classId);
        if (classOffering.getStatus() == ClassStatus.CANCELLED) {
            throw new BusinessException(ErrorCode.CLASS_NOT_AVAILABLE, "Cancelled classes cannot be updated");
        }

        if (request.isCourseIdProvided()) {
            if (request.getCourseId() == null) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST, "Course is required");
            }
            if (!request.getCourseId().equals(classOffering.getCourseId())
                    && classOfferingRepository.hasCommercialHistory(classId)) {
                throw new BusinessException(
                        ErrorCode.CONFLICT,
                        "Course cannot be changed after the class has enrollment or commercial history"
                );
            }
            classOffering.setCourseId(requireCourse(request.getCourseId()).getId());
        }
        if (request.isClassNameProvided()) {
            classOffering.setClassName(normalizeRequired(request.getClassName(), "Class name must not be blank"));
        }
        if (request.isTrainerIdProvided()) {
            UserAccount trainer = requireTrainer(request.getTrainerId());
            classOffering.setTrainerId(trainer == null ? null : trainer.getId());
        }
        if (request.isScheduleDescriptionProvided()) {
            classOffering.setScheduleDescription(normalizeNullable(request.getScheduleDescription()));
        }
        if (request.isStartDateProvided()) {
            classOffering.setStartDate(request.getStartDate());
        }
        if (request.isEndDateProvided()) {
            classOffering.setEndDate(request.getEndDate());
        }
        if (request.isMaxStudentsProvided()) {
            if (request.getMaxStudents() == null) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST, "Maximum students is required");
            }
            long activeCount = activeEnrollmentCount(classId);
            if (request.getMaxStudents() < activeCount) {
                throw new BusinessException(
                        ErrorCode.CLASS_CAPACITY_INVALID,
                        "Maximum students cannot be lower than the active enrollment count"
                );
            }
            classOffering.setMaxStudents(request.getMaxStudents());
        }
        validateDates(classOffering.getStartDate(), classOffering.getEndDate());

        ClassOffering saved = classOfferingRepository.save(classOffering);
        audit("CLASS_UPDATED", classId);
        return toResponse(saved);
    }

    @Transactional
    public ClassResponse cancel(UUID classId) {
        ClassOffering classOffering = findClassForUpdate(classId);
        if (classOffering.getStatus() != ClassStatus.CANCELLED) {
            classOffering.setStatus(ClassStatus.CANCELLED);
            classOfferingRepository.save(classOffering);
            audit("CLASS_CANCELLED", classId);
        }
        return toResponse(classOffering);
    }

    @Transactional
    public void softDelete(UUID classId) {
        ClassOffering classOffering = findClassForUpdate(classId);
        classOffering.setStatus(ClassStatus.CANCELLED);
        classOffering.setDeletedAt(Instant.now());
        classOfferingRepository.save(classOffering);
        audit("CLASS_DELETED", classId);
    }

    private ClassOffering findClass(UUID classId) {
        return classOfferingRepository.findByIdAndDeletedAtIsNull(classId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Class was not found"));
    }

    private ClassOffering findClassForUpdate(UUID classId) {
        return classOfferingRepository.findByIdForUpdate(classId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Class was not found"));
    }

    private Course requireCourse(UUID courseId) {
        return courseRepository.findByIdAndDeletedAtIsNull(courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Course was not found"));
    }

    private UserAccount requireTrainer(UUID trainerId) {
        if (trainerId == null) {
            return null;
        }
        return userRepository.findByIdAndRoleIgnoreCaseAndStatusIgnoreCaseAndDeletedAtIsNull(
                        trainerId,
                        "TRAINER",
                        "active"
                )
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.INVALID_TRAINER,
                        "Trainer must exist, be active, and have the TRAINER role"
                ));
    }

    private ClassResponse toResponse(ClassOffering classOffering) {
        Course course = requireCourse(classOffering.getCourseId());
        UserAccount trainer = classOffering.getTrainerId() == null
                ? null
                : userRepository.findByIdAndDeletedAtIsNull(classOffering.getTrainerId()).orElse(null);
        return toResponse(classOffering, course, trainer, activeEnrollmentCount(classOffering.getId()));
    }

    private ClassResponse toResponse(
            ClassOffering classOffering,
            Course course,
            UserAccount trainer,
            long activeCount
    ) {
        return new ClassResponse(
                classOffering.getId(),
                classOffering.getCourseId(),
                course.getTitle(),
                classOffering.getClassName(),
                classOffering.getTrainerId(),
                trainer == null ? null : trainer.getFullName(),
                classOffering.getScheduleDescription(),
                classOffering.getStartDate(),
                classOffering.getEndDate(),
                classOffering.getMaxStudents(),
                activeCount,
                Math.max(0, (long) classOffering.getMaxStudents() - activeCount),
                classOffering.getStatus().name().toLowerCase(Locale.ROOT),
                classOffering.getCreatedAt(),
                classOffering.getUpdatedAt()
        );
    }

    private ClassResponse toResponse(ClassAdminProjection classOffering) {
        long activeCount = classOffering.getActiveEnrollmentCount() == null
                ? 0
                : classOffering.getActiveEnrollmentCount();
        return new ClassResponse(
                classOffering.getId(),
                classOffering.getCourseId(),
                classOffering.getCourseTitle(),
                classOffering.getClassName(),
                classOffering.getTrainerId(),
                classOffering.getTrainerName(),
                classOffering.getScheduleDescription(),
                classOffering.getStartDate(),
                classOffering.getEndDate(),
                classOffering.getMaxStudents(),
                activeCount,
                Math.max(0, (long) classOffering.getMaxStudents() - activeCount),
                classOffering.getStatus(),
                classOffering.getCreatedAt(),
                classOffering.getUpdatedAt()
        );
    }

    private long activeEnrollmentCount(UUID classId) {
        return classEnrollmentRepository.countByClassIdAndStatus(classId, EnrollmentStatus.ACTIVE);
    }

    private void validateDates(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "End date must not be before start date");
        }
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
        }
        catch (IllegalArgumentException exception) {
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

    private String normalizeRequired(String value, String message) {
        String normalized = normalizeNullable(value);
        if (normalized == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, message);
        }
        return normalized;
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private void audit(String action, UUID classId) {
        UserAccount actor = currentUserService.requireAuthenticatedUser();
        auditLogService.record(actor.getEmail(), action, "CLASS", classId.toString());
    }
}
