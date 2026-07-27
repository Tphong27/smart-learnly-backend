package com.smartlearnly.backend.classroom.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.classroom.dto.ClassResponse;
import com.smartlearnly.backend.classroom.dto.UpdateClassRequest;
import com.smartlearnly.backend.classroom.entity.ClassLifecycle;
import com.smartlearnly.backend.classroom.entity.ClassOffering;
import com.smartlearnly.backend.classroom.entity.ClassStatus;
import com.smartlearnly.backend.classroom.repository.ClassAdminProjection;
import com.smartlearnly.backend.classroom.repository.ClassOfferingRepository;
import com.smartlearnly.backend.common.audit.AuditLogService;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.course.repository.CourseRepository;
import com.smartlearnly.backend.enrollment.repository.ClassEnrollmentRepository;
import com.smartlearnly.backend.user.entity.UserAccount;
import com.smartlearnly.backend.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KhiemClassAdminUpdateReportTest {

    private static final String ORIGINAL_SCHEDULE = """
            [
              {
                "dayOfWeek": "MONDAY",
                "slots": [
                  {
                    "startTime": "19:00",
                    "endTime": "21:00"
                  }
                ]
              }
            ]
            """;

    private static final String UPDATED_SCHEDULE = """
            [
              {
                "dayOfWeek": "WEDNESDAY",
                "slots": [
                  {
                    "startTime": "18:30",
                    "endTime": "20:30"
                  }
                ]
              }
            ]
            """;

    @Mock
    private ClassOfferingRepository classOfferingRepository;

    @Mock
    private ClassEnrollmentRepository classEnrollmentRepository;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private ClassSessionScheduleService classSessionScheduleService;

    private ClassAdminService service;

    @BeforeEach
    void setUp() {
        service = new ClassAdminService(
                classOfferingRepository,
                classEnrollmentRepository,
                courseRepository,
                userRepository,
                currentUserService,
                auditLogService,
                classSessionScheduleService);
    }

    @Test
    void UTCID_KHIEM_BE_462_update_rejectsEmptyPatch() {
        UpdateClassRequest request = new UpdateClassRequest();

        assertThatThrownBy(() -> service.update(UUID.randomUUID(), request))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        error -> {
                            assertThat(error.errorCode())
                                    .isEqualTo(ErrorCode.INVALID_REQUEST);
                            assertThat(error.getMessage())
                                    .isEqualTo("At least one class field must be provided");
                        });

        verify(classOfferingRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void UTCID_KHIEM_BE_463_update_rejectsManualLifecycleStatus() {
        ClassOffering classOffering = upcomingClass();
        UpdateClassRequest request = new UpdateClassRequest();
        request.setStatus("cancelled");
        stubLockedClass(classOffering);

        assertThatThrownBy(() -> service.update(classOffering.getId(), request))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        error -> {
                            assertThat(error.errorCode())
                                    .isEqualTo(ErrorCode.INVALID_REQUEST);
                            assertThat(error.getMessage())
                                    .contains("Class status is updated automatically");
                        });

        verify(classOfferingRepository, never())
                .saveAndFlush(any(ClassOffering.class));
    }

    @Test
    void UTCID_KHIEM_BE_464_update_rejectsCapacityBelowActiveEnrollment() {
        ClassOffering classOffering = upcomingClass();
        UpdateClassRequest request = new UpdateClassRequest();
        request.setMaxStudents(2);
        stubLockedClass(classOffering);
        when(classEnrollmentRepository.countByClassIdAndStatus(
                classOffering.getId(),
                "active"))
                .thenReturn(3L);

        assertThatThrownBy(() -> service.update(classOffering.getId(), request))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        error -> assertThat(error.errorCode())
                                .isEqualTo(ErrorCode.CLASS_CAPACITY_INVALID));

        verify(classOfferingRepository, never())
                .saveAndFlush(any(ClassOffering.class));
    }

    @Test
    void UTCID_KHIEM_BE_465_update_rejectsPriceChangeAfterCommercialHistory() {
        ClassOffering classOffering = upcomingClass();
        UpdateClassRequest request = new UpdateClassRequest();
        request.setPrice(new BigDecimal("750000"));
        stubLockedClass(classOffering);
        when(classOfferingRepository.hasCommercialHistory(classOffering.getId()))
                .thenReturn(true);

        assertThatThrownBy(() -> service.update(classOffering.getId(), request))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        error -> {
                            assertThat(error.errorCode())
                                    .isEqualTo(ErrorCode.CONFLICT);
                            assertThat(error.getMessage())
                                    .contains("price cannot be changed");
                        });

        verify(classOfferingRepository, never())
                .saveAndFlush(any(ClassOffering.class));
    }

    @Test
    void UTCID_KHIEM_BE_466_update_resynchronizesSessionsWhenScheduleChanges() {
        ClassOffering classOffering = upcomingClass();
        UpdateClassRequest request = new UpdateClassRequest();
        request.setScheduleDescription(UPDATED_SCHEDULE);
        UserAccount actor = actor();
        stubLockedClass(classOffering);
        when(currentUserService.requireAuthenticatedUser()).thenReturn(actor);
        stubDetail(classOffering, UPDATED_SCHEDULE, "upcoming");

        ClassResponse response = service.update(classOffering.getId(), request);

        assertThat(response.scheduleDescription()).isEqualTo(UPDATED_SCHEDULE);
        assertThat(response.status()).isEqualTo("upcoming");
        verify(classSessionScheduleService)
                .validateScheduleDefinition(classOffering);
        verify(classOfferingRepository).saveAndFlush(classOffering);
        verify(classSessionScheduleService)
                .synchronizeFutureSessions(classOffering);
        verify(classSessionScheduleService, never())
                .deleteFutureSessions(classOffering.getId());
        verify(auditLogService).record(
                actor.getEmail(),
                "CLASS_UPDATED",
                "CLASS",
                classOffering.getId().toString());
    }

    @Test
    void UTCID_KHIEM_BE_467_update_deletesFutureSessionsOnCompletedTransition() {
        ClassOffering classOffering = upcomingClass();
        LocalDate completedEnd = ClassLifecycle.today().minusDays(1);
        UpdateClassRequest request = new UpdateClassRequest();
        request.setStartDate(completedEnd.minusDays(20));
        request.setEndDate(completedEnd);
        UserAccount actor = actor();
        stubLockedClass(classOffering);
        when(currentUserService.requireAuthenticatedUser()).thenReturn(actor);
        stubDetail(classOffering, ORIGINAL_SCHEDULE, "completed");

        ClassResponse response = service.update(classOffering.getId(), request);

        assertThat(classOffering.getStatus()).isEqualTo(ClassStatus.COMPLETED);
        assertThat(response.status()).isEqualTo("completed");
        verify(classOfferingRepository).saveAndFlush(classOffering);
        verify(classSessionScheduleService)
                .deleteFutureSessions(classOffering.getId());
        verify(classSessionScheduleService, never())
                .validateScheduleDefinition(any(ClassOffering.class));
        verify(classSessionScheduleService, never())
                .synchronizeFutureSessions(any(ClassOffering.class));
    }

    private void stubLockedClass(ClassOffering classOffering) {
        when(classOfferingRepository.findByIdForUpdate(classOffering.getId()))
                .thenReturn(Optional.of(classOffering));
    }

    private void stubDetail(
            ClassOffering classOffering,
            String scheduleDescription,
            String status) {
        ClassAdminProjection projection = mock(ClassAdminProjection.class);
        when(projection.getId()).thenReturn(classOffering.getId());
        when(projection.getCourseId()).thenReturn(classOffering.getCourseId());
        when(projection.getCourseTitle()).thenReturn("Java Backend");
        when(projection.getClassName()).thenReturn(classOffering.getClassName());
        when(projection.getTrainerId()).thenReturn(classOffering.getTrainerId());
        when(projection.getTrainerName()).thenReturn("Trainer");
        when(projection.getMeetingUrl()).thenReturn(classOffering.getMeetingUrl());
        when(projection.getScheduleDescription()).thenReturn(scheduleDescription);
        when(projection.getPrice()).thenReturn(classOffering.getPrice());
        when(projection.getStartDate()).thenAnswer(
                ignored -> classOffering.getStartDate());
        when(projection.getEndDate()).thenAnswer(
                ignored -> classOffering.getEndDate());
        when(projection.getMaxStudents()).thenReturn(classOffering.getMaxStudents());
        when(projection.getActiveEnrollmentCount()).thenReturn(0L);
        when(projection.getStatus()).thenReturn(status);
        when(classOfferingRepository.findAdminClassDetail(classOffering.getId()))
                .thenReturn(Optional.of(projection));
    }

    private ClassOffering upcomingClass() {
        LocalDate startDate = ClassLifecycle.today().plusDays(2);
        ClassOffering classOffering = new ClassOffering();
        classOffering.setId(UUID.randomUUID());
        classOffering.setCourseId(UUID.randomUUID());
        classOffering.setClassName("Java Cohort");
        classOffering.setTrainerId(UUID.randomUUID());
        classOffering.setMeetingUrl("https://meet.google.com/abc-defg-hij");
        classOffering.setScheduleDescription(ORIGINAL_SCHEDULE);
        classOffering.setStartDate(startDate);
        classOffering.setEndDate(startDate.plusMonths(1));
        classOffering.setMaxStudents(30);
        classOffering.setPrice(new BigDecimal("500000"));
        classOffering.setStatus(ClassStatus.UPCOMING);
        return classOffering;
    }

    private UserAccount actor() {
        UserAccount actor = new UserAccount();
        actor.setId(UUID.randomUUID());
        actor.setEmail("khiem@smartlearnly.dev");
        return actor;
    }
}
