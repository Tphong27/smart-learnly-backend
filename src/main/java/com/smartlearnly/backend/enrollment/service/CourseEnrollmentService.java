package com.smartlearnly.backend.enrollment.service;

import com.smartlearnly.backend.common.api.PageResponse;
import com.smartlearnly.backend.common.audit.AuditAction;
import com.smartlearnly.backend.common.audit.AuditDomain;
import com.smartlearnly.backend.common.audit.AuditLogService;
import com.smartlearnly.backend.common.audit.AuditResult;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.course.dto.CategorySummaryResponse;
import com.smartlearnly.backend.course.entity.Course;
import com.smartlearnly.backend.course.entity.CourseStatus;
import com.smartlearnly.backend.course.repository.CourseRepository;
import com.smartlearnly.backend.enrollment.dto.EnrollmentHistoryResponse;
import com.smartlearnly.backend.enrollment.dto.EnrollmentResponse;
import com.smartlearnly.backend.enrollment.dto.EnrollmentStatusHistoryResponse;
import com.smartlearnly.backend.enrollment.dto.MyCourseResponse;
import com.smartlearnly.backend.enrollment.entity.CourseEnrollment;
import com.smartlearnly.backend.enrollment.entity.EnrollmentStatus;
import com.smartlearnly.backend.enrollment.entity.EnrollmentStatusHistory;
import com.smartlearnly.backend.enrollment.entity.EnrollmentTransitionSource;
import com.smartlearnly.backend.enrollment.repository.CourseEnrollmentRepository;
import com.smartlearnly.backend.enrollment.repository.EnrollmentHistoryProjection;
import com.smartlearnly.backend.enrollment.repository.EnrollmentStatusHistoryRepository;
import com.smartlearnly.backend.enrollment.repository.MyCourseProjection;
import com.smartlearnly.backend.payment.repository.SuccessfulPaymentRepository;
import com.smartlearnly.backend.user.entity.UserAccount;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CourseEnrollmentService {
    private static final int MAX_PAGE_SIZE = 100;

    private final CourseRepository courseRepository;
    private final CourseEnrollmentRepository courseEnrollmentRepository;
    private final EnrollmentStatusHistoryRepository enrollmentStatusHistoryRepository;
    private final SuccessfulPaymentRepository successfulPaymentRepository;
    private final CurrentUserService currentUserService;
    private final AuditLogService auditLogService;

    @Transactional
    public EnrollmentResponse enrollFree(UUID courseId) {
        UserAccount student = currentUserService.requireAuthenticatedUser();
        CourseEnrollment existing = courseEnrollmentRepository
                .findByCourseIdAndStudentIdForUpdate(courseId, student.getId())
                .orElse(null);

        if (hasAccess(existing)) {
            return toEnrollmentResponse(existing, true, false);
        }

        Course course = requirePublishedCourse(courseId);
        if (!isFree(course)) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FREE);
        }

        if (existing == null) {
            CourseEnrollment created = new CourseEnrollment();
            created.setCourseId(courseId);
            created.setStudentId(student.getId());
            created.setStatus(EnrollmentStatus.ACTIVE);
            CourseEnrollment saved = courseEnrollmentRepository.save(created);
            recordCourseTransition(
                    saved,
                    null,
                    EnrollmentStatus.ACTIVE,
                    EnrollmentTransitionSource.FREE_ENROLLMENT,
                    null,
                    "Initial free course enrollment"
            );
            auditLogService.recordUser(
                    student, AuditAction.ENROLLMENT_CREATED, AuditDomain.ENROLLMENT, AuditResult.SUCCESS,
                    "COURSE_ENROLLMENT", saved.getId().toString(), "Free course enrollment was created",
                    null, java.util.Map.of("status", EnrollmentStatus.ACTIVE.name()),
                    java.util.Map.of("courseId", courseId)
            );
            return toEnrollmentResponse(saved, false, false);
        }

        ensureReactivatable(existing.getStatus());
        EnrollmentStatus fromStatus = existing.getStatus();
        existing.setStatus(EnrollmentStatus.ACTIVE);
        CourseEnrollment saved = courseEnrollmentRepository.save(existing);
        recordCourseTransition(
                saved,
                fromStatus,
                EnrollmentStatus.ACTIVE,
                EnrollmentTransitionSource.FREE_ENROLLMENT,
                null,
                "Free enrollment reactivation"
        );
        auditLogService.recordUser(
                student, AuditAction.ENROLLMENT_REACTIVATED, AuditDomain.ENROLLMENT, AuditResult.SUCCESS,
                "COURSE_ENROLLMENT", saved.getId().toString(), "Course enrollment was reactivated",
                java.util.Map.of("status", fromStatus.name()),
                java.util.Map.of("status", EnrollmentStatus.ACTIVE.name()),
                java.util.Map.of("courseId", courseId)
        );
        return toEnrollmentResponse(saved, false, true);
    }

    @Transactional
    public EnrollmentResponse grantPaidCourseEnrollment(
            UUID studentId,
            UUID courseId,
            UUID transactionId
    ) {
        requireSuccessfulCoursePayment(transactionId, studentId, courseId);
        CourseEnrollment existing = courseEnrollmentRepository
                .findByCourseIdAndStudentIdForUpdate(courseId, studentId)
                .orElse(null);

        if (hasAccess(existing)) {
            return toEnrollmentResponse(existing, true, false);
        }

        requireExistingCourse(courseId);
        if (existing == null) {
            CourseEnrollment created = new CourseEnrollment();
            created.setCourseId(courseId);
            created.setStudentId(studentId);
            created.setStatus(EnrollmentStatus.ACTIVE);
            CourseEnrollment saved = courseEnrollmentRepository.save(created);
            recordCourseTransition(
                    saved,
                    null,
                    EnrollmentStatus.ACTIVE,
                    EnrollmentTransitionSource.PAYMENT_SUCCESS,
                    transactionId,
                    "Initial paid course enrollment"
            );
            auditLogService.recordSystem(
                    "payment-processing", AuditAction.ENROLLMENT_CREATED, AuditDomain.ENROLLMENT, AuditResult.SUCCESS,
                    "COURSE_ENROLLMENT", saved.getId().toString(), "Paid course enrollment was created",
                    java.util.Map.of("courseId", courseId, "studentId", studentId, "transactionId", transactionId),
                    "transaction:" + transactionId, null
            );
            return toEnrollmentResponse(saved, false, false);
        }

        ensureReactivatable(existing.getStatus());
        EnrollmentStatus fromStatus = existing.getStatus();
        existing.setStatus(EnrollmentStatus.ACTIVE);
        CourseEnrollment saved = courseEnrollmentRepository.save(existing);
        recordCourseTransition(
                saved,
                fromStatus,
                EnrollmentStatus.ACTIVE,
                EnrollmentTransitionSource.PAYMENT_SUCCESS,
                transactionId,
                "Paid course enrollment reactivation"
        );
        auditLogService.recordSystem(
                "payment-processing", AuditAction.ENROLLMENT_REACTIVATED, AuditDomain.ENROLLMENT, AuditResult.SUCCESS,
                "COURSE_ENROLLMENT", saved.getId().toString(), "Paid course enrollment was reactivated",
                java.util.Map.of("courseId", courseId, "studentId", studentId, "transactionId", transactionId),
                "transaction:" + transactionId, null
        );
        return toEnrollmentResponse(saved, false, true);
    }

    @Transactional(readOnly = true)
    public List<MyCourseResponse> getMyCourses() {
        UUID studentId = currentUserService.requireAuthenticatedUser().getId();
        return courseEnrollmentRepository.findActiveMyCourses(studentId)
                .stream()
                .map(this::toMyCourseResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<EnrollmentHistoryResponse> getHistory(int page, int size) {
        UUID studentId = currentUserService.requireAuthenticatedUser().getId();
        Page<EnrollmentHistoryProjection> history = courseEnrollmentRepository.findHistory(
                studentId,
                PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE))
        );
        return new PageResponse<>(
                history.stream().map(this::toHistoryResponse).toList(),
                history.getNumber(),
                history.getSize(),
                history.getTotalElements(),
                history.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public List<EnrollmentStatusHistoryResponse> getStatusHistory(UUID enrollmentId) {
        UserAccount actor = currentUserService.requireAuthenticatedUser();
        if (!isAdminOrTmo(actor)) {
            courseEnrollmentRepository.findByIdAndStudentId(enrollmentId, actor.getId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Enrollment was not found"));
        }
        else if (!courseEnrollmentRepository.existsById(enrollmentId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Enrollment was not found");
        }

        return enrollmentStatusHistoryRepository.findCourseHistory(enrollmentId)
                .stream()
                .map(this::toStatusHistoryResponse)
                .toList();
    }

    private Course requirePublishedCourse(UUID courseId) {
        Course course = courseRepository.findByIdAndDeletedAtIsNullForUpdate(courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Course not found"));
        if (course.getStatus() != CourseStatus.PUBLISHED) {
            throw new BusinessException(ErrorCode.COURSE_NOT_ENROLLABLE);
        }
        return course;
    }

    private Course requireExistingCourse(UUID courseId) {
        return courseRepository.findByIdAndDeletedAtIsNullForUpdate(courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Course not found"));
    }

    private void requireSuccessfulCoursePayment(UUID transactionId, UUID studentId, UUID courseId) {
        if (transactionId == null
                || !successfulPaymentRepository.existsForCourse(transactionId, studentId, courseId)) {
            throw new BusinessException(
                    ErrorCode.PAYMENT_NOT_SUCCESSFUL,
                    "A successful payment transaction is required"
            );
        }
    }

    private boolean isFree(Course course) {
        BigDecimal price = course.getPrice() == null ? BigDecimal.ZERO : course.getPrice();
        return Boolean.TRUE.equals(course.getFree()) || price.compareTo(BigDecimal.ZERO) == 0;
    }

    private boolean hasAccess(CourseEnrollment enrollment) {
        return enrollment != null
                && (enrollment.getStatus() == EnrollmentStatus.ACTIVE
                || enrollment.getStatus() == EnrollmentStatus.COMPLETED);
    }

    private void ensureReactivatable(EnrollmentStatus status) {
        if (status != EnrollmentStatus.CANCELLED && status != EnrollmentStatus.REFUNDED) {
            throw new BusinessException(
                    ErrorCode.ENROLLMENT_TRANSITION_INVALID,
                    "Only cancelled or refunded enrollments can be reactivated"
            );
        }
    }

    private void recordCourseTransition(
            CourseEnrollment enrollment,
            EnrollmentStatus fromStatus,
            EnrollmentStatus toStatus,
            EnrollmentTransitionSource source,
            UUID transactionId,
            String reason
    ) {
        EnrollmentStatusHistory history = new EnrollmentStatusHistory();
        history.setCourseEnrollmentId(enrollment.getId());
        history.setStudentId(enrollment.getStudentId());
        history.setFromStatus(fromStatus);
        history.setToStatus(toStatus);
        history.setSource(source);
        history.setTransactionId(transactionId);
        history.setReason(reason);
        enrollmentStatusHistoryRepository.save(history);
    }

    private EnrollmentResponse toEnrollmentResponse(
            CourseEnrollment enrollment,
            boolean alreadyEnrolled,
            boolean reactivated
    ) {
        return new EnrollmentResponse(
                enrollment.getId(),
                enrollment.getCourseId(),
                enrollment.getStatus().name(),
                enrollment.getEnrollmentDate(),
                alreadyEnrolled,
                reactivated
        );
    }

    private MyCourseResponse toMyCourseResponse(MyCourseProjection course) {
        return new MyCourseResponse(
                course.getId(),
                course.getTitle(),
                course.getSlug(),
                course.getDescription(),
                course.getPrice(),
                course.getAvatarUrl(),
                Boolean.TRUE.equals(course.getFeatured()),
                new CategorySummaryResponse(
                        course.getCategoryId(),
                        course.getCategoryName(),
                        course.getCategorySlug()
                ),
                course.getEnrollmentId(),
                course.getEnrollmentStatus(),
                course.getEnrollmentDate(),
                course.getCourseStatus(),
                course.getAccessBlockedAt() == null,
                course.getAccessBlockReason()
        );
    }

    private EnrollmentHistoryResponse toHistoryResponse(EnrollmentHistoryProjection enrollment) {
        return new EnrollmentHistoryResponse(
                enrollment.getEnrollmentId(),
                enrollment.getCourseId(),
                enrollment.getCourseTitle(),
                enrollment.getCourseSlug(),
                enrollment.getStatus(),
                enrollment.getEnrollmentDate(),
                enrollment.getUpdatedAt()
        );
    }

    private EnrollmentStatusHistoryResponse toStatusHistoryResponse(EnrollmentStatusHistory history) {
        return new EnrollmentStatusHistoryResponse(
                history.getId(),
                history.getFromStatus() == null ? null : history.getFromStatus().name(),
                history.getToStatus().name(),
                history.getSource().name(),
                history.getReason(),
                history.getTransactionId(),
                history.getChangedBy(),
                history.getCreatedAt()
        );
    }

    private boolean isAdminOrTmo(UserAccount user) {
        return "ADMIN".equalsIgnoreCase(user.getRole()) || "TMO".equalsIgnoreCase(user.getRole());
    }
}
