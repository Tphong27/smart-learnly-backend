package com.smartlearnly.backend.classroom.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.classroom.dto.ClassResponse;
import com.smartlearnly.backend.classroom.admin.dto.UpdateClassRequest;
import com.smartlearnly.backend.classroom.entity.ClassLifecycle;
import com.smartlearnly.backend.classroom.entity.ClassOffering;
import com.smartlearnly.backend.classroom.entity.ClassStatus;
import com.smartlearnly.backend.classroom.repository.ClassAdminProjection;
import com.smartlearnly.backend.classroom.repository.ClassOfferingRepository;
import com.smartlearnly.backend.classroom.schedule.service.ClassSessionScheduleService;
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
class ClassAdminUpdateTest {

        private static final UUID CLASS_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
        private static final UUID ORIGINAL_COURSE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
        private static final UUID UPDATED_COURSE_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
        private static final UUID ORIGINAL_TRAINER_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
        private static final UUID UPDATED_TRAINER_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
        private static final UUID ACTOR_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");
        private static final UUID STUDENT_ONE_ID = UUID.fromString("77777777-7777-7777-7777-777777777777");
        private static final UUID STUDENT_TWO_ID = UUID.fromString("88888888-8888-8888-8888-888888888888");

        private static final String ORIGINAL_MEETING_URL = "https://meet.google.com/abc-defg-hij";
        private static final String UPDATED_MEETING_URL = "https://meet.google.com/klm-nopq-rst";

        private static final String ORIGINAL_SCHEDULE = """
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
        void UTCID01_update_updatesAllFieldsNormalizesValuesAndRunsSideEffectsInOrder() {
                ClassOffering existing = upcomingClass();
                Course updatedCourse = publishedCourse(UPDATED_COURSE_ID);
                UserAccount updatedTrainer = trainer(UPDATED_TRAINER_ID, "Updated Trainer");
                UserAccount actor = actor();
                LocalDate updatedStart = ClassLifecycle.today().plusDays(10);
                LocalDate updatedEnd = updatedStart.plusMonths(2);

                UpdateClassRequest request = new UpdateClassRequest();
                request.setCourseId(UPDATED_COURSE_ID);
                request.setClassName("  Updated Java Backend Cohort  ");
                request.setTrainerId(UPDATED_TRAINER_ID);
                request.setMeetingUrl("  " + UPDATED_MEETING_URL + "  ");
                request.setScheduleDescription("  " + UPDATED_SCHEDULE + "  ");
                request.setStartDate(updatedStart);
                request.setEndDate(updatedEnd);
                request.setMaxStudents(30);
                request.setPrice(new BigDecimal("750000.00"));

                stubLockedClass(existing);
                when(classOfferingRepository.hasCommercialHistory(CLASS_ID))
                                .thenReturn(false);
                when(courseRepository.findByIdAndDeletedAtIsNull(UPDATED_COURSE_ID))
                                .thenReturn(Optional.of(updatedCourse));
                when(userRepository.findActiveUserByIdAndRole(
                                UPDATED_TRAINER_ID,
                                "TRAINER",
                                "active"))
                                .thenReturn(Optional.of(updatedTrainer));
                when(classEnrollmentRepository.countByClassIdAndStatus(
                                CLASS_ID,
                                "active"))
                                .thenReturn(30L);
                when(currentUserService.requireAuthenticatedUser())
                                .thenReturn(actor);
                when(classEnrollmentRepository.findActiveOrCompletedStudentIdsByClassId(CLASS_ID))
                                .thenReturn(List.of(STUDENT_ONE_ID, STUDENT_TWO_ID));
                stubDetailFromEntity(existing, "Updated Course", "Updated Trainer", 30L);

                ClassResponse response = service.update(CLASS_ID, request);

                ArgumentCaptor<NotificationCreateCommand> notificationCaptor = ArgumentCaptor
                                .forClass(NotificationCreateCommand.class);
                InOrder order = inOrder(
                                classSessionScheduleService,
                                classOfferingRepository,
                                auditLogService,
                                notificationService);

                order.verify(classSessionScheduleService)
                                .validateScheduleDefinition(existing);
                order.verify(classOfferingRepository)
                                .saveAndFlush(existing);
                order.verify(classSessionScheduleService)
                                .synchronizeFutureSessions(existing);
                order.verify(auditLogService)
                                .record(
                                                actor.getEmail(),
                                                "CLASS_UPDATED",
                                                "CLASS",
                                                CLASS_ID.toString());
                order.verify(notificationService, times(3))
                                .emit(notificationCaptor.capture());

                assertThat(existing.getCourseId()).isEqualTo(UPDATED_COURSE_ID);
                assertThat(existing.getClassName()).isEqualTo("Updated Java Backend Cohort");
                assertThat(existing.getTrainerId()).isEqualTo(UPDATED_TRAINER_ID);
                assertThat(existing.getMeetingUrl()).isEqualTo(UPDATED_MEETING_URL);
                assertThat(existing.getScheduleDescription()).isEqualTo(UPDATED_SCHEDULE);
                assertThat(existing.getStartDate()).isEqualTo(updatedStart);
                assertThat(existing.getEndDate()).isEqualTo(updatedEnd);
                assertThat(existing.getMaxStudents()).isEqualTo(30);
                assertThat(existing.getPrice()).isEqualByComparingTo("750000.00");
                assertThat(existing.getStatus()).isEqualTo(ClassStatus.UPCOMING);

                assertThat(response.id()).isEqualTo(CLASS_ID);
                assertThat(response.courseId()).isEqualTo(UPDATED_COURSE_ID);
                assertThat(response.courseTitle()).isEqualTo("Updated Course");
                assertThat(response.className()).isEqualTo("Updated Java Backend Cohort");
                assertThat(response.trainerId()).isEqualTo(UPDATED_TRAINER_ID);
                assertThat(response.trainerName()).isEqualTo("Updated Trainer");
                assertThat(response.meetingUrl()).isEqualTo(UPDATED_MEETING_URL);
                assertThat(response.scheduleDescription()).isEqualTo(UPDATED_SCHEDULE);
                assertThat(response.price()).isEqualByComparingTo("750000.00");
                assertThat(response.startDate()).isEqualTo(updatedStart);
                assertThat(response.endDate()).isEqualTo(updatedEnd);
                assertThat(response.maxStudents()).isEqualTo(30);
                assertThat(response.activeEnrollmentCount()).isEqualTo(30);
                assertThat(response.availableSeats()).isZero();
                assertThat(response.status()).isEqualTo("upcoming");

                List<NotificationCreateCommand> notifications = notificationCaptor.getAllValues();
                assertThat(notifications)
                                .extracting(NotificationCreateCommand::userId)
                                .containsExactly(UPDATED_TRAINER_ID, STUDENT_ONE_ID, STUDENT_TWO_ID);
                assertThat(notifications)
                                .allSatisfy(notification -> {
                                        assertThat(notification.type()).isEqualTo(NotificationType.CLASS);
                                        assertThat(notification.title()).isEqualTo("Class updated");
                                        assertThat(notification.body())
                                                        .isEqualTo("Updated Java Backend Cohort was updated.");
                                        assertThat(notification.referenceType()).isEqualTo("CLASS");
                                        assertThat(notification.referenceId()).isEqualTo(CLASS_ID);
                                        assertThat(notification.actionUrl()).isEqualTo("/classes/" + CLASS_ID);
                                        assertThat(notification.eventKey())
                                                        .isEqualTo("class:" + CLASS_ID + ":updated");
                                        assertThat(notification.payload())
                                                        .containsEntry("classId", CLASS_ID)
                                                        .containsEntry("courseId", UPDATED_COURSE_ID)
                                                        .containsEntry("className", "Updated Java Backend Cohort");
                                });
                verify(classOfferingRepository, times(2))
                                .hasCommercialHistory(CLASS_ID);
                verify(classSessionScheduleService, never())
                                .deleteFutureSessions(CLASS_ID);
        }

        @Test
        void UTCID02_update_rejectsRequestWithoutAnyProvidedField() {
                UpdateClassRequest request = new UpdateClassRequest();

                assertBusinessException(
                                request,
                                ErrorCode.INVALID_REQUEST,
                                "At least one class field must be provided");

                verify(classOfferingRepository, never())
                                .findByIdForUpdate(any());
        }

        @Test
        void UTCID03_update_rejectsClassThatDoesNotExist() {
                UpdateClassRequest request = classNameRequest("Updated name");
                when(classOfferingRepository.findByIdForUpdate(CLASS_ID))
                                .thenReturn(Optional.empty());

                assertBusinessException(
                                request,
                                ErrorCode.RESOURCE_NOT_FOUND,
                                "Class was not found");

                assertNoPersistenceOrPostSaveSideEffects();
        }

        @Test
        void UTCID04_update_rejectsManualLifecycleStatus() {
                ClassOffering existing = upcomingClass();
                UpdateClassRequest request = new UpdateClassRequest();
                request.setStatus("cancelled");
                stubLockedClass(existing);

                assertBusinessException(
                                request,
                                ErrorCode.INVALID_REQUEST,
                                "Class status is updated automatically from start date and end date. "
                                                + "Use the cancel endpoint to cancel a class.");

                assertNoPersistenceOrPostSaveSideEffects();
        }

        @Test
        void UTCID05_update_rejectsCompletedClassAsReadOnly() {
                ClassOffering existing = upcomingClass();
                existing.setStartDate(ClassLifecycle.today().minusMonths(2));
                existing.setEndDate(ClassLifecycle.today().minusDays(1));
                stubLockedClass(existing);

                assertBusinessException(
                                classNameRequest("Updated name"),
                                ErrorCode.CONFLICT,
                                "Completed or cancelled classes are read-only");

                assertThat(existing.getStatus()).isEqualTo(ClassStatus.COMPLETED);
                assertNoPersistenceOrPostSaveSideEffects();
        }

        @Test
        void UTCID06_update_rejectsCancelledClassAsReadOnly() {
                ClassOffering existing = upcomingClass();
                existing.setStatus(ClassStatus.CANCELLED);
                stubLockedClass(existing);

                assertBusinessException(
                                classNameRequest("Updated name"),
                                ErrorCode.CONFLICT,
                                "Completed or cancelled classes are read-only");

                assertNoPersistenceOrPostSaveSideEffects();
        }

        @Test
        void UTCID07_update_rejectsCourseChangeWhileClassIsOngoing() {
                ClassOffering existing = ongoingClass();
                UpdateClassRequest request = new UpdateClassRequest();
                request.setCourseId(UPDATED_COURSE_ID);
                stubLockedClass(existing);

                assertBusinessException(
                                request,
                                ErrorCode.CONFLICT,
                                "Course cannot be changed while the class is ongoing");

                verify(classOfferingRepository, never())
                                .hasCommercialHistory(any());
                assertNoPersistenceOrPostSaveSideEffects();
        }

        @Test
        void UTCID08_update_rejectsStartDateChangeWhileClassIsOngoing() {
                ClassOffering existing = ongoingClass();
                UpdateClassRequest request = new UpdateClassRequest();
                request.setStartDate(existing.getStartDate().minusDays(1));
                stubLockedClass(existing);

                assertBusinessException(
                                request,
                                ErrorCode.CONFLICT,
                                "Start date cannot be changed while the class is ongoing");

                assertNoPersistenceOrPostSaveSideEffects();
        }

        @Test
        void UTCID09_update_allowsPriceChangeWhileClassIsOngoing() {
                ClassOffering existing = ongoingClass();
                UserAccount actor = actor();
                UpdateClassRequest request = new UpdateClassRequest();
                request.setPrice(new BigDecimal("750000"));
                stubLockedClass(existing);
                stubSuccessfulTail(existing, actor, "Java Backend", "Original Trainer", 4L);

                ClassResponse response = service.update(CLASS_ID, request);

                assertThat(existing.getPrice()).isEqualByComparingTo("750000");
                assertThat(response.status()).isEqualTo("ongoing");
                assertThat(response.price()).isEqualByComparingTo("750000");
                verify(classOfferingRepository)
                                .hasCommercialHistory(CLASS_ID);
        }

        @Test
        void UTCID10_update_allowsOngoingClassWhenProtectedValuesAreUnchanged() {
                ClassOffering existing = ongoingClass();
                UserAccount actor = actor();
                UpdateClassRequest request = new UpdateClassRequest();
                request.setCourseId(existing.getCourseId());
                request.setStartDate(existing.getStartDate());
                request.setPrice(new BigDecimal("500000.00"));
                request.setClassName("  Ongoing Cohort Renamed  ");
                stubLockedClass(existing);
                stubSuccessfulTail(existing, actor, "Java Backend", "Original Trainer", 4L);

                ClassResponse response = service.update(CLASS_ID, request);

                assertThat(existing.getClassName()).isEqualTo("Ongoing Cohort Renamed");
                assertThat(response.status()).isEqualTo("ongoing");
                verify(courseRepository, never())
                                .findByIdAndDeletedAtIsNull(any());
                verify(classOfferingRepository, never())
                                .hasCommercialHistory(any());
                verify(classSessionScheduleService, never())
                                .validateScheduleDefinition(any());
                verify(classSessionScheduleService, never())
                                .synchronizeFutureSessions(any());
                verify(classSessionScheduleService, never())
                                .deleteFutureSessions(any());
        }

        @Test
        void UTCID11_update_rejectsExplicitNullCourse() {
                ClassOffering existing = upcomingClass();
                UpdateClassRequest request = new UpdateClassRequest();
                request.setCourseId(null);
                stubLockedClass(existing);

                assertBusinessException(request, ErrorCode.INVALID_REQUEST, "Course is required");

                assertNoPersistenceOrPostSaveSideEffects();
        }

        @Test
        void UTCID12_update_rejectsCourseChangeAfterCommercialHistory() {
                ClassOffering existing = upcomingClass();
                UpdateClassRequest request = new UpdateClassRequest();
                request.setCourseId(UPDATED_COURSE_ID);
                stubLockedClass(existing);
                when(classOfferingRepository.hasCommercialHistory(CLASS_ID))
                                .thenReturn(true);

                assertBusinessException(
                                request,
                                ErrorCode.CONFLICT,
                                "Course cannot be changed after the class has enrollment or commercial history");

                verify(courseRepository, never())
                                .findByIdAndDeletedAtIsNull(any());
                assertNoPersistenceOrPostSaveSideEffects();
        }

        @Test
        void UTCID13_update_rejectsChangedCourseThatDoesNotExist() {
                ClassOffering existing = upcomingClass();
                UpdateClassRequest request = new UpdateClassRequest();
                request.setCourseId(UPDATED_COURSE_ID);
                stubLockedClass(existing);
                when(courseRepository.findByIdAndDeletedAtIsNull(UPDATED_COURSE_ID))
                                .thenReturn(Optional.empty());

                assertBusinessException(request, ErrorCode.RESOURCE_NOT_FOUND, "Course was not found");

                assertNoPersistenceOrPostSaveSideEffects();
        }

        @Test
        void UTCID14_update_rejectsChangedCourseThatIsNotPublished() {
                ClassOffering existing = upcomingClass();
                Course draftCourse = publishedCourse(UPDATED_COURSE_ID);
                draftCourse.setStatus(CourseStatus.DRAFT);
                UpdateClassRequest request = new UpdateClassRequest();
                request.setCourseId(UPDATED_COURSE_ID);
                stubLockedClass(existing);
                when(courseRepository.findByIdAndDeletedAtIsNull(UPDATED_COURSE_ID))
                                .thenReturn(Optional.of(draftCourse));

                assertBusinessException(
                                request,
                                ErrorCode.BUSINESS_RULE_VIOLATION,
                                "Only a published course can be assigned to a class");

                assertNoPersistenceOrPostSaveSideEffects();
        }

        @Test
        void UTCID15_update_rejectsExplicitNullClassName() {
                ClassOffering existing = upcomingClass();
                stubLockedClass(existing);

                assertBusinessException(
                                classNameRequest(null),
                                ErrorCode.INVALID_REQUEST,
                                "Class name is required");

                assertNoPersistenceOrPostSaveSideEffects();
        }

        @Test
        void UTCID16_update_rejectsClassNameContainingOnlyWhitespace() {
                ClassOffering existing = upcomingClass();
                stubLockedClass(existing);

                assertBusinessException(
                                classNameRequest("   "),
                                ErrorCode.INVALID_REQUEST,
                                "Class name is required");

                assertNoPersistenceOrPostSaveSideEffects();
        }

        @Test
        void UTCID17_update_rejectsExplicitNullTrainer() {
                ClassOffering existing = upcomingClass();
                UpdateClassRequest request = new UpdateClassRequest();
                request.setTrainerId(null);
                stubLockedClass(existing);

                assertBusinessException(
                                request,
                                ErrorCode.INVALID_TRAINER,
                                "Please select a trainer");

                assertNoPersistenceOrPostSaveSideEffects();
        }

        @Test
        void UTCID18_update_rejectsTrainerThatIsMissingInactiveOrNotTrainer() {
                ClassOffering existing = upcomingClass();
                UpdateClassRequest request = new UpdateClassRequest();
                request.setTrainerId(UPDATED_TRAINER_ID);
                stubLockedClass(existing);
                when(userRepository.findActiveUserByIdAndRole(
                                UPDATED_TRAINER_ID,
                                "TRAINER",
                                "active"))
                                .thenReturn(Optional.empty());

                assertBusinessException(
                                request,
                                ErrorCode.INVALID_TRAINER,
                                "Trainer must exist, be active, and have the TRAINER role");

                assertNoPersistenceOrPostSaveSideEffects();
        }

        @Test
        void UTCID19_update_rejectsExplicitNullMeetingUrl() {
                assertMeetingUrlRejected(null, "Google Meet URL is required");
        }

        @Test
        void UTCID20_update_rejectsMeetingUrlLongerThan255Characters() {
                assertMeetingUrlRejected(
                                "https://meet.google.com/" + "a".repeat(232),
                                "Google Meet URL must not exceed 255 characters");
        }

        @Test
        void UTCID21_update_rejectsMeetingUrlWithInvalidFormat() {
                assertMeetingUrlRejected(
                                "https://example.com/abc-defg-hij",
                                "Meeting URL must use the format https://meet.google.com/abc-defg-hij");
        }

        @Test
        void UTCID22_update_rejectsExplicitNullSchedule() {
                assertScheduleRejected(null, "Class schedule is required");
        }

        @Test
        void UTCID23_update_rejectsScheduleContainingOnlyWhitespace() {
                assertScheduleRejected("   ", "Class schedule is required");
        }

        @Test
        void UTCID24_update_doesNotPersistWhenChangedScheduleFailsValidation() {
                ClassOffering existing = upcomingClass();
                UpdateClassRequest request = new UpdateClassRequest();
                request.setScheduleDescription(UPDATED_SCHEDULE);
                BusinessException validationError = new BusinessException(
                                ErrorCode.INVALID_REQUEST,
                                "Class schedule contains an unsupported slot");
                stubLockedClass(existing);
                doThrow(validationError)
                                .when(classSessionScheduleService)
                                .validateScheduleDefinition(existing);

                assertThatThrownBy(() -> service.update(CLASS_ID, request))
                                .isSameAs(validationError);

                assertNoPersistenceOrPostSaveSideEffects();
        }

        @Test
        void UTCID25_update_rejectsExplicitNullStartDate() {
                ClassOffering existing = upcomingClass();
                UpdateClassRequest request = new UpdateClassRequest();
                request.setStartDate(null);
                stubLockedClass(existing);

                assertBusinessException(request, ErrorCode.INVALID_REQUEST, "Start date is required");

                assertNoPersistenceOrPostSaveSideEffects();
        }

        @Test
        void UTCID26_update_rejectsExplicitNullEndDate() {
                ClassOffering existing = upcomingClass();
                UpdateClassRequest request = new UpdateClassRequest();
                request.setEndDate(null);
                stubLockedClass(existing);

                assertBusinessException(request, ErrorCode.INVALID_REQUEST, "End date is required");

                assertNoPersistenceOrPostSaveSideEffects();
        }

        @Test
        void UTCID27_update_rejectsEndDateBeforeStartDate() {
                ClassOffering existing = upcomingClass();
                UpdateClassRequest request = new UpdateClassRequest();
                request.setEndDate(existing.getStartDate().minusDays(1));
                stubLockedClass(existing);

                assertBusinessException(
                                request,
                                ErrorCode.INVALID_REQUEST,
                                "End date must not be before start date");

                assertNoPersistenceOrPostSaveSideEffects();
        }

        @Test
        void UTCID28_update_rejectsExplicitNullCapacity() {
                ClassOffering existing = upcomingClass();
                UpdateClassRequest request = new UpdateClassRequest();
                request.setMaxStudents(null);
                stubLockedClass(existing);

                assertBusinessException(request, ErrorCode.INVALID_REQUEST, "Capacity is required");

                verify(classEnrollmentRepository, never())
                                .countByClassIdAndStatus(any(), any());
                assertNoPersistenceOrPostSaveSideEffects();
        }

        @Test
        void UTCID29_update_rejectsCapacityBelowActiveEnrollmentCount() {
                ClassOffering existing = upcomingClass();
                UpdateClassRequest request = new UpdateClassRequest();
                request.setMaxStudents(2);
                stubLockedClass(existing);
                when(classEnrollmentRepository.countByClassIdAndStatus(CLASS_ID, "active"))
                                .thenReturn(3L);

                assertBusinessException(
                                request,
                                ErrorCode.CLASS_CAPACITY_INVALID,
                                "Capacity cannot be lower than the active enrollment count");

                assertNoPersistenceOrPostSaveSideEffects();
        }

        @Test
        void UTCID30_update_rejectsExplicitNullPrice() {
                ClassOffering existing = upcomingClass();
                UpdateClassRequest request = new UpdateClassRequest();
                request.setPrice(null);
                stubLockedClass(existing);

                assertBusinessException(request, ErrorCode.INVALID_REQUEST, "Class price is required");

                verify(classOfferingRepository, never())
                                .hasCommercialHistory(any());
                assertNoPersistenceOrPostSaveSideEffects();
        }

        @Test
        void UTCID31_update_rejectsPriceChangeAfterCommercialHistory() {
                ClassOffering existing = upcomingClass();
                UpdateClassRequest request = new UpdateClassRequest();
                request.setPrice(new BigDecimal("750000"));
                stubLockedClass(existing);
                when(classOfferingRepository.hasCommercialHistory(CLASS_ID))
                                .thenReturn(true);

                assertBusinessException(
                                request,
                                ErrorCode.CONFLICT,
                                "Class price cannot be changed after enrollment or payment history exists");

                assertNoPersistenceOrPostSaveSideEffects();
        }

        @Test
        void UTCID32_update_transitionsToCompletedAndDeletesFutureSessions() {
                ClassOffering existing = upcomingClass();
                UserAccount actor = actor();
                LocalDate completedEnd = ClassLifecycle.today().minusDays(1);
                UpdateClassRequest request = new UpdateClassRequest();
                request.setStartDate(completedEnd.minusMonths(1));
                request.setEndDate(completedEnd);
                stubLockedClass(existing);
                stubSuccessfulTail(existing, actor, "Java Backend", "Original Trainer", 0L);

                ClassResponse response = service.update(CLASS_ID, request);

                assertThat(existing.getStatus()).isEqualTo(ClassStatus.COMPLETED);
                assertThat(response.status()).isEqualTo("completed");
                verify(classSessionScheduleService, never())
                                .validateScheduleDefinition(any());
                verify(classSessionScheduleService)
                                .deleteFutureSessions(CLASS_ID);
                verify(classSessionScheduleService, never())
                                .synchronizeFutureSessions(any());
        }

        @Test
        void UTCID33_update_resynchronizesSessionsWhenScheduleChanges() {
                ClassOffering existing = upcomingClass();
                UpdateClassRequest request = new UpdateClassRequest();
                request.setScheduleDescription(UPDATED_SCHEDULE);

                ClassResponse response = executeSuccessfulUpdate(existing, request, 0L);

                assertThat(response.scheduleDescription()).isEqualTo(UPDATED_SCHEDULE);
                verifyScheduleWasValidatedAndSynchronized(existing);
        }

        @Test
        void UTCID34_update_resynchronizesSessionsWhenOnlyStartDateChanges() {
                ClassOffering existing = upcomingClass();
                UpdateClassRequest request = new UpdateClassRequest();
                request.setStartDate(existing.getStartDate().plusDays(1));

                executeSuccessfulUpdate(existing, request, 0L);

                verifyScheduleWasValidatedAndSynchronized(existing);
        }

        @Test
        void UTCID35_update_resynchronizesSessionsWhenOnlyEndDateChanges() {
                ClassOffering existing = upcomingClass();
                UpdateClassRequest request = new UpdateClassRequest();
                request.setEndDate(existing.getEndDate().plusDays(1));

                executeSuccessfulUpdate(existing, request, 0L);

                verifyScheduleWasValidatedAndSynchronized(existing);
        }

        @Test
        void UTCID36_update_resynchronizesSessionsWhenOnlyTrainerChanges() {
                ClassOffering existing = upcomingClass();
                UserAccount updatedTrainer = trainer(UPDATED_TRAINER_ID, "Updated Trainer");
                UpdateClassRequest request = new UpdateClassRequest();
                request.setTrainerId(UPDATED_TRAINER_ID);
                stubLockedClass(existing);
                when(userRepository.findActiveUserByIdAndRole(
                                UPDATED_TRAINER_ID,
                                "TRAINER",
                                "active"))
                                .thenReturn(Optional.of(updatedTrainer));
                stubSuccessfulTail(existing, actor(), "Java Backend", "Updated Trainer", 0L);

                ClassResponse response = service.update(CLASS_ID, request);

                assertThat(response.trainerId()).isEqualTo(UPDATED_TRAINER_ID);
                verifyScheduleWasValidatedAndSynchronized(existing);
        }

        @Test
        void UTCID37_update_propagatesRepositorySaveFailureBeforePostSaveSideEffects() {
                ClassOffering existing = upcomingClass();
                RuntimeException saveError = new RuntimeException("database unavailable");
                stubLockedClass(existing);
                when(classOfferingRepository.saveAndFlush(existing))
                                .thenThrow(saveError);

                assertThatThrownBy(() -> service.update(
                                CLASS_ID,
                                classNameRequest("Updated name")))
                                .isSameAs(saveError);

                verify(currentUserService, never())
                                .requireAuthenticatedUser();
                verify(classSessionScheduleService, never())
                                .synchronizeFutureSessions(any());
                verify(classSessionScheduleService, never())
                                .deleteFutureSessions(any());
                verify(auditLogService, never())
                                .record(any(), any(), any(), any());
                verify(notificationService, never())
                                .emit(any());
        }

        @Test
        void UTCID38_update_propagatesSessionSynchronizationFailureBeforeAudit() {
                ClassOffering existing = upcomingClass();
                UpdateClassRequest request = new UpdateClassRequest();
                request.setScheduleDescription(UPDATED_SCHEDULE);
                RuntimeException synchronizationError = new RuntimeException("session synchronization failed");
                stubLockedClass(existing);
                doThrow(synchronizationError)
                                .when(classSessionScheduleService)
                                .synchronizeFutureSessions(existing);

                assertThatThrownBy(() -> service.update(CLASS_ID, request))
                                .isSameAs(synchronizationError);

                verify(classOfferingRepository)
                                .saveAndFlush(existing);
                verify(auditLogService, never())
                                .record(any(), any(), any(), any());
                verify(notificationService, never())
                                .emit(any());
        }

        @Test
        void UTCID39_update_propagatesUnauthenticatedFailureDuringAudit() {
                ClassOffering existing = upcomingClass();
                BusinessException unauthenticated = new BusinessException(ErrorCode.UNAUTHENTICATED);
                stubLockedClass(existing);
                when(currentUserService.requireAuthenticatedUser())
                                .thenThrow(unauthenticated);

                assertThatThrownBy(() -> service.update(
                                CLASS_ID,
                                classNameRequest("Updated name")))
                                .isSameAs(unauthenticated);

                verify(classOfferingRepository)
                                .saveAndFlush(existing);
                verify(auditLogService, never())
                                .record(any(), any(), any(), any());
                verify(notificationService, never())
                                .emit(any());
        }

        @Test
        void UTCID40_update_propagatesAuditStorageFailureBeforeNotification() {
                ClassOffering existing = upcomingClass();
                UserAccount actor = actor();
                RuntimeException auditError = new RuntimeException("audit storage failed");
                stubLockedClass(existing);
                when(currentUserService.requireAuthenticatedUser())
                                .thenReturn(actor);
                doThrow(auditError)
                                .when(auditLogService)
                                .record(
                                                actor.getEmail(),
                                                "CLASS_UPDATED",
                                                "CLASS",
                                                CLASS_ID.toString());

                assertThatThrownBy(() -> service.update(
                                CLASS_ID,
                                classNameRequest("Updated name")))
                                .isSameAs(auditError);

                verify(notificationService, never())
                                .emit(any());
                verify(classEnrollmentRepository, never())
                                .findActiveOrCompletedStudentIdsByClassId(any());
        }

        @Test
        void UTCID41_update_succeedsWhenOptionalNotificationServiceIsUnavailable() {
                ClassOffering existing = upcomingClass();
                UserAccount actor = actor();
                service = new ClassAdminService(
                                classOfferingRepository,
                                classEnrollmentRepository,
                                courseRepository,
                                userRepository,
                                currentUserService,
                                auditLogService,
                                classSessionScheduleService);
                stubLockedClass(existing);
                when(currentUserService.requireAuthenticatedUser())
                                .thenReturn(actor);
                stubDetailFromEntity(existing, "Java Backend", "Original Trainer", 0L);

                ClassResponse response = service.update(
                                CLASS_ID,
                                classNameRequest("Updated without notifications"));

                assertThat(response.className()).isEqualTo("Updated without notifications");
                verify(classEnrollmentRepository, never())
                                .findActiveOrCompletedStudentIdsByClassId(any());
                verify(notificationService, never())
                                .emit(any());
        }

        @Test
        void UTCID42_update_propagatesNotificationFailureAfterAudit() {
                ClassOffering existing = upcomingClass();
                UserAccount actor = actor();
                RuntimeException notificationError = new RuntimeException("notification delivery failed");
                stubLockedClass(existing);
                when(currentUserService.requireAuthenticatedUser())
                                .thenReturn(actor);
                doThrow(notificationError)
                                .when(notificationService)
                                .emit(any(NotificationCreateCommand.class));

                assertThatThrownBy(() -> service.update(
                                CLASS_ID,
                                classNameRequest("Updated name")))
                                .isSameAs(notificationError);

                verify(auditLogService)
                                .record(
                                                actor.getEmail(),
                                                "CLASS_UPDATED",
                                                "CLASS",
                                                CLASS_ID.toString());
                verify(classEnrollmentRepository, never())
                                .findActiveOrCompletedStudentIdsByClassId(any());
                verify(classOfferingRepository, never())
                                .findAdminClassDetail(any());
        }

        @Test
        void UTCID43_update_reportsMissingDetailAfterAllWriteSideEffectsComplete() {
                ClassOffering existing = upcomingClass();
                UserAccount actor = actor();
                stubLockedClass(existing);
                when(currentUserService.requireAuthenticatedUser())
                                .thenReturn(actor);
                when(classEnrollmentRepository.findActiveOrCompletedStudentIdsByClassId(CLASS_ID))
                                .thenReturn(List.of());
                when(classOfferingRepository.findAdminClassDetail(CLASS_ID))
                                .thenReturn(Optional.empty());

                assertThatThrownBy(() -> service.update(
                                CLASS_ID,
                                classNameRequest("Updated name")))
                                .isInstanceOfSatisfying(
                                                BusinessException.class,
                                                error -> {
                                                        assertThat(error.errorCode())
                                                                        .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
                                                        assertThat(error.getMessage())
                                                                        .isEqualTo("Class was not found");
                                                });

                verify(classOfferingRepository)
                                .saveAndFlush(existing);
                verify(auditLogService)
                                .record(
                                                actor.getEmail(),
                                                "CLASS_UPDATED",
                                                "CLASS",
                                                CLASS_ID.toString());
                verify(notificationService)
                                .emit(any(NotificationCreateCommand.class));
        }

        private ClassResponse executeSuccessfulUpdate(
                        ClassOffering existing,
                        UpdateClassRequest request,
                        long activeCount) {
                stubLockedClass(existing);
                stubSuccessfulTail(
                                existing,
                                actor(),
                                "Java Backend",
                                "Original Trainer",
                                activeCount);
                return service.update(CLASS_ID, request);
        }

        private void stubSuccessfulTail(
                        ClassOffering existing,
                        UserAccount actor,
                        String courseTitle,
                        String trainerName,
                        long activeCount) {
                when(currentUserService.requireAuthenticatedUser())
                                .thenReturn(actor);
                when(classEnrollmentRepository.findActiveOrCompletedStudentIdsByClassId(CLASS_ID))
                                .thenReturn(List.of());
                stubDetailFromEntity(existing, courseTitle, trainerName, activeCount);
        }

        private void stubDetailFromEntity(
                        ClassOffering existing,
                        String courseTitle,
                        String trainerName,
                        Long activeCount) {
                ClassAdminProjection projection = org.mockito.Mockito.mock(ClassAdminProjection.class);
                when(projection.getId()).thenReturn(CLASS_ID);
                when(projection.getCourseId()).thenAnswer(ignored -> existing.getCourseId());
                when(projection.getCourseTitle()).thenReturn(courseTitle);
                when(projection.getClassName()).thenAnswer(ignored -> existing.getClassName());
                when(projection.getTrainerId()).thenAnswer(ignored -> existing.getTrainerId());
                when(projection.getTrainerName()).thenReturn(trainerName);
                when(projection.getMeetingUrl()).thenAnswer(ignored -> existing.getMeetingUrl());
                when(projection.getScheduleDescription())
                                .thenAnswer(ignored -> existing.getScheduleDescription());
                when(projection.getPrice()).thenAnswer(ignored -> existing.getPrice());
                when(projection.getStartDate()).thenAnswer(ignored -> existing.getStartDate());
                when(projection.getEndDate()).thenAnswer(ignored -> existing.getEndDate());
                when(projection.getMaxStudents()).thenAnswer(ignored -> existing.getMaxStudents());
                when(projection.getActiveEnrollmentCount()).thenReturn(activeCount);
                when(projection.getStatus())
                                .thenAnswer(ignored -> existing.getStatus().name().toLowerCase());
                when(classOfferingRepository.findAdminClassDetail(CLASS_ID))
                                .thenReturn(Optional.of(projection));
        }

        private void assertMeetingUrlRejected(String meetingUrl, String expectedMessage) {
                ClassOffering existing = upcomingClass();
                UpdateClassRequest request = new UpdateClassRequest();
                request.setMeetingUrl(meetingUrl);
                stubLockedClass(existing);

                assertBusinessException(request, ErrorCode.INVALID_REQUEST, expectedMessage);

                assertNoPersistenceOrPostSaveSideEffects();
        }

        private void assertScheduleRejected(String schedule, String expectedMessage) {
                ClassOffering existing = upcomingClass();
                UpdateClassRequest request = new UpdateClassRequest();
                request.setScheduleDescription(schedule);
                stubLockedClass(existing);

                assertBusinessException(request, ErrorCode.INVALID_REQUEST, expectedMessage);

                verify(classSessionScheduleService, never())
                                .validateScheduleDefinition(any());
                assertNoPersistenceOrPostSaveSideEffects();
        }

        private void assertBusinessException(
                        UpdateClassRequest request,
                        ErrorCode expectedCode,
                        String expectedMessage) {
                assertThatThrownBy(() -> service.update(CLASS_ID, request))
                                .isInstanceOfSatisfying(
                                                BusinessException.class,
                                                error -> {
                                                        assertThat(error.errorCode()).isEqualTo(expectedCode);
                                                        assertThat(error.getMessage()).isEqualTo(expectedMessage);
                                                });
        }

        private void assertNoPersistenceOrPostSaveSideEffects() {
                verify(classOfferingRepository, never())
                                .saveAndFlush(any());
                verify(classSessionScheduleService, never())
                                .synchronizeFutureSessions(any());
                verify(classSessionScheduleService, never())
                                .deleteFutureSessions(any());
                verify(auditLogService, never())
                                .record(any(), any(), any(), any());
                verify(notificationService, never())
                                .emit(any());
        }

        private void verifyScheduleWasValidatedAndSynchronized(ClassOffering existing) {
                verify(classSessionScheduleService)
                                .validateScheduleDefinition(existing);
                verify(classOfferingRepository)
                                .saveAndFlush(existing);
                verify(classSessionScheduleService)
                                .synchronizeFutureSessions(existing);
                verify(classSessionScheduleService, never())
                                .deleteFutureSessions(CLASS_ID);
        }

        private void stubLockedClass(ClassOffering existing) {
                when(classOfferingRepository.findByIdForUpdate(CLASS_ID))
                                .thenReturn(Optional.of(existing));
        }

        private UpdateClassRequest classNameRequest(String className) {
                UpdateClassRequest request = new UpdateClassRequest();
                request.setClassName(className);
                return request;
        }

        private ClassOffering upcomingClass() {
                LocalDate startDate = ClassLifecycle.today().plusDays(5);
                ClassOffering classOffering = new ClassOffering();
                classOffering.setId(CLASS_ID);
                classOffering.setCourseId(ORIGINAL_COURSE_ID);
                classOffering.setClassName("Original Java Backend Cohort");
                classOffering.setTrainerId(ORIGINAL_TRAINER_ID);
                classOffering.setMeetingUrl(ORIGINAL_MEETING_URL);
                classOffering.setScheduleDescription(ORIGINAL_SCHEDULE);
                classOffering.setStartDate(startDate);
                classOffering.setEndDate(startDate.plusMonths(1));
                classOffering.setMaxStudents(30);
                classOffering.setPrice(new BigDecimal("500000"));
                classOffering.setStatus(ClassStatus.UPCOMING);
                return classOffering;
        }

        private ClassOffering ongoingClass() {
                ClassOffering classOffering = upcomingClass();
                classOffering.setStartDate(ClassLifecycle.today().minusDays(2));
                classOffering.setEndDate(ClassLifecycle.today().plusDays(20));
                return classOffering;
        }

        private Course publishedCourse(UUID courseId) {
                Course course = new Course();
                course.setId(courseId);
                course.setTitle("Updated Course");
                course.setStatus(CourseStatus.PUBLISHED);
                return course;
        }

        private UserAccount trainer(UUID trainerId, String fullName) {
                UserAccount trainer = new UserAccount();
                trainer.setId(trainerId);
                trainer.setEmail("trainer@smartlearnly.dev");
                trainer.setFullName(fullName);
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