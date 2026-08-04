package com.smartlearnly.backend.classroom.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;

import com.smartlearnly.backend.classroom.dto.ClassResponse;
import com.smartlearnly.backend.classroom.dto.RestoreClassRequest;
import com.smartlearnly.backend.classroom.entity.ClassLifecycle;
import com.smartlearnly.backend.classroom.entity.ClassOffering;
import com.smartlearnly.backend.classroom.entity.ClassStatus;
import com.smartlearnly.backend.classroom.repository.ClassOfferingRepository;
import com.smartlearnly.backend.common.audit.AuditLogService;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.course.entity.Course;
import com.smartlearnly.backend.course.entity.CourseStatus;
import com.smartlearnly.backend.course.repository.CourseRepository;
import com.smartlearnly.backend.enrollment.repository.ClassEnrollmentRepository;
import com.smartlearnly.backend.notification.dto.NotificationCreateCommand;
import com.smartlearnly.backend.notification.entity.NotificationType;
import com.smartlearnly.backend.notification.service.NotificationService;
import com.smartlearnly.backend.user.entity.UserAccount;
import com.smartlearnly.backend.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClassAdminRestoreTest {

    private static final UUID CLASS_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID COURSE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID TRAINER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID ACTOR_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID STUDENT_ONE_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID STUDENT_TWO_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");

    private static final String MEETING_URL = "https://meet.google.com/abc-defg-hij";
    private static final String SCHEDULE = """
            [
              {
                "dayOfWeek": "MONDAY",
                "slots": [
                  {
                    "startTime": "19:30",
                    "endTime": "21:30"
                  }
                ]
              }
            ]
            """.strip();

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

    @Mock
    private NotificationService notificationService;

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
        service.setNotificationService(notificationService);
    }

    @Test
    void UTCID01_restore_restoresUpcomingClassNormalizesValuesAndRunsSideEffectsInOrder() {
        ClassOffering cancelled = cancelledClass();
        cancelled.setMeetingUrl("  " + MEETING_URL + "  ");
        cancelled.setScheduleDescription("  " + SCHEDULE + "  ");
        LocalDate startDate = ClassLifecycle.today().plusDays(7);
        RestoreClassRequest request = new RestoreClassRequest(startDate, startDate.plusMonths(1));
        UserAccount actor = actor();

        stubLockedClass(cancelled);
        when(classEnrollmentRepository.countByClassIdAndStatus(CLASS_ID, "active"))
                .thenReturn(2L);
        stubActiveLifecycleReferences(cancelled);
        when(currentUserService.requireAuthenticatedUser()).thenReturn(actor);
        when(classEnrollmentRepository.findActiveOrCompletedStudentIdsByClassId(CLASS_ID))
                .thenReturn(List.of(STUDENT_ONE_ID, STUDENT_TWO_ID));

        ClassResponse response = service.restore(CLASS_ID, request);

        ArgumentCaptor<NotificationCreateCommand> notificationCaptor = ArgumentCaptor
                .forClass(NotificationCreateCommand.class);
        InOrder order = inOrder(
                classSessionScheduleService,
                classOfferingRepository,
                auditLogService,
                notificationService);
        order.verify(classSessionScheduleService).validateScheduleDefinition(cancelled);
        order.verify(classOfferingRepository).saveAndFlush(cancelled);
        order.verify(classSessionScheduleService).synchronizeFutureSessions(cancelled);
        order.verify(auditLogService).record(
                actor.getEmail(),
                "CLASS_RESTORED",
                "CLASS",
                CLASS_ID.toString());
        order.verify(notificationService, times(3)).emit(notificationCaptor.capture());

        assertThat(cancelled.getStartDate()).isEqualTo(startDate);
        assertThat(cancelled.getEndDate()).isEqualTo(startDate.plusMonths(1));
        assertThat(cancelled.getStatus()).isEqualTo(ClassStatus.UPCOMING);
        assertThat(cancelled.getMeetingUrl()).isEqualTo(MEETING_URL);
        assertThat(cancelled.getScheduleDescription()).isEqualTo(SCHEDULE);

        assertThat(response.id()).isEqualTo(CLASS_ID);
        assertThat(response.courseId()).isEqualTo(COURSE_ID);
        assertThat(response.courseTitle()).isEqualTo("Java Backend");
        assertThat(response.className()).isEqualTo("Java Backend Cohort");
        assertThat(response.trainerId()).isEqualTo(TRAINER_ID);
        assertThat(response.trainerName()).isEqualTo("Java Trainer");
        assertThat(response.meetingUrl()).isEqualTo(MEETING_URL);
        assertThat(response.scheduleDescription()).isEqualTo(SCHEDULE);
        assertThat(response.price()).isEqualByComparingTo("500000");
        assertThat(response.startDate()).isEqualTo(startDate);
        assertThat(response.endDate()).isEqualTo(startDate.plusMonths(1));
        assertThat(response.maxStudents()).isEqualTo(30);
        assertThat(response.activeEnrollmentCount()).isEqualTo(2);
        assertThat(response.availableSeats()).isEqualTo(28);
        assertThat(response.status()).isEqualTo("upcoming");

        List<NotificationCreateCommand> notifications = notificationCaptor.getAllValues();
        assertThat(notifications)
                .extracting(NotificationCreateCommand::userId)
                .containsExactly(TRAINER_ID, STUDENT_ONE_ID, STUDENT_TWO_ID);
        assertThat(notifications).allSatisfy(notification -> {
            assertThat(notification.type()).isEqualTo(NotificationType.CLASS);
            assertThat(notification.title()).isEqualTo("Class restored");
            assertThat(notification.body()).isEqualTo("Java Backend Cohort was restored.");
            assertThat(notification.referenceType()).isEqualTo("CLASS");
            assertThat(notification.referenceId()).isEqualTo(CLASS_ID);
            assertThat(notification.actionUrl()).isEqualTo("/classes/" + CLASS_ID);
            assertThat(notification.eventKey()).isEqualTo("class:" + CLASS_ID + ":restored");
            assertThat(notification.payload())
                    .containsEntry("classId", CLASS_ID)
                    .containsEntry("courseId", COURSE_ID)
                    .containsEntry("className", "Java Backend Cohort");
        });
        verify(classSessionScheduleService, never()).deleteFutureSessions(any());
    }

    @Test
    void UTCID02_restore_restoresOngoingClassAndSynchronizesFutureSessions() {
        ClassOffering cancelled = cancelledClass();
        LocalDate startDate = ClassLifecycle.today().minusDays(3);
        RestoreClassRequest request = new RestoreClassRequest(
                startDate,
                ClassLifecycle.today().plusDays(20));

        stubActiveRestore(cancelled, 4L);

        ClassResponse response = service.restore(CLASS_ID, request);

        assertThat(cancelled.getStatus()).isEqualTo(ClassStatus.ONGOING);
        assertThat(response.status()).isEqualTo("ongoing");
        assertThat(response.activeEnrollmentCount()).isEqualTo(4);
        assertThat(response.availableSeats()).isEqualTo(26);
        verify(classSessionScheduleService).validateScheduleDefinition(cancelled);
        verify(classSessionScheduleService).synchronizeFutureSessions(cancelled);
        verify(classSessionScheduleService, never()).deleteFutureSessions(any());
    }

    @Test
    void UTCID03_restore_restoresCompletedClassAndDeletesFutureSessions() {
        ClassOffering cancelled = cancelledClass();
        LocalDate endDate = ClassLifecycle.today().minusDays(1);
        RestoreClassRequest request = new RestoreClassRequest(endDate.minusMonths(1), endDate);
        Course course = publishedCourse();
        UserAccount trainer = trainer();

        stubLockedClass(cancelled);
        when(classEnrollmentRepository.countByClassIdAndStatus(CLASS_ID, "active"))
                .thenReturn(3L);
        when(courseRepository.findByIdAndDeletedAtIsNull(COURSE_ID))
                .thenReturn(Optional.of(course));
        when(userRepository.findByIdAndDeletedAtIsNull(TRAINER_ID))
                .thenReturn(Optional.of(trainer));
        stubAuditAndNotificationTail();

        ClassResponse response = service.restore(CLASS_ID, request);

        assertThat(cancelled.getStatus()).isEqualTo(ClassStatus.COMPLETED);
        assertThat(response.status()).isEqualTo("completed");
        assertThat(response.trainerName()).isEqualTo("Java Trainer");
        verify(classSessionScheduleService, never()).validateScheduleDefinition(any());
        verify(classSessionScheduleService, never()).synchronizeFutureSessions(any());
        verify(classSessionScheduleService).deleteFutureSessions(CLASS_ID);
    }

    @Test
    void UTCID04_restore_restoresCompletedClassWithoutTrainer() {
        ClassOffering cancelled = cancelledClass();
        cancelled.setTrainerId(null);
        LocalDate endDate = ClassLifecycle.today().minusDays(1);
        RestoreClassRequest request = new RestoreClassRequest(endDate.minusDays(10), endDate);

        stubLockedClass(cancelled);
        when(classEnrollmentRepository.countByClassIdAndStatus(CLASS_ID, "active"))
                .thenReturn(0L);
        when(courseRepository.findByIdAndDeletedAtIsNull(COURSE_ID))
                .thenReturn(Optional.of(publishedCourse()));
        stubAuditAndNotificationTail();

        ClassResponse response = service.restore(CLASS_ID, request);

        assertThat(response.status()).isEqualTo("completed");
        assertThat(response.trainerId()).isNull();
        assertThat(response.trainerName()).isNull();
        verify(userRepository, never()).findByIdAndDeletedAtIsNull(any());
        verify(notificationService, never()).emit(any());
    }

    @Test
    void UTCID05_restore_returnsNullTrainerNameWhenHistoricalTrainerNoLongerExists() {
        ClassOffering cancelled = cancelledClass();
        LocalDate endDate = ClassLifecycle.today().minusDays(2);
        RestoreClassRequest request = new RestoreClassRequest(endDate.minusMonths(1), endDate);

        stubLockedClass(cancelled);
        when(classEnrollmentRepository.countByClassIdAndStatus(CLASS_ID, "active"))
                .thenReturn(0L);
        when(courseRepository.findByIdAndDeletedAtIsNull(COURSE_ID))
                .thenReturn(Optional.of(publishedCourse()));
        when(userRepository.findByIdAndDeletedAtIsNull(TRAINER_ID))
                .thenReturn(Optional.empty());
        stubAuditAndNotificationTail();

        ClassResponse response = service.restore(CLASS_ID, request);

        assertThat(response.status()).isEqualTo("completed");
        assertThat(response.trainerId()).isEqualTo(TRAINER_ID);
        assertThat(response.trainerName()).isNull();
    }

    @Test
    void UTCID06_restore_rejectsClassThatDoesNotExist() {
        when(classOfferingRepository.findByIdForUpdate(CLASS_ID))
                .thenReturn(Optional.empty());

        assertRestoreBusinessException(
                validUpcomingRequest(),
                ErrorCode.RESOURCE_NOT_FOUND,
                "Class was not found");

        assertNoPersistenceOrPostSaveSideEffects();
    }

    @Test
    void UTCID07_restore_rejectsClassThatIsNotCancelled() {
        ClassOffering classOffering = cancelledClass();
        classOffering.setStatus(ClassStatus.UPCOMING);
        stubLockedClass(classOffering);

        assertRestoreBusinessException(
                validUpcomingRequest(),
                ErrorCode.CONFLICT,
                "Only a cancelled class can be restored");

        verify(classEnrollmentRepository, never()).countByClassIdAndStatus(any(), any());
        assertNoPersistenceOrPostSaveSideEffects();
    }

    @Test
    void UTCID08_restore_rejectsNullStartDate() {
        stubLockedClass(cancelledClass());

        assertRestoreBusinessException(
                new RestoreClassRequest(null, ClassLifecycle.today().plusDays(20)),
                ErrorCode.INVALID_REQUEST,
                "Start date is required");

        assertNoPersistenceOrPostSaveSideEffects();
    }

    @Test
    void UTCID09_restore_rejectsNullEndDate() {
        stubLockedClass(cancelledClass());

        assertRestoreBusinessException(
                new RestoreClassRequest(ClassLifecycle.today().plusDays(5), null),
                ErrorCode.INVALID_REQUEST,
                "End date is required");

        assertNoPersistenceOrPostSaveSideEffects();
    }

    @Test
    void UTCID10_restore_rejectsEndDateBeforeStartDate() {
        stubLockedClass(cancelledClass());
        LocalDate startDate = ClassLifecycle.today().plusDays(10);

        assertRestoreBusinessException(
                new RestoreClassRequest(startDate, startDate.minusDays(1)),
                ErrorCode.INVALID_REQUEST,
                "End date must not be before start date");

        assertNoPersistenceOrPostSaveSideEffects();
    }

    @Test
    void UTCID11_restore_rejectsNullCapacity() {
        ClassOffering cancelled = cancelledClass();
        cancelled.setMaxStudents(null);
        stubLockedClass(cancelled);
        when(classEnrollmentRepository.countByClassIdAndStatus(CLASS_ID, "active"))
                .thenReturn(0L);

        assertRestoreBusinessException(
                validUpcomingRequest(),
                ErrorCode.CLASS_CAPACITY_INVALID,
                "Capacity cannot be lower than the active enrollment count");

        verify(courseRepository, never()).findByIdAndDeletedAtIsNull(any());
        assertNoPersistenceOrPostSaveSideEffects();
    }

    @Test
    void UTCID12_restore_rejectsCapacityBelowActiveEnrollmentCount() {
        ClassOffering cancelled = cancelledClass();
        cancelled.setMaxStudents(2);
        stubLockedClass(cancelled);
        when(classEnrollmentRepository.countByClassIdAndStatus(CLASS_ID, "active"))
                .thenReturn(3L);

        assertRestoreBusinessException(
                validUpcomingRequest(),
                ErrorCode.CLASS_CAPACITY_INVALID,
                "Capacity cannot be lower than the active enrollment count");

        verify(courseRepository, never()).findByIdAndDeletedAtIsNull(any());
        assertNoPersistenceOrPostSaveSideEffects();
    }

    @Test
    void UTCID13_restore_allowsCapacityEqualToActiveEnrollmentCount() {
        ClassOffering cancelled = cancelledClass();
        cancelled.setMaxStudents(3);
        stubActiveRestore(cancelled, 3L);

        ClassResponse response = service.restore(CLASS_ID, validUpcomingRequest());

        assertThat(response.activeEnrollmentCount()).isEqualTo(3);
        assertThat(response.availableSeats()).isZero();
        assertThat(response.status()).isEqualTo("upcoming");
    }

    @Test
    void UTCID14_restore_rejectsMissingCourse() {
        ClassOffering cancelled = cancelledClass();
        stubLockedClass(cancelled);
        when(classEnrollmentRepository.countByClassIdAndStatus(CLASS_ID, "active"))
                .thenReturn(0L);
        when(courseRepository.findByIdAndDeletedAtIsNull(COURSE_ID))
                .thenReturn(Optional.empty());

        assertRestoreBusinessException(
                validUpcomingRequest(),
                ErrorCode.RESOURCE_NOT_FOUND,
                "Course was not found");

        assertNoPersistenceOrPostSaveSideEffects();
    }

    @Test
    void UTCID15_restore_rejectsUnpublishedCourseForActiveLifecycle() {
        ClassOffering cancelled = cancelledClass();
        Course draftCourse = publishedCourse();
        draftCourse.setStatus(CourseStatus.DRAFT);
        stubLockedClass(cancelled);
        when(classEnrollmentRepository.countByClassIdAndStatus(CLASS_ID, "active"))
                .thenReturn(0L);
        when(courseRepository.findByIdAndDeletedAtIsNull(COURSE_ID))
                .thenReturn(Optional.of(draftCourse));
        when(userRepository.findByIdAndDeletedAtIsNull(TRAINER_ID))
                .thenReturn(Optional.of(trainer()));

        assertRestoreBusinessException(
                validUpcomingRequest(),
                ErrorCode.BUSINESS_RULE_VIOLATION,
                "Only a published course can be assigned to a class");

        verify(userRepository, never()).findActiveUserByIdAndRole(any(), any(), any());
        assertNoPersistenceOrPostSaveSideEffects();
    }

    @Test
    void UTCID16_restore_rejectsMissingTrainerSelectionForActiveLifecycle() {
        ClassOffering cancelled = cancelledClass();
        cancelled.setTrainerId(null);
        stubLockedClass(cancelled);
        when(classEnrollmentRepository.countByClassIdAndStatus(CLASS_ID, "active"))
                .thenReturn(0L);
        when(courseRepository.findByIdAndDeletedAtIsNull(COURSE_ID))
                .thenReturn(Optional.of(publishedCourse()));

        assertRestoreBusinessException(
                validUpcomingRequest(),
                ErrorCode.INVALID_TRAINER,
                "Please select a trainer");

        assertNoPersistenceOrPostSaveSideEffects();
    }

    @Test
    void UTCID17_restore_rejectsTrainerThatIsMissingInactiveOrNotTrainer() {
        ClassOffering cancelled = cancelledClass();
        stubLockedClass(cancelled);
        when(classEnrollmentRepository.countByClassIdAndStatus(CLASS_ID, "active"))
                .thenReturn(0L);
        when(courseRepository.findByIdAndDeletedAtIsNull(COURSE_ID))
                .thenReturn(Optional.of(publishedCourse()));
        when(userRepository.findByIdAndDeletedAtIsNull(TRAINER_ID))
                .thenReturn(Optional.of(trainer()));
        when(userRepository.findActiveUserByIdAndRole(TRAINER_ID, "TRAINER", "active"))
                .thenReturn(Optional.empty());

        assertRestoreBusinessException(
                validUpcomingRequest(),
                ErrorCode.INVALID_TRAINER,
                "Trainer must exist, be active, and have the TRAINER role");

        assertNoPersistenceOrPostSaveSideEffects();
    }

    @Test
    void UTCID18_restore_rejectsNullMeetingUrlForActiveLifecycle() {
        assertMeetingUrlRejected(null, "Google Meet URL is required");
    }

    @Test
    void UTCID19_restore_rejectsBlankMeetingUrlForActiveLifecycle() {
        assertMeetingUrlRejected("   ", "Google Meet URL is required");
    }

    @Test
    void UTCID20_restore_rejectsMeetingUrlLongerThan255Characters() {
        String tooLong = "https://meet.google.com/" + "a".repeat(232);
        assertThat(tooLong).hasSize(256);

        assertMeetingUrlRejected(
                tooLong,
                "Google Meet URL must not exceed 255 characters");
    }

    @Test
    void UTCID21_restore_rejectsInvalidMeetingUrlFormat() {
        assertMeetingUrlRejected(
                "http://meet.google.com/abc-defg-hij",
                "Meeting URL must use the format https://meet.google.com/abc-defg-hij");
    }

    @Test
    void UTCID22_restore_rejectsNullScheduleForActiveLifecycle() {
        assertScheduleRejected(null);
    }

    @Test
    void UTCID23_restore_rejectsBlankScheduleForActiveLifecycle() {
        assertScheduleRejected("   ");
    }

    @Test
    void UTCID24_restore_rejectsNullPriceForActiveLifecycle() {
        ClassOffering cancelled = cancelledClass();
        cancelled.setPrice(null);
        stubLockedClass(cancelled);
        when(classEnrollmentRepository.countByClassIdAndStatus(CLASS_ID, "active"))
                .thenReturn(0L);
        stubActiveLifecycleReferences(cancelled);

        assertRestoreBusinessException(
                validUpcomingRequest(),
                ErrorCode.INVALID_REQUEST,
                "Class price is required before restoring");

        verify(classSessionScheduleService, never()).validateScheduleDefinition(any());
        assertNoPersistenceOrPostSaveSideEffects();
    }

    @Test
    void UTCID25_restore_propagatesScheduleValidationFailureBeforeSaving() {
        ClassOffering cancelled = cancelledClass();
        BusinessException validationError = new BusinessException(
                ErrorCode.INVALID_REQUEST,
                "Class schedule contains an unsupported slot");
        stubLockedClass(cancelled);
        when(classEnrollmentRepository.countByClassIdAndStatus(CLASS_ID, "active"))
                .thenReturn(0L);
        stubActiveLifecycleReferences(cancelled);
        doThrow(validationError)
                .when(classSessionScheduleService)
                .validateScheduleDefinition(cancelled);

        assertThatThrownBy(() -> service.restore(CLASS_ID, validUpcomingRequest()))
                .isSameAs(validationError);

        assertNoPersistenceOrPostSaveSideEffects();
    }

    @Test
    void UTCID26_restore_propagatesSaveFailureBeforeSessionSynchronization() {
        ClassOffering cancelled = cancelledClass();
        RuntimeException saveError = new RuntimeException("database unavailable");
        stubLockedClass(cancelled);
        when(classEnrollmentRepository.countByClassIdAndStatus(CLASS_ID, "active"))
                .thenReturn(0L);
        stubActiveLifecycleReferences(cancelled);
        doThrow(saveError).when(classOfferingRepository).saveAndFlush(cancelled);

        assertThatThrownBy(() -> service.restore(CLASS_ID, validUpcomingRequest()))
                .isSameAs(saveError);

        verify(classSessionScheduleService, never()).synchronizeFutureSessions(any());
        verify(auditLogService, never()).record(any(), any(), any(), any());
        verify(notificationService, never()).emit(any());
    }

    @Test
    void UTCID27_restore_propagatesSynchronizationFailureBeforeAudit() {
        ClassOffering cancelled = cancelledClass();
        RuntimeException synchronizationError = new RuntimeException("session synchronization failed");
        stubLockedClass(cancelled);
        when(classEnrollmentRepository.countByClassIdAndStatus(CLASS_ID, "active"))
                .thenReturn(0L);
        stubActiveLifecycleReferences(cancelled);
        doThrow(synchronizationError)
                .when(classSessionScheduleService)
                .synchronizeFutureSessions(cancelled);

        assertThatThrownBy(() -> service.restore(CLASS_ID, validUpcomingRequest()))
                .isSameAs(synchronizationError);

        verify(classOfferingRepository).saveAndFlush(cancelled);
        verify(auditLogService, never()).record(any(), any(), any(), any());
        verify(notificationService, never()).emit(any());
    }

    @Test
    void UTCID28_restore_propagatesDeleteFutureSessionsFailureForCompletedClass() {
        ClassOffering cancelled = cancelledClass();
        LocalDate endDate = ClassLifecycle.today().minusDays(1);
        RestoreClassRequest request = new RestoreClassRequest(endDate.minusMonths(1), endDate);
        RuntimeException deleteError = new RuntimeException("session deletion failed");
        stubLockedClass(cancelled);
        when(classEnrollmentRepository.countByClassIdAndStatus(CLASS_ID, "active"))
                .thenReturn(0L);
        when(courseRepository.findByIdAndDeletedAtIsNull(COURSE_ID))
                .thenReturn(Optional.of(publishedCourse()));
        when(userRepository.findByIdAndDeletedAtIsNull(TRAINER_ID))
                .thenReturn(Optional.of(trainer()));
        doThrow(deleteError)
                .when(classSessionScheduleService)
                .deleteFutureSessions(CLASS_ID);

        assertThatThrownBy(() -> service.restore(CLASS_ID, request))
                .isSameAs(deleteError);

        verify(classOfferingRepository).saveAndFlush(cancelled);
        verify(auditLogService, never()).record(any(), any(), any(), any());
        verify(notificationService, never()).emit(any());
    }

    // @Test
    // void UTCID29_restore_propagatesUnauthenticatedAuditFailureBeforeNotification() {
    //     ClassOffering cancelled = cancelledClass();
    //     BusinessException unauthenticated = new BusinessException(ErrorCode.UNAUTHENTICATED);
    //     stubLockedClass(cancelled);
    //     when(classEnrollmentRepository.countByClassIdAndStatus(CLASS_ID, "active"))
    //             .thenReturn(0L);
    //     stubActiveLifecycleReferences(cancelled);
    //     when(currentUserService.requireAuthenticatedUser()).thenThrow(unauthenticated);

    //     assertThatThrownBy(() -> service.restore(CLASS_ID, validUpcomingRequest()))
    //             .isSameAs(unauthenticated);

    //     verify(classOfferingRepository).saveAndFlush(cancelled);
    //     verify(classSessionScheduleService).synchronizeFutureSessions(cancelled);
    //     verify(auditLogService, never()).record(any(), any(), any(), any());
    //     verify(notificationService, never()).emit(any());
    // }

    // @Test
    // void UTCID30_restore_propagatesAuditStorageFailureBeforeNotification() {
    //     ClassOffering cancelled = cancelledClass();
    //     UserAccount actor = actor();
    //     RuntimeException auditError = new RuntimeException("audit storage failed");
    //     stubLockedClass(cancelled);
    //     when(classEnrollmentRepository.countByClassIdAndStatus(CLASS_ID, "active"))
    //             .thenReturn(0L);
    //     stubActiveLifecycleReferences(cancelled);
    //     when(currentUserService.requireAuthenticatedUser()).thenReturn(actor);
    //     doThrow(auditError).when(auditLogService).record(
    //             actor.getEmail(),
    //             "CLASS_RESTORED",
    //             "CLASS",
    //             CLASS_ID.toString());

    //     assertThatThrownBy(() -> service.restore(CLASS_ID, validUpcomingRequest()))
    //             .isSameAs(auditError);

    //     verify(notificationService, never()).emit(any());
    //     verify(classEnrollmentRepository, never())
    //             .findActiveOrCompletedStudentIdsByClassId(any());
    // }

    // @Test
    // void UTCID31_restore_succeedsWhenOptionalNotificationServiceIsUnavailable() {
    //     ClassOffering cancelled = cancelledClass();
    //     service = new ClassAdminService(
    //             classOfferingRepository,
    //             classEnrollmentRepository,
    //             courseRepository,
    //             userRepository,
    //             currentUserService,
    //             auditLogService,
    //             classSessionScheduleService);
    //     stubLockedClass(cancelled);
    //     when(classEnrollmentRepository.countByClassIdAndStatus(CLASS_ID, "active"))
    //             .thenReturn(0L);
    //     stubActiveLifecycleReferences(cancelled);
    //     when(currentUserService.requireAuthenticatedUser()).thenReturn(actor());

    //     ClassResponse response = service.restore(CLASS_ID, validUpcomingRequest());

    //     assertThat(response.status()).isEqualTo("upcoming");
    //     verify(classEnrollmentRepository, never())
    //             .findActiveOrCompletedStudentIdsByClassId(any());
    //     verify(notificationService, never()).emit(any());
    // }

    // @Test
    // void UTCID32_restore_propagatesNotificationFailureAfterAudit() {
    //     ClassOffering cancelled = cancelledClass();
    //     UserAccount actor = actor();
    //     RuntimeException notificationError = new RuntimeException("notification delivery failed");
    //     stubLockedClass(cancelled);
    //     when(classEnrollmentRepository.countByClassIdAndStatus(CLASS_ID, "active"))
    //             .thenReturn(0L);
    //     stubActiveLifecycleReferences(cancelled);
    //     when(currentUserService.requireAuthenticatedUser()).thenReturn(actor);
    //     doThrow(notificationError)
    //             .when(notificationService)
    //             .emit(any(NotificationCreateCommand.class));

    //     assertThatThrownBy(() -> service.restore(CLASS_ID, validUpcomingRequest()))
    //             .isSameAs(notificationError);

    //     verify(auditLogService).record(
    //             actor.getEmail(),
    //             "CLASS_RESTORED",
    //             "CLASS",
    //             CLASS_ID.toString());
    //     verify(classEnrollmentRepository, never())
    //             .findActiveOrCompletedStudentIdsByClassId(any());
    // }

    // @Test
    // void UTCID33_restore_propagatesStudentNotificationFailureAfterTrainerNotification() {
    //     ClassOffering cancelled = cancelledClass();
    //     UserAccount actor = actor();
    //     RuntimeException notificationError = new RuntimeException("student notification delivery failed");

    //     stubLockedClass(cancelled);

    //     when(classEnrollmentRepository.countByClassIdAndStatus(CLASS_ID, "active"))
    //             .thenReturn(0L);

    //     stubActiveLifecycleReferences(cancelled);

    //     when(currentUserService.requireAuthenticatedUser())
    //             .thenReturn(actor);

    //     when(classEnrollmentRepository.findActiveOrCompletedStudentIdsByClassId(CLASS_ID))
    //             .thenReturn(List.of(STUDENT_ONE_ID));

    //     doAnswer(invocation -> {
    //         NotificationCreateCommand command = invocation.getArgument(0);

    //         if (STUDENT_ONE_ID.equals(command.userId())) {
    //             throw notificationError;
    //         }

    //         return null;
    //     })
    //             .when(notificationService)
    //             .emit(any(NotificationCreateCommand.class));

    //     assertThatThrownBy(
    //             () -> service.restore(CLASS_ID, validUpcomingRequest()))
    //             .isSameAs(notificationError);

    //     ArgumentCaptor<NotificationCreateCommand> notificationCaptor = ArgumentCaptor
    //             .forClass(NotificationCreateCommand.class);

    //     verify(notificationService, times(2))
    //             .emit(notificationCaptor.capture());

    //     assertThat(notificationCaptor.getAllValues())
    //             .extracting(NotificationCreateCommand::userId)
    //             .containsExactly(TRAINER_ID, STUDENT_ONE_ID);
    // }

    private void assertMeetingUrlRejected(String meetingUrl, String expectedMessage) {
        ClassOffering cancelled = cancelledClass();
        cancelled.setMeetingUrl(meetingUrl);
        stubLockedClass(cancelled);
        when(classEnrollmentRepository.countByClassIdAndStatus(CLASS_ID, "active"))
                .thenReturn(0L);
        stubActiveLifecycleReferences(cancelled);

        assertRestoreBusinessException(
                validUpcomingRequest(),
                ErrorCode.INVALID_REQUEST,
                expectedMessage);

        verify(classSessionScheduleService, never()).validateScheduleDefinition(any());
        assertNoPersistenceOrPostSaveSideEffects();
    }

    private void assertScheduleRejected(String schedule) {
        ClassOffering cancelled = cancelledClass();
        cancelled.setScheduleDescription(schedule);
        stubLockedClass(cancelled);
        when(classEnrollmentRepository.countByClassIdAndStatus(CLASS_ID, "active"))
                .thenReturn(0L);
        stubActiveLifecycleReferences(cancelled);

        assertRestoreBusinessException(
                validUpcomingRequest(),
                ErrorCode.INVALID_REQUEST,
                "Class schedule is required before restoring");

        verify(classSessionScheduleService, never()).validateScheduleDefinition(any());
        assertNoPersistenceOrPostSaveSideEffects();
    }

    private void assertRestoreBusinessException(
            RestoreClassRequest request,
            ErrorCode expectedCode,
            String expectedMessage) {
        assertThatThrownBy(() -> service.restore(CLASS_ID, request))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        error -> {
                            assertThat(error.errorCode()).isEqualTo(expectedCode);
                            assertThat(error.getMessage()).isEqualTo(expectedMessage);
                        });
    }

    private void assertNoPersistenceOrPostSaveSideEffects() {
        verify(classOfferingRepository, never()).saveAndFlush(any());
        verify(classSessionScheduleService, never()).synchronizeFutureSessions(any());
        verify(classSessionScheduleService, never()).deleteFutureSessions(any());
        verify(auditLogService, never()).record(any(), any(), any(), any());
        verify(notificationService, never()).emit(any());
    }

    private void stubActiveRestore(ClassOffering cancelled, long activeCount) {
        stubLockedClass(cancelled);
        when(classEnrollmentRepository.countByClassIdAndStatus(CLASS_ID, "active"))
                .thenReturn(activeCount);
        stubActiveLifecycleReferences(cancelled);
        stubAuditAndNotificationTail();
    }

    private void stubActiveLifecycleReferences(ClassOffering cancelled) {
        Course course = publishedCourse();
        UserAccount trainer = trainer();
        when(courseRepository.findByIdAndDeletedAtIsNull(COURSE_ID))
                .thenReturn(Optional.of(course));
        when(userRepository.findByIdAndDeletedAtIsNull(cancelled.getTrainerId()))
                .thenReturn(Optional.of(trainer));
        when(userRepository.findActiveUserByIdAndRole(
                cancelled.getTrainerId(),
                "TRAINER",
                "active"))
                .thenReturn(Optional.of(trainer));
    }

    private void stubAuditAndNotificationTail() {
        when(currentUserService.requireAuthenticatedUser()).thenReturn(actor());
        when(classEnrollmentRepository.findActiveOrCompletedStudentIdsByClassId(CLASS_ID))
                .thenReturn(List.of());
    }

    private void stubLockedClass(ClassOffering classOffering) {
        when(classOfferingRepository.findByIdForUpdate(CLASS_ID))
                .thenReturn(Optional.of(classOffering));
    }

    private RestoreClassRequest validUpcomingRequest() {
        LocalDate startDate = ClassLifecycle.today().plusDays(5);
        return new RestoreClassRequest(startDate, startDate.plusMonths(1));
    }

    private ClassOffering cancelledClass() {
        ClassOffering classOffering = new ClassOffering();
        classOffering.setId(CLASS_ID);
        classOffering.setCourseId(COURSE_ID);
        classOffering.setClassName("Java Backend Cohort");
        classOffering.setTrainerId(TRAINER_ID);
        classOffering.setMeetingUrl(MEETING_URL);
        classOffering.setScheduleDescription(SCHEDULE);
        classOffering.setStartDate(ClassLifecycle.today().minusMonths(2));
        classOffering.setEndDate(ClassLifecycle.today().minusMonths(1));
        classOffering.setMaxStudents(30);
        classOffering.setPrice(new BigDecimal("500000"));
        classOffering.setStatus(ClassStatus.CANCELLED);
        return classOffering;
    }

    private Course publishedCourse() {
        Course course = new Course();
        course.setId(COURSE_ID);
        course.setTitle("Java Backend");
        course.setStatus(CourseStatus.PUBLISHED);
        return course;
    }

    private UserAccount trainer() {
        UserAccount trainer = new UserAccount();
        trainer.setId(TRAINER_ID);
        trainer.setEmail("trainer@smartlearnly.dev");
        trainer.setFullName("Java Trainer");
        trainer.setRole("TRAINER");
        trainer.setStatus("active");
        return trainer;
    }

    private UserAccount actor() {
        UserAccount actor = new UserAccount();
        actor.setId(ACTOR_ID);
        actor.setEmail("khiem@smartlearnly.dev");
        actor.setFullName("Khiem");
        return actor;
    }
}