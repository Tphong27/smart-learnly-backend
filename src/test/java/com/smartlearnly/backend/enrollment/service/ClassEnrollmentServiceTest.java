package com.smartlearnly.backend.enrollment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.classroom.entity.ClassOffering;
import com.smartlearnly.backend.classroom.entity.ClassStatus;
import com.smartlearnly.backend.classroom.repository.ClassOfferingRepository;
import com.smartlearnly.backend.common.audit.AuditLogService;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.enrollment.entity.ClassEnrollment;
import com.smartlearnly.backend.enrollment.entity.EnrollmentStatus;
import com.smartlearnly.backend.enrollment.entity.EnrollmentStatusHistory;
import com.smartlearnly.backend.enrollment.repository.ClassEnrollmentRepository;
import com.smartlearnly.backend.enrollment.repository.EnrollmentStatusHistoryRepository;
import com.smartlearnly.backend.payment.repository.SuccessfulPaymentRepository;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClassEnrollmentServiceTest {
    @Mock
    private ClassOfferingRepository classOfferingRepository;
    @Mock
    private ClassEnrollmentRepository classEnrollmentRepository;
    @Mock
    private EnrollmentStatusHistoryRepository enrollmentStatusHistoryRepository;
    @Mock
    private SuccessfulPaymentRepository successfulPaymentRepository;
    @Mock
    private CourseEnrollmentService courseEnrollmentService;
    @Mock
    private AuditLogService auditLogService;

    private ClassEnrollmentService service;

    @BeforeEach
    void setUp() {
        service = new ClassEnrollmentService(
                classOfferingRepository,
                classEnrollmentRepository,
                enrollmentStatusHistoryRepository,
                successfulPaymentRepository,
                courseEnrollmentService,
                auditLogService
        );
    }

    @Test
    void successfulPaymentAndAvailableCapacityShouldCreateClassAndCourseEnrollment() {
        UUID studentId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        ClassOffering classOffering = classOffering(2);
        when(classOfferingRepository.findByIdForUpdate(classOffering.getId()))
                .thenReturn(Optional.of(classOffering));
        when(successfulPaymentRepository.existsForClass(
                transactionId,
                studentId,
                classOffering.getId()
        )).thenReturn(true);
        when(classEnrollmentRepository.findByClassIdAndStudentIdForUpdate(
                classOffering.getId(),
                studentId
        )).thenReturn(Optional.empty());
        when(classEnrollmentRepository.countByClassIdAndStatus(
                classOffering.getId(),
                "active"
        )).thenReturn(1L);
        when(classEnrollmentRepository.save(any(ClassEnrollment.class)))
                .thenAnswer(invocation -> {
                    ClassEnrollment enrollment = invocation.getArgument(0);
                    enrollment.setId(UUID.randomUUID());
                    return enrollment;
                });

        ClassEnrollment result = service.grantPaidClassEnrollment(
                studentId,
                classOffering.getId(),
                new BigDecimal("500000"),
                transactionId
        );

        assertThat(result.getStatus()).isEqualTo(EnrollmentStatus.ACTIVE);
        verify(courseEnrollmentService).grantPaidCourseEnrollment(
                studentId,
                classOffering.getCourseId(),
                transactionId
        );
        verify(enrollmentStatusHistoryRepository).save(any(EnrollmentStatusHistory.class));
    }

    @Test
    void classEnrollmentShouldRejectPaymentThatIsNotSuccessful() {
        UUID studentId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        ClassOffering classOffering = classOffering(2);
        when(classOfferingRepository.findByIdForUpdate(classOffering.getId()))
                .thenReturn(Optional.of(classOffering));
        when(successfulPaymentRepository.existsForClass(
                transactionId,
                studentId,
                classOffering.getId()
        )).thenReturn(false);

        assertThatThrownBy(() -> service.grantPaidClassEnrollment(
                studentId,
                classOffering.getId(),
                BigDecimal.TEN,
                transactionId
        ))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.PAYMENT_NOT_SUCCESSFUL));

        verify(classEnrollmentRepository, never()).save(any());
    }

    @Test
    void fullClassShouldRejectEnrollment() {
        UUID studentId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        ClassOffering classOffering = classOffering(1);
        when(classOfferingRepository.findByIdForUpdate(classOffering.getId()))
                .thenReturn(Optional.of(classOffering));
        when(successfulPaymentRepository.existsForClass(
                transactionId,
                studentId,
                classOffering.getId()
        )).thenReturn(true);
        when(classEnrollmentRepository.findByClassIdAndStudentIdForUpdate(
                classOffering.getId(),
                studentId
        )).thenReturn(Optional.empty());
        when(classEnrollmentRepository.countByClassIdAndStatus(
                classOffering.getId(),
                "active"
        )).thenReturn(1L);

        assertThatThrownBy(() -> service.grantPaidClassEnrollment(
                studentId,
                classOffering.getId(),
                BigDecimal.TEN,
                transactionId
        ))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.CLASS_FULL));
        verify(classEnrollmentRepository, never()).save(any());
        verify(courseEnrollmentService, never())
                .grantPaidCourseEnrollment(any(), any(), any());
    }

    @Test
    void refundedClassEnrollmentShouldReactivateWithPaymentAudit() {
        UUID studentId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        ClassOffering classOffering = classOffering(3);
        ClassEnrollment existing = new ClassEnrollment();
        existing.setId(UUID.randomUUID());
        existing.setClassId(classOffering.getId());
        existing.setStudentId(studentId);
        existing.setStatus(EnrollmentStatus.REFUNDED);
        when(classOfferingRepository.findByIdForUpdate(classOffering.getId()))
                .thenReturn(Optional.of(classOffering));
        when(successfulPaymentRepository.existsForClass(
                transactionId,
                studentId,
                classOffering.getId()
        )).thenReturn(true);
        when(classEnrollmentRepository.findByClassIdAndStudentIdForUpdate(
                classOffering.getId(),
                studentId
        )).thenReturn(Optional.of(existing));
        when(classEnrollmentRepository.countByClassIdAndStatus(
                classOffering.getId(),
                "active"
        )).thenReturn(0L);
        when(classEnrollmentRepository.save(existing)).thenReturn(existing);

        ClassEnrollment result = service.grantPaidClassEnrollment(
                studentId,
                classOffering.getId(),
                BigDecimal.TEN,
                transactionId
        );

        assertThat(result.getStatus()).isEqualTo(EnrollmentStatus.ACTIVE);
        ArgumentCaptor<EnrollmentStatusHistory> historyCaptor =
                ArgumentCaptor.forClass(EnrollmentStatusHistory.class);
        verify(enrollmentStatusHistoryRepository).save(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getFromStatus()).isEqualTo(EnrollmentStatus.REFUNDED);
        assertThat(historyCaptor.getValue().getTransactionId()).isEqualTo(transactionId);
    }

    private ClassOffering classOffering(int maxStudents) {
        ClassOffering classOffering = new ClassOffering();
        classOffering.setId(UUID.randomUUID());
        classOffering.setCourseId(UUID.randomUUID());
        classOffering.setMaxStudents(maxStudents);
        classOffering.setStatus(ClassStatus.UPCOMING);
        return classOffering;
    }
}
