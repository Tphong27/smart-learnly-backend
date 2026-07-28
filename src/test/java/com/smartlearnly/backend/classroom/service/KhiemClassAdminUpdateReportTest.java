package com.smartlearnly.backend.classroom.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.classroom.dto.ClassResponse;
import com.smartlearnly.backend.classroom.dto.CreateClassRequest;
import com.smartlearnly.backend.classroom.dto.RestoreClassRequest;
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
import com.smartlearnly.backend.course.entity.Course;
import com.smartlearnly.backend.course.entity.CourseStatus;
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

    @Test
    void UTCID_KHIEM_BE_497_create_persistsValidUpcomingClassAndBuildsSessions() {
        Course course = publishedCourse();
        UserAccount trainer = trainer();
        UserAccount actor = actor();
        LocalDate startDate = ClassLifecycle.today().plusDays(2);
        CreateClassRequest request = new CreateClassRequest(
                course.getId(),
                "  Java Cohort  ",
                trainer.getId(),
                " https://meet.google.com/abc-defg-hij ",
                ORIGINAL_SCHEDULE,
                startDate,
                startDate.plusMonths(1),
                30,
                new BigDecimal("500000"));
        when(currentUserService.requireAuthenticatedUser()).thenReturn(actor);
        when(courseRepository.findByIdAndDeletedAtIsNull(course.getId()))
                .thenReturn(Optional.of(course));
        when(userRepository.findActiveUserByIdAndRole(
                trainer.getId(), "TRAINER", "active"))
                .thenReturn(Optional.of(trainer));
        when(classOfferingRepository.saveAndFlush(any(ClassOffering.class)))
                .thenAnswer(invocation -> {
                    ClassOffering saved = invocation.getArgument(0);
                    saved.setId(UUID.randomUUID());
                    return saved;
                });

        ClassResponse response = service.create(request);

        assertThat(response.className()).isEqualTo("Java Cohort");
        assertThat(response.meetingUrl()).isEqualTo("https://meet.google.com/abc-defg-hij");
        assertThat(response.status()).isEqualTo("upcoming");
        assertThat(response.availableSeats()).isEqualTo(30);
        verify(classSessionScheduleService)
                .validateScheduleDefinition(any(ClassOffering.class));
        verify(classSessionScheduleService)
                .synchronizeFutureSessions(any(ClassOffering.class));
        verify(auditLogService).record(
                actor.getEmail(),
                "CLASS_CREATED",
                "CLASS",
                response.id().toString());
    }

    @Test
    void UTCID_KHIEM_BE_498_create_rejectsUnpublishedCourseBeforeSaving() {
        Course course = publishedCourse();
        course.setStatus(CourseStatus.DRAFT);
        LocalDate startDate = ClassLifecycle.today().plusDays(2);
        CreateClassRequest request = new CreateClassRequest(
                course.getId(),
                "Java Cohort",
                UUID.randomUUID(),
                "https://meet.google.com/abc-defg-hij",
                ORIGINAL_SCHEDULE,
                startDate,
                startDate.plusMonths(1),
                30,
                BigDecimal.ZERO);
        when(currentUserService.requireAuthenticatedUser()).thenReturn(actor());
        when(courseRepository.findByIdAndDeletedAtIsNull(course.getId()))
                .thenReturn(Optional.of(course));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOfSatisfying(BusinessException.class, error ->
                        assertThat(error.errorCode())
                                .isEqualTo(ErrorCode.BUSINESS_RULE_VIOLATION));

        verify(classOfferingRepository, never())
                .saveAndFlush(any(ClassOffering.class));
    }

    @Test
    void UTCID_KHIEM_BE_499_restore_rebuildsActiveCancelledClass() {
        ClassOffering classOffering = upcomingClass();
        classOffering.setStatus(ClassStatus.CANCELLED);
        Course course = publishedCourse();
        course.setId(classOffering.getCourseId());
        UserAccount trainer = trainer();
        trainer.setId(classOffering.getTrainerId());
        UserAccount actor = actor();
        LocalDate startDate = ClassLifecycle.today().plusDays(3);
        RestoreClassRequest request =
                new RestoreClassRequest(startDate, startDate.plusMonths(1));
        stubLockedClass(classOffering);
        when(classEnrollmentRepository.countByClassIdAndStatus(
                classOffering.getId(), "active")).thenReturn(2L);
        when(courseRepository.findByIdAndDeletedAtIsNull(classOffering.getCourseId()))
                .thenReturn(Optional.of(course));
        when(userRepository.findByIdAndDeletedAtIsNull(classOffering.getTrainerId()))
                .thenReturn(Optional.of(trainer));
        when(userRepository.findActiveUserByIdAndRole(
                classOffering.getTrainerId(), "TRAINER", "active"))
                .thenReturn(Optional.of(trainer));
        when(currentUserService.requireAuthenticatedUser()).thenReturn(actor);

        ClassResponse response = service.restore(classOffering.getId(), request);

        assertThat(response.status()).isEqualTo("upcoming");
        assertThat(response.activeEnrollmentCount()).isEqualTo(2);
        verify(classSessionScheduleService)
                .validateScheduleDefinition(classOffering);
        verify(classSessionScheduleService)
                .synchronizeFutureSessions(classOffering);
        verify(classSessionScheduleService, never())
                .deleteFutureSessions(classOffering.getId());
    }

    @Test
    void UTCID_KHIEM_BE_500_restore_completedDatesDeleteFutureSessions() {
        ClassOffering classOffering = upcomingClass();
        classOffering.setStatus(ClassStatus.CANCELLED);
        Course course = publishedCourse();
        course.setId(classOffering.getCourseId());
        UserAccount actor = actor();
        LocalDate endDate = ClassLifecycle.today().minusDays(1);
        RestoreClassRequest request =
                new RestoreClassRequest(endDate.minusMonths(1), endDate);
        stubLockedClass(classOffering);
        when(classEnrollmentRepository.countByClassIdAndStatus(
                classOffering.getId(), "active")).thenReturn(0L);
        when(courseRepository.findByIdAndDeletedAtIsNull(classOffering.getCourseId()))
                .thenReturn(Optional.of(course));
        when(userRepository.findByIdAndDeletedAtIsNull(classOffering.getTrainerId()))
                .thenReturn(Optional.empty());
        when(currentUserService.requireAuthenticatedUser()).thenReturn(actor);

        ClassResponse response = service.restore(classOffering.getId(), request);

        assertThat(response.status()).isEqualTo("completed");
        verify(classSessionScheduleService)
                .deleteFutureSessions(classOffering.getId());
        verify(classSessionScheduleService, never())
                .synchronizeFutureSessions(any(ClassOffering.class));
    }

    @Test
    void UTCID_KHIEM_BE_501_restore_rejectsClassThatIsNotCancelled() {
        ClassOffering classOffering = upcomingClass();
        stubLockedClass(classOffering);
        RestoreClassRequest request = new RestoreClassRequest(
                ClassLifecycle.today().plusDays(1),
                ClassLifecycle.today().plusMonths(1));

        assertThatThrownBy(() -> service.restore(classOffering.getId(), request))
                .isInstanceOfSatisfying(BusinessException.class, error -> {
                    assertThat(error.errorCode()).isEqualTo(ErrorCode.CONFLICT);
                    assertThat(error.getMessage()).contains("Only a cancelled class");
                });

        verify(classOfferingRepository, never())
                .saveAndFlush(any(ClassOffering.class));
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

    private UserAccount trainer() {
        UserAccount trainer = new UserAccount();
        trainer.setId(UUID.randomUUID());
        trainer.setFullName("Trainer");
        trainer.setEmail("trainer@smartlearnly.dev");
        trainer.setRole("TRAINER");
        trainer.setStatus("active");
        return trainer;
    }

    private Course publishedCourse() {
        Course course = new Course();
        course.setId(UUID.randomUUID());
        course.setTitle("Java Backend");
        course.setStatus(CourseStatus.PUBLISHED);
        return course;
    }
}
