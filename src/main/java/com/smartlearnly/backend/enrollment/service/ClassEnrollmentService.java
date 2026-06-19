package com.smartlearnly.backend.enrollment.service;

import com.smartlearnly.backend.classroom.entity.ClassOffering;
import com.smartlearnly.backend.classroom.entity.ClassStatus;
import com.smartlearnly.backend.classroom.repository.ClassOfferingRepository;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.enrollment.entity.ClassEnrollment;
import com.smartlearnly.backend.enrollment.entity.EnrollmentStatus;
import com.smartlearnly.backend.enrollment.entity.EnrollmentStatusHistory;
import com.smartlearnly.backend.enrollment.entity.EnrollmentTransitionSource;
import com.smartlearnly.backend.enrollment.repository.ClassEnrollmentRepository;
import com.smartlearnly.backend.enrollment.repository.EnrollmentStatusHistoryRepository;
import com.smartlearnly.backend.payment.repository.SuccessfulPaymentRepository;
import java.math.BigDecimal;
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

    @Transactional
    public ClassEnrollment grantPaidClassEnrollment(
            UUID studentId,
            UUID classId,
            BigDecimal paidPrice,
            UUID transactionId
    ) {
        if (paidPrice != null && paidPrice.signum() < 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Paid price must not be negative");
        }

        ClassOffering classOffering = classOfferingRepository.findByIdForUpdate(classId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Class not found"));
        requireSuccessfulClassPayment(transactionId, studentId, classId);

        ClassEnrollment existing = classEnrollmentRepository
                .findByClassIdAndStudentIdForUpdate(classId, studentId)
                .orElse(null);
        if (hasAccess(existing)) {
            courseEnrollmentService.grantPaidCourseEnrollment(
                    studentId,
                    classOffering.getCourseId(),
                    transactionId
            );
            return existing;
        }

        if (classOffering.getStatus() == ClassStatus.CANCELLED
                || classOffering.getStatus() == ClassStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.CLASS_NOT_AVAILABLE);
        }

        if (existing != null) {
            ensureReactivatable(existing.getStatus());
        }

        long activeEnrollmentCount = classEnrollmentRepository.countByClassIdAndStatus(
                classId,
                EnrollmentStatus.ACTIVE
        );
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
        }
        else {
            enrollment = existing;
            fromStatus = existing.getStatus();
        }
        enrollment.setPrice(paidPrice == null ? BigDecimal.ZERO : paidPrice);
        enrollment.setStatus(EnrollmentStatus.ACTIVE);
        ClassEnrollment saved = classEnrollmentRepository.save(enrollment);
        recordClassTransition(saved, fromStatus, transactionId);

        courseEnrollmentService.grantPaidCourseEnrollment(
                studentId,
                classOffering.getCourseId(),
                transactionId
        );
        return saved;
    }

    private void requireSuccessfulClassPayment(UUID transactionId, UUID studentId, UUID classId) {
        if (transactionId == null
                || !successfulPaymentRepository.existsForClass(transactionId, studentId, classId)) {
            throw new BusinessException(
                    ErrorCode.PAYMENT_NOT_SUCCESSFUL,
                    "A successful payment transaction is required"
            );
        }
    }

    private boolean hasAccess(ClassEnrollment enrollment) {
        return enrollment != null
                && (enrollment.getStatus() == EnrollmentStatus.ACTIVE
                || enrollment.getStatus() == EnrollmentStatus.COMPLETED);
    }

    private void ensureReactivatable(EnrollmentStatus status) {
        if (status != EnrollmentStatus.CANCELLED && status != EnrollmentStatus.REFUNDED) {
            throw new BusinessException(
                    ErrorCode.ENROLLMENT_TRANSITION_INVALID,
                    "Only cancelled or refunded class enrollments can be reactivated"
            );
        }
    }

    private void recordClassTransition(
            ClassEnrollment enrollment,
            EnrollmentStatus fromStatus,
            UUID transactionId
    ) {
        EnrollmentStatusHistory history = new EnrollmentStatusHistory();
        history.setClassEnrollmentId(enrollment.getId());
        history.setStudentId(enrollment.getStudentId());
        history.setFromStatus(fromStatus);
        history.setToStatus(EnrollmentStatus.ACTIVE);
        history.setSource(EnrollmentTransitionSource.PAYMENT_SUCCESS);
        history.setTransactionId(transactionId);
        history.setReason(fromStatus == null
                ? "Initial paid class enrollment"
                : "Paid class enrollment reactivation");
        enrollmentStatusHistoryRepository.save(history);
    }
}
