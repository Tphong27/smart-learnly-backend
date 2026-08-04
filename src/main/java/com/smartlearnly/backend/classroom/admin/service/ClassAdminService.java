package com.smartlearnly.backend.classroom.admin.service;

import com.smartlearnly.backend.classroom.schedule.service.ClassSessionScheduleService;
import com.smartlearnly.backend.classroom.dto.ClassResponse;
import com.smartlearnly.backend.classroom.admin.dto.CreateClassRequest;
import com.smartlearnly.backend.classroom.admin.dto.UpdateClassRequest;
import com.smartlearnly.backend.classroom.admin.dto.RestoreClassRequest;
import com.smartlearnly.backend.classroom.admin.dto.ClassStatusOptionResponse;
import com.smartlearnly.backend.classroom.admin.repository.ClassStatusOptionProjection;
import com.smartlearnly.backend.classroom.entity.ClassOffering;
import com.smartlearnly.backend.classroom.entity.ClassStatus;
import com.smartlearnly.backend.classroom.entity.ClassLifecycle;
import com.smartlearnly.backend.classroom.repository.ClassAdminProjection;
import com.smartlearnly.backend.classroom.repository.ClassOfferingRepository;
import com.smartlearnly.backend.common.api.PageResponse;
import com.smartlearnly.backend.common.audit.AuditLogService;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.course.entity.Course;
import com.smartlearnly.backend.course.repository.CourseRepository;
import com.smartlearnly.backend.course.entity.CourseStatus;
import com.smartlearnly.backend.enrollment.repository.ClassEnrollmentRepository;
import com.smartlearnly.backend.notification.dto.NotificationCreateCommand;
import com.smartlearnly.backend.notification.entity.NotificationType;
import com.smartlearnly.backend.notification.service.NotificationPayloads;
import com.smartlearnly.backend.notification.service.NotificationService;
import com.smartlearnly.backend.user.entity.UserAccount;
import com.smartlearnly.backend.user.repository.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Objects;
import java.util.regex.Pattern;
import java.net.URI;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
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
    private NotificationService notificationService;
    private static final Pattern GOOGLE_MEET_PATH_PATTERN = Pattern.compile("^/[a-z]{3}-[a-z]{4}-[a-z]{3}/?$");

    @Autowired(required = false)
    void setNotificationService(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    // Trả danh sách trạng thái lớp hợp lệ cho biểu mẫu quản trị.
    public List<ClassStatusOptionResponse> listStatusOptions() {
        return classOfferingRepository.findClassStatusOptions()
                .stream()
                .map(status -> new ClassStatusOptionResponse(
                        status.getValue(),
                        status.getLabel()))
                .toList();
    }

    @Transactional(readOnly = true)
    // Liệt kê lớp học theo bộ lọc quản trị và phân trang ổn định.
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
    // Lấy chi tiết lớp chưa bị xóa mềm hoặc báo lỗi không tìm thấy.
    public ClassResponse get(UUID classId) {
        return getClassDetailResponse(classId);
    }

    @Transactional
    // Tạo lớp mới, kiểm tra khóa học/giảng viên và sinh các phiên học tương lai.
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
        emitClassNotificationToTrainer(
                saved,
                "New class assigned",
                "You have been assigned to " + saved.getClassName() + ".",
                "created");
        return toResponse(saved, course, trainer, 0);
    }

    @Transactional
    // Áp dụng các trường PATCH của lớp, kiểm tra quyền và đồng bộ phiên học nếu lịch đổi.
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

        emitClassNotificationToTrainerAndStudents(
                classOffering,
                "Class updated",
                classOffering.getClassName() + " was updated.",
                "updated");

        return getClassDetailResponse(classId);
    }

    @Transactional
    // Hủy lớp, ngừng các phiên tương lai và thông báo cho người liên quan.
    public ClassResponse cancel(UUID classId) {
        ClassOffering classOffering = findClassForUpdate(classId);
        ClassStatus effectiveStatus = ClassLifecycle.resolveStatus(
                classOffering.getStartDate(),
                classOffering.getEndDate(),
                classOffering.getStatus());

        if (effectiveStatus == ClassStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.CONFLICT, "A completed class cannot be cancelled");
        }

        if (effectiveStatus == ClassStatus.CANCELLED) {
            return toResponse(classOffering);
        }

        classOffering.setStatus(ClassStatus.CANCELLED);
        classOfferingRepository.saveAndFlush(classOffering);

        classSessionScheduleService.deleteFutureSessions(classId);

        audit("CLASS_CANCELLED", classId);

        emitClassNotificationToTrainerAndStudents(
                classOffering,
                "Class cancelled",
                classOffering.getClassName() + " was cancelled.",
                "cancelled");

        return toResponse(classOffering);
    }

    @Transactional
    // Khôi phục lớp đã hủy, tính lại lifecycle và tạo lại các phiên phù hợp.
    public ClassResponse restore(UUID classId, RestoreClassRequest request) {
        ClassOffering classOffering = findClassForUpdate(classId);

        if (classOffering.getStatus() != ClassStatus.CANCELLED) {
            throw new BusinessException(ErrorCode.CONFLICT, "Only a cancelled class can be restored");
        }

        validateRequiredDates(request.startDate(), request.endDate());
        classOffering.setStartDate(request.startDate());
        classOffering.setEndDate(request.endDate());

        ClassStatus restoredStatus = ClassLifecycle.resolveStatus(classOffering.getStartDate(),
                classOffering.getEndDate(), null);

        long activeCount = activeEnrollmentCount(classId);

        if (classOffering.getMaxStudents() == null || classOffering.getMaxStudents() < activeCount) {
            throw new BusinessException(
                    ErrorCode.CLASS_CAPACITY_INVALID,
                    "Capacity cannot be lower than the active enrollment count");
        }

        Course course = requireCourse(classOffering.getCourseId());

        UserAccount trainer = classOffering.getTrainerId() == null
                ? null
                : userRepository
                        .findByIdAndDeletedAtIsNull(classOffering.getTrainerId())
                        .orElse(null);

        boolean activeLifecycle = restoredStatus == ClassStatus.UPCOMING || restoredStatus == ClassStatus.ONGOING;

        if (activeLifecycle) {
            course = requirePublishedCourse(classOffering.getCourseId());
            trainer = requireTrainer(classOffering.getTrainerId());
            classOffering.setMeetingUrl(normalizeMeetingUrl(classOffering.getMeetingUrl()));

            classOffering.setScheduleDescription(normalizeRequired(
                    classOffering.getScheduleDescription(),
                    "Class schedule is required before restoring"));

            if (classOffering.getPrice() == null) {
                throw new BusinessException(
                        ErrorCode.INVALID_REQUEST,
                        "Class price is required before restoring");
            }

            classSessionScheduleService.validateScheduleDefinition(classOffering);
        }
        classOffering.setStatus(restoredStatus);
        classOfferingRepository.saveAndFlush(classOffering);

        if (activeLifecycle) {
            classSessionScheduleService.synchronizeFutureSessions(
                    classOffering);
        } else {
            classSessionScheduleService.deleteFutureSessions(classId);
        }

        audit("CLASS_RESTORED", classId);

        emitClassNotificationToTrainerAndStudents(
                classOffering,
                "Class restored",
                classOffering.getClassName() + " was restored.",
                "restored");

        return toResponse(classOffering, course, trainer, activeCount);
    }

    @Transactional
    // Xóa mềm lớp và dọn các phiên chưa diễn ra để giữ lịch sử.
    public void softDelete(UUID classId) {
        ClassOffering classOffering = findClassForUpdate(classId);
        classOffering.setStatus(ClassStatus.CANCELLED);
        classOffering.setDeletedAt(Instant.now());
        classOfferingRepository.save(classOffering);
        audit("CLASS_DELETED", classId);
    }

    // Lấy lớp với khóa cập nhật để tránh ghi đè thay đổi đồng thời.
    private ClassOffering findClassForUpdate(UUID classId) {
        return classOfferingRepository.findByIdForUpdate(classId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Class was not found"));
    }

    // Kiểm tra khóa học tồn tại trước khi gắn vào lớp.
    private Course requireCourse(UUID courseId) {
        return courseRepository.findByIdAndDeletedAtIsNull(courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Course was not found"));
    }

    // Kiểm tra người được gán là giảng viên hoạt động hợp lệ.
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

    // Chuyển entity lớp sang dữ liệu phản hồi quản trị với số học viên hiện tại.
    private ClassResponse toResponse(ClassOffering classOffering) {
        Course course = requireCourse(classOffering.getCourseId());
        UserAccount trainer = classOffering.getTrainerId() == null
                ? null
                : userRepository.findByIdAndDeletedAtIsNull(classOffering.getTrainerId()).orElse(null);
        return toResponse(classOffering, course, trainer, activeEnrollmentCount(classOffering.getId()));
    }

    // Chuyển entity lớp sang DTO khi số học viên đã được truy vấn sẵn.
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

    // Chỉ cho phép mở lớp cho khóa học đang xuất bản.
    private Course requirePublishedCourse(UUID courseId) {
        Course course = requireCourse(courseId);

        if (course.getStatus() != CourseStatus.PUBLISHED) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Only a published course can be assigned to a class");
        }

        return course;
    }

    // Chuyển projection truy vấn danh sách lớp sang DTO quản trị.
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

    // Đếm số ghi danh còn hiệu lực để áp dụng quy tắc capacity và cập nhật.
    private long activeEnrollmentCount(UUID classId) {
        return classEnrollmentRepository.countByClassIdAndStatus(classId, "active");
    }

    // Bắt buộc ngày bắt đầu/kết thúc và đảm bảo khoảng thời gian lớp hợp lệ.
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

    // Kiểm tra thay đổi nhạy cảm của lớp theo trạng thái, ghi danh và vai trò người sửa.
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

    // So sánh giá trị tiền tệ, coi null và 0 là cùng một mức giá.
    private boolean samePrice(BigDecimal first, BigDecimal second) {
        if (first == null || second == null) {
            return first == second;
        }

        return first.compareTo(second) == 0;
    }

    // Chuẩn hóa bộ lọc trạng thái và từ chối trạng thái không thuộc hợp đồng.
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

    // Chuẩn hóa từ khóa tìm kiếm, bỏ chuỗi rỗng.
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

    // Chuẩn hóa và kiểm tra URL Google Meet trước khi lưu vào lớp.
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

    // Tạo lỗi chuẩn dùng chung cho URL Google Meet không hợp lệ.
    private BusinessException invalidMeetingUrl() {
        return new BusinessException(
                ErrorCode.INVALID_REQUEST,
                "Meeting URL must use the format "
                        + "https://meet.google.com/abc-defg-hij");
    }

    // Chuẩn hóa chuỗi bắt buộc và báo lỗi nếu chỉ có khoảng trắng.
    private String normalizeRequired(String value, String message) {
        String normalized = normalizeNullable(value);
        if (normalized == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, message);
        }
        return normalized;
    }

    // Chuẩn hóa chuỗi tùy chọn, biến giá trị rỗng thành null.
    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    // Ghi audit thao tác quản trị lớp bằng người dùng đang đăng nhập.
    private void audit(String action, UUID classId) {
        UserAccount actor = currentUserService.requireAuthenticatedUser();
        auditLogService.record(actor.getEmail(), action, "CLASS", classId.toString());
    }

    // Gửi cùng một thông báo lớp cho giảng viên và các học viên liên quan.
    private void emitClassNotificationToTrainerAndStudents(
            ClassOffering classOffering,
            String title,
            String body,
            String eventSuffix) {
        emitClassNotificationToTrainer(classOffering, title, body, eventSuffix);
        if (notificationService == null || classOffering == null || classOffering.getId() == null) {
            return;
        }
        for (UUID studentId : classEnrollmentRepository.findActiveOrCompletedStudentIdsByClassId(classOffering.getId())) {
            emitClassNotification(studentId, classOffering, title, body, eventSuffix);
        }
    }

    // Gửi thông báo thay đổi lớp chỉ cho giảng viên được phân công.
    private void emitClassNotificationToTrainer(
            ClassOffering classOffering,
            String title,
            String body,
            String eventSuffix) {
        if (classOffering == null || classOffering.getTrainerId() == null) {
            return;
        }
        emitClassNotification(classOffering.getTrainerId(), classOffering, title, body, eventSuffix);
    }

    // Phát thông báo lớp nếu dịch vụ notification đang được cấu hình.
    private void emitClassNotification(
            UUID userId,
            ClassOffering classOffering,
            String title,
            String body,
            String eventSuffix) {
        if (notificationService == null || userId == null || classOffering == null) {
            return;
        }
        notificationService.emit(new NotificationCreateCommand(
                userId,
                NotificationType.CLASS,
                title,
                body,
                "CLASS",
                classOffering.getId(),
                "/classes/" + classOffering.getId(),
                null,
                "class:" + classOffering.getId() + ":" + eventSuffix,
                NotificationPayloads.of(
                        "classId", classOffering.getId(),
                        "courseId", classOffering.getCourseId(),
                        "className", classOffering.getClassName())));
    }

    // Lấy phản hồi chi tiết lớp từ projection quản trị hiện tại.
    private ClassResponse getClassDetailResponse(UUID classId) {
        ClassAdminProjection classDetail = classOfferingRepository
                .findAdminClassDetail(classId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Class was not found"));

        return toResponse(classDetail);
    }
}
