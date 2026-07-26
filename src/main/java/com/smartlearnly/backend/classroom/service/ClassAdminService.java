package com.smartlearnly.backend.classroom.service;

import com.smartlearnly.backend.classroom.dto.ClassResponse;
import com.smartlearnly.backend.classroom.dto.CreateClassRequest;
import com.smartlearnly.backend.classroom.dto.UpdateClassRequest;
import com.smartlearnly.backend.classroom.entity.ClassOffering;
import com.smartlearnly.backend.classroom.entity.ClassStatus;
import com.smartlearnly.backend.classroom.entity.ClassLifecycle;
import com.smartlearnly.backend.classroom.repository.ClassAdminProjection;
import com.smartlearnly.backend.classroom.repository.ClassOfferingRepository;
import com.smartlearnly.backend.classroom.dto.ClassStatusOptionResponse;
import com.smartlearnly.backend.common.api.PageResponse;
import com.smartlearnly.backend.common.audit.AuditLogService;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.course.entity.Course;
import com.smartlearnly.backend.course.repository.CourseRepository;
import com.smartlearnly.backend.course.entity.CourseStatus;
import com.smartlearnly.backend.enrollment.repository.ClassEnrollmentRepository;
import com.smartlearnly.backend.user.entity.UserAccount;
import com.smartlearnly.backend.user.repository.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.List;
import java.util.UUID;
import java.util.Objects;
import java.util.regex.Pattern;
import java.net.URI;
import java.math.BigDecimal;
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
    private final ClassSessionScheduleService classSessionScheduleService;
    private static final Pattern GOOGLE_MEET_PATH_PATTERN = Pattern.compile("^/[a-z]{3}-[a-z]{4}-[a-z]{3}/?$");

    @Transactional(readOnly = true)
    public List<ClassStatusOptionResponse> listStatusOptions() {
        return classOfferingRepository.findClassStatusOptions()
                .stream()
                .map(status -> new ClassStatusOptionResponse(
                        status.getValue(),
                        status.getLabel()))
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<ClassResponse> list(
            UUID courseId,
            UUID trainerId,
            String status,
            String keyword,
            int page,
            int size) {
        String normalizedStatus = normalizeStatusFilter(status);
        String keywordPattern = normalizeKeyword(keyword);
        Page<ClassAdminProjection> result = classOfferingRepository.findAdminClasses(
                courseId,
                trainerId,
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
    public ClassResponse get(UUID classId) {
        return getClassDetailResponse(classId);
    }

    @Transactional
    public ClassResponse create(CreateClassRequest request) {
        UserAccount actor = currentUserService.requireAuthenticatedUser();
        Course course = requirePublishedCourse(request.courseId());
        UserAccount trainer = requireTrainer(request.trainerId());
        validateRequiredDates(request.startDate(), request.endDate());

        ClassOffering classOffering = new ClassOffering();
        classOffering.setCourseId(course.getId());
        classOffering.setClassName(normalizeRequired(request.className(), "Class name is required"));
        classOffering.setTrainerId(trainer.getId());
        classOffering.setMeetingUrl(normalizeMeetingUrl(request.meetingUrl()));
        classOffering.setScheduleDescription(normalizeNullable(request.scheduleDescription()));
        classOffering.setPrice(request.price());
        classOffering.setStartDate(request.startDate());
        classOffering.setEndDate(request.endDate());
        classOffering.setMaxStudents(request.maxStudents());
        classOffering.setStatus(ClassLifecycle.resolveStatus(request.startDate(), request.endDate(), null));
        classOffering.setCreatedBy(actor.getId());

        classSessionScheduleService.validateScheduleDefinition(classOffering);

        ClassOffering saved = classOfferingRepository.saveAndFlush(classOffering);
        classSessionScheduleService.synchronizeFutureSessions(saved);
        auditLogService.record(actor.getEmail(), "CLASS_CREATED", "CLASS", saved.getId().toString());
        return toResponse(saved, course, trainer, 0);
    }

    @Transactional
    public ClassResponse update(UUID classId, UpdateClassRequest request) {

        if (!request.hasAnyField()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "At least one class field must be provided");
        }

        ClassOffering classOffering = findClassForUpdate(classId);
        ClassStatus previousStatus = ClassLifecycle.resolveStatus(classOffering.getStartDate(),
                classOffering.getEndDate(), classOffering.getStatus());

        classOffering.setStatus(previousStatus);

        if (request.isStatusProvided()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "Class status is updated automatically from start date "
                            + "and end date. Use the cancel endpoint to cancel a class.");
        }

        validateUpdatePermissions(classOffering, request);

        String previousScheduleDescription = classOffering.getScheduleDescription();
        LocalDate previousStartDate = classOffering.getStartDate();
        LocalDate previousEndDate = classOffering.getEndDate();
        UUID previousTrainerId = classOffering.getTrainerId();

        /*
         * Course
         */
        if (request.isCourseIdProvided()) {
            UUID requestedCourseId = request.getCourseId();

            if (requestedCourseId == null) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST, "Course is required");
            }

            boolean courseChanged = !Objects.equals(classOffering.getCourseId(), requestedCourseId);

            if (courseChanged) {
                if (classOfferingRepository.hasCommercialHistory(classId)) {
                    throw new BusinessException(
                            ErrorCode.CONFLICT,
                            "Course cannot be changed after the class has enrollment or commercial history");
                }

                Course course = requirePublishedCourse(requestedCourseId);
                classOffering.setCourseId(course.getId());
            }
        }

        /*
         * Class name
         */
        if (request.isClassNameProvided()) {
            classOffering.setClassName(normalizeRequired(request.getClassName(), "Class name is required"));
        }

        /*
         * Trainer
         */
        if (request.isTrainerIdProvided()) {
            UserAccount trainer = requireTrainer(request.getTrainerId());
            classOffering.setTrainerId(trainer.getId());
        }

        /*
         * Google Meet URL
         */
        if (request.isMeetingUrlProvided()) {
            classOffering.setMeetingUrl(normalizeMeetingUrl(request.getMeetingUrl()));
        }

        /*
         * Weekly schedule
         */
        if (request.isScheduleDescriptionProvided()) {
            classOffering.setScheduleDescription(
                    normalizeRequired(request.getScheduleDescription(), "Class schedule is required"));
        }

        /*
         * Start and end dates
         */
        if (request.isStartDateProvided()) {
            classOffering.setStartDate(request.getStartDate());
        }

        if (request.isEndDateProvided()) {
            classOffering.setEndDate(request.getEndDate());
        }

        /*
         * Capacity
         */
        if (request.isMaxStudentsProvided()) {
            Integer requestedMaxStudents = request.getMaxStudents();

            if (requestedMaxStudents == null) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST, "Capacity is required");
            }

            long activeCount = activeEnrollmentCount(classId);

            if (requestedMaxStudents < activeCount) {
                throw new BusinessException(
                        ErrorCode.CLASS_CAPACITY_INVALID,
                        "Capacity cannot be lower than the active enrollment count");
            }

            classOffering.setMaxStudents(requestedMaxStudents);
        }

        /*
         * Class price
         */
        if (request.isPriceProvided()) {
            if (request.getPrice() == null) {
                throw new BusinessException(
                        ErrorCode.INVALID_REQUEST,
                        "Class price is required");
            }

            boolean priceChanged = classOffering.getPrice() == null
                    || classOffering.getPrice().compareTo(request.getPrice()) != 0;

            if (priceChanged && classOfferingRepository.hasCommercialHistory(classId)) {
                throw new BusinessException(
                        ErrorCode.CONFLICT,
                        "Class price cannot be changed after enrollment or payment history exists");
            }

            classOffering.setPrice(request.getPrice());
        }

        validateRequiredDates(classOffering.getStartDate(), classOffering.getEndDate());

        /*
         * UPCOMING, ONGOING and COMPLETED are derived values.
         * CANCELLED remains unchanged.
         */
        classOffering.setStatus(ClassLifecycle.resolveStatus(classOffering.getStartDate(), classOffering.getEndDate(),
                classOffering.getStatus()));

        boolean scheduleDefinitionChanged = !Objects.equals(
                previousScheduleDescription,
                classOffering.getScheduleDescription())
                || !Objects.equals(
                        previousStartDate,
                        classOffering.getStartDate())
                || !Objects.equals(
                        previousEndDate,
                        classOffering.getEndDate())
                || !Objects.equals(
                        previousTrainerId,
                        classOffering.getTrainerId());

        boolean terminalTransition = previousStatus != classOffering.getStatus()
                && (classOffering.getStatus() == ClassStatus.COMPLETED
                        || classOffering.getStatus() == ClassStatus.CANCELLED);

        /*
         * Validate before saving so an invalid definition never reaches
         * class_sessions.
         *
         * A terminal transition does not need a new future schedule because
         * all not-yet-started sessions will be deleted.
         */
        if (scheduleDefinitionChanged && !terminalTransition) {
            classSessionScheduleService.validateScheduleDefinition(classOffering);
        }

        /*
         * saveAndFlush is still inside the transaction. If session
         * synchronization fails, the complete update is rolled back.
         */
        classOfferingRepository.saveAndFlush(classOffering);

        if (terminalTransition) {
            classSessionScheduleService.deleteFutureSessions(classId);
        } else if (scheduleDefinitionChanged) {
            classSessionScheduleService.synchronizeFutureSessions(classOffering);
        }

        audit("CLASS_UPDATED", classId);

        return getClassDetailResponse(classId);
    }

    @Transactional
    public ClassResponse cancel(UUID classId) {
        ClassOffering classOffering = findClassForUpdate(classId);
        if (classOffering.getStatus() == ClassStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.CONFLICT, "A completed class cannot be cancelled");
        }
        if (classOffering.getStatus() != ClassStatus.CANCELLED) {
            classOffering.setStatus(ClassStatus.CANCELLED);
            classOfferingRepository.saveAndFlush(classOffering);
            classSessionScheduleService.deleteFutureSessions(classId);
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
            throw new BusinessException(
                    ErrorCode.INVALID_TRAINER,
                    "Please select a trainer");
        }

        return userRepository.findActiveUserByIdAndRole(
                trainerId,
                "TRAINER",
                "active")
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.INVALID_TRAINER,
                        "Trainer must exist, be active, and have the TRAINER role"));
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
            long activeCount) {
        return new ClassResponse(
                classOffering.getId(),
                classOffering.getCourseId(),
                course.getTitle(),
                classOffering.getClassName(),
                classOffering.getTrainerId(),
                trainer == null ? null : trainer.getFullName(),
                classOffering.getMeetingUrl(),
                classOffering.getScheduleDescription(),
                classOffering.getPrice(),
                classOffering.getStartDate(),
                classOffering.getEndDate(),
                classOffering.getMaxStudents(),
                activeCount,
                Math.max(0, (long) classOffering.getMaxStudents() - activeCount),
                classOffering.getStatus().name().toLowerCase(Locale.ROOT),
                classOffering.getCreatedAt(),
                classOffering.getUpdatedAt());
    }

    private Course requirePublishedCourse(UUID courseId) {
        Course course = requireCourse(courseId);

        if (course.getStatus() != CourseStatus.PUBLISHED) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Only a published course can be assigned to a class");
        }

        return course;
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
                classOffering.getMeetingUrl(),
                classOffering.getScheduleDescription(),
                classOffering.getPrice(),
                classOffering.getStartDate(),
                classOffering.getEndDate(),
                classOffering.getMaxStudents(),
                activeCount,
                Math.max(0, (long) classOffering.getMaxStudents() - activeCount),
                classOffering.getStatus(),
                classOffering.getCreatedAt(),
                classOffering.getUpdatedAt());
    }

    private long activeEnrollmentCount(UUID classId) {
        return classEnrollmentRepository.countByClassIdAndStatus(classId, "active");
    }

    private void validateRequiredDates(LocalDate startDate, LocalDate endDate) {
        if (startDate == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Start date is required");
        }
        if (endDate == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "End date is required");
        }
        if (endDate.isBefore(startDate)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "End date must not be before start date");
        }
    }

    private void validateUpdatePermissions(
            ClassOffering current,
            UpdateClassRequest request) {
        ClassStatus status = current.getStatus();

        if (status == ClassStatus.COMPLETED
                || status == ClassStatus.CANCELLED) {
            throw new BusinessException(
                    ErrorCode.CONFLICT,
                    "Completed or cancelled classes are read-only");
        }

        if (status != ClassStatus.ONGOING) {
            return;
        }

        if (request.isCourseIdProvided()
                && !Objects.equals(
                        current.getCourseId(),
                        request.getCourseId())) {
            throw new BusinessException(
                    ErrorCode.CONFLICT,
                    "Course cannot be changed while the class is ongoing");
        }

        if (request.isStartDateProvided()
                && !Objects.equals(
                        current.getStartDate(),
                        request.getStartDate())) {
            throw new BusinessException(
                    ErrorCode.CONFLICT,
                    "Start date cannot be changed while the class is ongoing");
        }

        if (request.isPriceProvided()
                && !samePrice(
                        current.getPrice(),
                        request.getPrice())) {
            throw new BusinessException(
                    ErrorCode.CONFLICT,
                    "Class price cannot be changed while the class is ongoing");
        }
    }

    private boolean samePrice(BigDecimal first, BigDecimal second) {
        if (first == null || second == null) {
            return first == second;
        }

        return first.compareTo(second) == 0;
    }

    private String normalizeStatusFilter(String status) {
        String normalized = normalizeNullable(status);
        if (normalized == null) {
            return null;
        }
        try {
            return ClassStatus.valueOf(normalized.toUpperCase(Locale.ROOT)).name().toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Class status must be upcoming, ongoing, completed, or cancelled");
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

    private String normalizeMeetingUrl(String value) {
        String normalized = normalizeNullable(value);

        if (normalized == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Google Meet URL is required");
        }

        if (normalized.length() > 255) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Google Meet URL must not exceed 255 characters");
        }

        try {
            URI uri = URI.create(normalized);
            boolean valid = "https".equalsIgnoreCase(uri.getScheme())
                    && "meet.google.com".equalsIgnoreCase(uri.getHost())
                    && uri.getPort() == -1
                    && uri.getRawUserInfo() == null
                    && uri.getRawFragment() == null
                    && uri.getPath() != null
                    && GOOGLE_MEET_PATH_PATTERN.matcher(uri.getPath()).matches();

            if (!valid) {
                throw invalidMeetingUrl();
            }

            return normalized;
        } catch (IllegalArgumentException exception) {
            throw invalidMeetingUrl();
        }
    }

    private BusinessException invalidMeetingUrl() {
        return new BusinessException(
                ErrorCode.INVALID_REQUEST,
                "Meeting URL must use the format "
                        + "https://meet.google.com/abc-defg-hij");
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

    private ClassResponse getClassDetailResponse(UUID classId) {
        ClassAdminProjection classDetail = classOfferingRepository
                .findAdminClassDetail(classId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Class was not found"));

        return toResponse(classDetail);
    }
}
