package com.smartlearnly.backend.enrollment.service;

import com.smartlearnly.backend.classroom.entity.ClassOffering;
import com.smartlearnly.backend.classroom.entity.ClassStatus;
import com.smartlearnly.backend.classroom.repository.ClassOfferingRepository;
import com.smartlearnly.backend.common.audit.AuditAction;
import com.smartlearnly.backend.common.audit.AuditDomain;
import com.smartlearnly.backend.common.audit.AuditLogService;
import com.smartlearnly.backend.common.audit.AuditResult;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.enrollment.dto.ClassEnrollmentResponse;
import com.smartlearnly.backend.enrollment.entity.ClassEnrollment;
import com.smartlearnly.backend.enrollment.entity.EnrollmentStatus;
import com.smartlearnly.backend.enrollment.entity.EnrollmentStatusHistory;
import com.smartlearnly.backend.enrollment.entity.EnrollmentTransitionSource;
import com.smartlearnly.backend.enrollment.repository.ClassEnrollmentRepository;
import com.smartlearnly.backend.enrollment.repository.EnrollmentStatusHistoryRepository;
import com.smartlearnly.backend.payment.repository.SuccessfulPaymentRepository;
import com.smartlearnly.backend.user.entity.UserAccount;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ClassEnrollmentService {

    private final ClassOfferingRepository classOfferingRepository;
    private final ClassEnrollmentRepository classEnrollmentRepository;
    private final EnrollmentStatusHistoryRepository enrollmentStatusHistoryRepository;
    private final SuccessfulPaymentRepository successfulPaymentRepository;
    private final CourseEnrollmentService courseEnrollmentService;
    private final AuditLogService auditLogService;
    private final CurrentUserService currentUserService;

    @Transactional
    public ClassEnrollmentResponse enrollFreeClass(UUID classId) {
        UserAccount student = currentUserService.requireAuthenticatedUser();

        ClassOffering classOffering = classOfferingRepository
                .findByIdForUpdate(classId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Class not found"));

        ClassEnrollment existing = classEnrollmentRepository
                .findByClassIdAndStudentIdForUpdate(classId, student.getId())
                .orElse(null);

        if (hasAccess(existing)) {
            courseEnrollmentService.grantFreeClassCourseEnrollment(
                    student.getId(),
                    classOffering.getCourseId(),
                    classId);

            return toResponse(
                    existing,
                    classOffering.getCourseId(),
                    true,
                    false);
        }

        requireFreeClassAvailable(classOffering);

        if (existing != null) {
            ensureReactivatable(existing.getStatus());
        }

        long activeEnrollmentCount = classEnrollmentRepository.countByClassIdAndStatus(classId, "active");

        if (activeEnrollmentCount >= classOffering.getMaxStudents()) {
            throw new BusinessException(ErrorCode.CLASS_FULL);
        }

        ClassEnrollment enrollment;
        EnrollmentStatus fromStatus;

        if (existing == null) {
            enrollment = new ClassEnrollment();
            enrollment.setClassId(classId);
            enrollment.setStudentId(student.getId());
            fromStatus = null;
        } else {
            enrollment = existing;
            fromStatus = existing.getStatus();
        }

        enrollment.setPrice(BigDecimal.ZERO);
        enrollment.setStatus(EnrollmentStatus.ACTIVE);

        ClassEnrollment saved = classEnrollmentRepository.save(enrollment);

        recordClassTransition(
                saved,
                fromStatus,
                EnrollmentTransitionSource.FREE_ENROLLMENT,
                null,
                fromStatus == null
                        ? "Initial free class enrollment"
                        : "Free class enrollment reactivation");

        courseEnrollmentService.grantFreeClassCourseEnrollment(
                student.getId(),
                classOffering.getCourseId(),
                classId);

        AuditAction auditAction = fromStatus == null
                ? AuditAction.ENROLLMENT_CREATED
                : AuditAction.ENROLLMENT_REACTIVATED;

        auditLogService.recordUser(
                student,
                auditAction,
                AuditDomain.ENROLLMENT,
                AuditResult.SUCCESS,
                "CLASS_ENROLLMENT",
                saved.getId().toString(),
                "Free class enrollment was completed",
                fromStatus == null
                        ? null
                        : Map.of("status", fromStatus.name()),
                Map.of("status", EnrollmentStatus.ACTIVE.name()),
                Map.of(
                        "classId", classId,
                        "courseId", classOffering.getCourseId()));

        return toResponse(
                saved,
                classOffering.getCourseId(),
                false,
                fromStatus != null);
    }

    @Transactional
    public ClassEnrollment grantPaidClassEnrollment(
            UUID studentId,
            UUID classId,
            BigDecimal paidPrice,
            UUID transactionId) {

        if (paidPrice != null && paidPrice.signum() < 0) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Paid price must not be negative");
        }

        ClassOffering classOffering = classOfferingRepository
                .findByIdForUpdate(classId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Class not found"));

        requireSuccessfulClassPayment(
                transactionId,
                studentId,
                classId);

        ClassEnrollment existing = classEnrollmentRepository
                .findByClassIdAndStudentIdForUpdate(
                        classId,
                        studentId)
                .orElse(null);

        if (hasAccess(existing)) {
            courseEnrollmentService.grantPaidCourseEnrollment(
                    studentId,
                    classOffering.getCourseId(),
                    transactionId);

            return existing;
        }

        if (classOffering.getStatus() == ClassStatus.CANCELLED
                || classOffering.getStatus() == ClassStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.CLASS_NOT_AVAILABLE);
        }

        if (existing != null) {
            ensureReactivatable(existing.getStatus());
        }

        long activeEnrollmentCount = classEnrollmentRepository.countByClassIdAndStatus(classId, "active");
        if (activeEnrollmentCount >= classOffering.getMaxStudents()) {
            throw new BusinessException(ErrorCode.CLASS_FULL);
        }

        ClassEnrollment enrollment;
        EnrollmentStatus fromStatus;
        if (existing == null) {
            enrollment = new ClassEnrollment();
            enrollment.setClassId(classId);
            enrollment.setStudentId(studentId);
            fromStatus = null;
        } else {
            enrollment = existing;
            fromStatus = existing.getStatus();
        }
        enrollment.setPrice(paidPrice == null ? BigDecimal.ZERO : paidPrice);
        enrollment.setStatus(EnrollmentStatus.ACTIVE);
        ClassEnrollment saved = classEnrollmentRepository.save(enrollment);

        recordClassTransition(
                saved,
                fromStatus,
                EnrollmentTransitionSource.PAYMENT_SUCCESS,
                transactionId,
                fromStatus == null
                        ? "Initial paid class enrollment"
                        : "Paid class enrollment reactivation");

        courseEnrollmentService.grantPaidCourseEnrollment(
                studentId,
                classOffering.getCourseId(),
                transactionId);

        AuditAction auditAction = fromStatus == null
                ? AuditAction.ENROLLMENT_CREATED
                : AuditAction.ENROLLMENT_REACTIVATED;
        auditLogService.recordSystem(
                "payment-processing",
                auditAction,
                AuditDomain.ENROLLMENT,
                AuditResult.SUCCESS,
                "CLASS_ENROLLMENT",
                saved.getId().toString(),
                "Paid class enrollment was granted",
                Map.of(
                        "classId", classId,
                        "studentId", studentId,
                        "transactionId", transactionId),
                "transaction:" + transactionId,
                null);

        return saved;
    }

    private void requireSuccessfulClassPayment(UUID transactionId, UUID studentId, UUID classId) {
        if (transactionId == null || !successfulPaymentRepository.existsForClass(transactionId, studentId, classId)) {
            throw new BusinessException(
                    ErrorCode.PAYMENT_NOT_SUCCESSFUL,
                    "A successful payment transaction is required");
        }
    }

    private void requireFreeClassAvailable(
            ClassOffering classOffering) {

        if (classOffering.getStatus() != ClassStatus.UPCOMING) {
            throw new BusinessException(
                    ErrorCode.CLASS_NOT_AVAILABLE,
                    "Only upcoming classes can be registered");
        }

        if (classOffering.getStartDate() == null || classOffering.getStartDate().isBefore(LocalDate.now())) {
            throw new BusinessException(
                    ErrorCode.CLASS_NOT_AVAILABLE,
                    "Class registration is no longer available");
        }

        if (classOffering.getPrice() == null) {
            throw new BusinessException(
                    ErrorCode.CLASS_NOT_AVAILABLE,
                    "Class price is not configured");
        }

        if (classOffering.getPrice().signum() != 0) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Paid classes must use checkout");
        }
    }

    private boolean hasAccess(ClassEnrollment enrollment) {
        return enrollment != null
                && (enrollment.getStatus() == EnrollmentStatus.ACTIVE
                        || enrollment.getStatus() == EnrollmentStatus.COMPLETED);
    }

    private void ensureReactivatable(EnrollmentStatus status) {
        if (status != EnrollmentStatus.CANCELLED
                && status != EnrollmentStatus.REFUNDED) {

            throw new BusinessException(
                    ErrorCode.ENROLLMENT_TRANSITION_INVALID,
                    "Only cancelled or refunded class enrollments can be reactivated");
        }
    }

    private void recordClassTransition(
            ClassEnrollment enrollment,
            EnrollmentStatus fromStatus,
            EnrollmentTransitionSource source,
            UUID transactionId,
            String reason) {

        EnrollmentStatusHistory history = new EnrollmentStatusHistory();
        history.setClassEnrollmentId(enrollment.getId());
        history.setStudentId(enrollment.getStudentId());
        history.setFromStatus(fromStatus);
        history.setToStatus(EnrollmentStatus.ACTIVE);
        history.setSource(source);
        history.setTransactionId(transactionId);
        history.setReason(reason);

        enrollmentStatusHistoryRepository.save(history);
    }

    private ClassEnrollmentResponse toResponse(ClassEnrollment enrollment, UUID courseId, boolean alreadyEnrolled, boolean reactivated) {
        return new ClassEnrollmentResponse(
                enrollment.getId(),
                enrollment.getClassId(),
                courseId,
                enrollment.getStatus().name(),
                enrollment.getEnrollmentDate(),
                alreadyEnrolled,
                reactivated);
    }
}