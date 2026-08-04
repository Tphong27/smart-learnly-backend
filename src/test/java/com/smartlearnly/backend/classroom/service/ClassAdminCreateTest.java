package com.smartlearnly.backend.classroom.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.classroom.dto.ClassResponse;
import com.smartlearnly.backend.classroom.dto.CreateClassRequest;
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
class ClassAdminCreateTest {

        private static final UUID ACTOR_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        private static final UUID COURSE_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        private static final UUID TRAINER_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
        private static final UUID INVALID_TRAINER_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
        private static final UUID SAVED_CLASS_ID = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");

        private static final String VALID_SCHEDULE = """
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

        private static final String VALID_MEETING_URL = "https://meet.google.com/abc-defg-hij";

        private static final String INVALID_MEETING_URL_MESSAGE = "Meeting URL must use the format https://meet.google.com/abc-defg-hij";

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
        void UTCID01_create_persistsNormalizedClassAndRunsSideEffectsInOrder() {
                UserAccount actor = actor();
                UserAccount trainer = trainer();
                Course course = publishedCourse();
                LocalDate startDate = validStartDate();
                LocalDate endDate = startDate.plusMonths(1);

                CreateClassRequest request = request(
                                course.getId(),
                                "  Java Backend Cohort  ",
                                trainer.getId(),
                                "  " + VALID_MEETING_URL + "  ",
                                "  " + VALID_SCHEDULE + "  ",
                                startDate,
                                endDate,
                                30,
                                BigDecimal.ZERO);

                stubAuthenticatedCreateDependencies(actor, course, trainer);
                stubSuccessfulSave();

                ClassResponse response = service.create(request);

                ArgumentCaptor<ClassOffering> savedCaptor = ArgumentCaptor.forClass(ClassOffering.class);
                ArgumentCaptor<NotificationCreateCommand> notificationCaptor = ArgumentCaptor
                                .forClass(NotificationCreateCommand.class);

                InOrder callOrder = inOrder(
                                classSessionScheduleService,
                                classOfferingRepository,
                                auditLogService,
                                notificationService);

                callOrder.verify(classSessionScheduleService)
                                .validateScheduleDefinition(any(ClassOffering.class));
                callOrder.verify(classOfferingRepository)
                                .saveAndFlush(savedCaptor.capture());

                ClassOffering saved = savedCaptor.getValue();

                callOrder.verify(classSessionScheduleService)
                                .synchronizeFutureSessions(saved);
                callOrder.verify(auditLogService).record(
                                actor.getEmail(),
                                "CLASS_CREATED",
                                "CLASS",
                                SAVED_CLASS_ID.toString());
                callOrder.verify(notificationService)
                                .emit(notificationCaptor.capture());

                assertThat(saved.getId()).isEqualTo(SAVED_CLASS_ID);
                assertThat(saved.getCourseId()).isEqualTo(COURSE_ID);
                assertThat(saved.getClassName()).isEqualTo("Java Backend Cohort");
                assertThat(saved.getTrainerId()).isEqualTo(TRAINER_ID);
                assertThat(saved.getMeetingUrl()).isEqualTo(VALID_MEETING_URL);
                assertThat(saved.getScheduleDescription()).isEqualTo(VALID_SCHEDULE);
                assertThat(saved.getPrice()).isEqualByComparingTo(BigDecimal.ZERO);
                assertThat(saved.getStartDate()).isEqualTo(startDate);
                assertThat(saved.getEndDate()).isEqualTo(endDate);
                assertThat(saved.getMaxStudents()).isEqualTo(30);
                assertThat(saved.getStatus()).isEqualTo(ClassStatus.UPCOMING);
                assertThat(saved.getCreatedBy()).isEqualTo(ACTOR_ID);

                assertThat(response.id()).isEqualTo(SAVED_CLASS_ID);
                assertThat(response.courseId()).isEqualTo(COURSE_ID);
                assertThat(response.courseTitle()).isEqualTo("Java Backend");
                assertThat(response.className()).isEqualTo("Java Backend Cohort");
                assertThat(response.trainerId()).isEqualTo(TRAINER_ID);
                assertThat(response.trainerName()).isEqualTo("Java Trainer");
                assertThat(response.meetingUrl()).isEqualTo(VALID_MEETING_URL);
                assertThat(response.price()).isEqualByComparingTo(BigDecimal.ZERO);
                assertThat(response.startDate()).isEqualTo(startDate);
                assertThat(response.endDate()).isEqualTo(endDate);
                assertThat(response.maxStudents()).isEqualTo(30);
                assertThat(response.activeEnrollmentCount()).isZero();
                assertThat(response.availableSeats()).isEqualTo(30);
                assertThat(response.status()).isEqualTo("upcoming");

                NotificationCreateCommand notification = notificationCaptor.getValue();
                assertThat(notification.userId()).isEqualTo(TRAINER_ID);
                assertThat(notification.type()).isEqualTo(NotificationType.CLASS);
                assertThat(notification.title()).isEqualTo("New class assigned");
                assertThat(notification.referenceType()).isEqualTo("CLASS");
                assertThat(notification.referenceId()).isEqualTo(SAVED_CLASS_ID);
                assertThat(notification.eventKey())
                                .isEqualTo("class:" + SAVED_CLASS_ID + ":created");
        }

        @Test
        void UTCID02_create_rejectsUnauthenticatedActorBeforeReadingRequestDependencies() {
                BusinessException unauthenticated = new BusinessException(ErrorCode.UNAUTHENTICATED);

                when(currentUserService.requireAuthenticatedUser())
                                .thenThrow(unauthenticated);

                assertThatThrownBy(() -> service.create(validRequest()))
                                .isSameAs(unauthenticated);

                verify(courseRepository, never())
                                .findByIdAndDeletedAtIsNull(any());
                verify(userRepository, never())
                                .findActiveUserByIdAndRole(any(), any(), any());
                verify(classSessionScheduleService, never())
                                .validateScheduleDefinition(any(ClassOffering.class));
                assertNoWriteSideEffects();
        }

        @Test
        void UTCID03_create_rejectsCourseThatDoesNotExist() {
                when(currentUserService.requireAuthenticatedUser())
                                .thenReturn(actor());
                when(courseRepository.findByIdAndDeletedAtIsNull(COURSE_ID))
                                .thenReturn(Optional.empty());

                assertBusinessException(
                                validRequest(),
                                ErrorCode.RESOURCE_NOT_FOUND,
                                "Course was not found");

                verify(userRepository, never())
                                .findActiveUserByIdAndRole(any(), any(), any());
                verify(classSessionScheduleService, never())
                                .validateScheduleDefinition(any(ClassOffering.class));
                assertNoWriteSideEffects();
        }

        @Test
        void UTCID04_create_rejectsUnpublishedCourseBeforeTrainerLookup() {
                Course course = publishedCourse();
                course.setStatus(CourseStatus.DRAFT);

                when(currentUserService.requireAuthenticatedUser())
                                .thenReturn(actor());
                when(courseRepository.findByIdAndDeletedAtIsNull(COURSE_ID))
                                .thenReturn(Optional.of(course));

                assertBusinessException(
                                validRequest(),
                                ErrorCode.BUSINESS_RULE_VIOLATION,
                                "Only a published course can be assigned to a class");

                verify(userRepository, never())
                                .findActiveUserByIdAndRole(any(), any(), any());
                verify(classSessionScheduleService, never())
                                .validateScheduleDefinition(any(ClassOffering.class));
                assertNoWriteSideEffects();
        }

        @Test
        void UTCID05_create_rejectsNullTrainerId() {
                UserAccount actor = actor();
                Course course = publishedCourse();

                when(currentUserService.requireAuthenticatedUser())
                                .thenReturn(actor);
                when(courseRepository.findByIdAndDeletedAtIsNull(COURSE_ID))
                                .thenReturn(Optional.of(course));

                CreateClassRequest request = withTrainerId(validRequest(), null);

                assertBusinessException(
                                request,
                                ErrorCode.INVALID_TRAINER,
                                "Please select a trainer");

                verify(userRepository, never())
                                .findActiveUserByIdAndRole(any(), any(), any());
                verify(classSessionScheduleService, never())
                                .validateScheduleDefinition(any(ClassOffering.class));
                assertNoWriteSideEffects();
        }

        @Test
        void UTCID06_create_rejectsTrainerThatIsMissingInactiveOrNotTrainer() {
                when(currentUserService.requireAuthenticatedUser())
                                .thenReturn(actor());
                when(courseRepository.findByIdAndDeletedAtIsNull(COURSE_ID))
                                .thenReturn(Optional.of(publishedCourse()));
                when(userRepository.findActiveUserByIdAndRole(
                                INVALID_TRAINER_ID,
                                "TRAINER",
                                "active"))
                                .thenReturn(Optional.empty());

                CreateClassRequest request = withTrainerId(
                                validRequest(),
                                INVALID_TRAINER_ID);

                assertBusinessException(
                                request,
                                ErrorCode.INVALID_TRAINER,
                                "Trainer must exist, be active, and have the TRAINER role");

                verify(classSessionScheduleService, never())
                                .validateScheduleDefinition(any(ClassOffering.class));
                assertNoWriteSideEffects();
        }

        @Test
        void UTCID07_create_rejectsNullStartDate() {
                stubAuthenticatedCreateDependencies(actor(), publishedCourse(), trainer());

                CreateClassRequest request = withDates(
                                validRequest(),
                                null,
                                validStartDate().plusMonths(1));

                assertBusinessException(
                                request,
                                ErrorCode.INVALID_REQUEST,
                                "Start date is required");

                verify(classSessionScheduleService, never())
                                .validateScheduleDefinition(any(ClassOffering.class));
                assertNoWriteSideEffects();
        }

        @Test
        void UTCID08_create_rejectsNullEndDate() {
                stubAuthenticatedCreateDependencies(actor(), publishedCourse(), trainer());

                CreateClassRequest request = withDates(
                                validRequest(),
                                validStartDate(),
                                null);

                assertBusinessException(
                                request,
                                ErrorCode.INVALID_REQUEST,
                                "End date is required");

                verify(classSessionScheduleService, never())
                                .validateScheduleDefinition(any(ClassOffering.class));
                assertNoWriteSideEffects();
        }

        @Test
        void UTCID09_create_rejectsEndDateBeforeStartDate() {
                stubAuthenticatedCreateDependencies(actor(), publishedCourse(), trainer());
                LocalDate startDate = validStartDate();

                CreateClassRequest request = withDates(
                                validRequest(),
                                startDate,
                                startDate.minusDays(1));

                assertBusinessException(
                                request,
                                ErrorCode.INVALID_REQUEST,
                                "End date must not be before start date");

                verify(classSessionScheduleService, never())
                                .validateScheduleDefinition(any(ClassOffering.class));
                assertNoWriteSideEffects();
        }

        @Test
        void UTCID10_create_acceptsEndDateEqualToStartDateBoundary() {
                UserAccount actor = actor();
                Course course = publishedCourse();
                UserAccount trainer = trainer();
                LocalDate sameDate = validStartDate();

                stubAuthenticatedCreateDependencies(actor, course, trainer);
                stubSuccessfulSave();

                ClassResponse response = service.create(withDates(
                                validRequest(),
                                sameDate,
                                sameDate));

                assertThat(response.startDate()).isEqualTo(sameDate);
                assertThat(response.endDate()).isEqualTo(sameDate);
                assertThat(response.status()).isEqualTo("upcoming");
                verifySuccessfulPostSaveSideEffects(actor);
        }

        @Test
        void UTCID11_create_resolvesOngoingStatusWhenClassStartsToday() {
                UserAccount actor = actor();
                Course course = publishedCourse();
                UserAccount trainer = trainer();
                LocalDate today = ClassLifecycle.today();

                stubAuthenticatedCreateDependencies(actor, course, trainer);
                stubSuccessfulSave();

                ClassResponse response = service.create(withDates(
                                validRequest(),
                                today,
                                today.plusDays(7)));

                assertThat(response.status()).isEqualTo("ongoing");
                verifySuccessfulPostSaveSideEffects(actor);
        }

        @Test
        void UTCID12_create_rejectsNullClassName() {
                stubAuthenticatedCreateDependencies(actor(), publishedCourse(), trainer());

                assertBusinessException(
                                withClassName(validRequest(), null),
                                ErrorCode.INVALID_REQUEST,
                                "Class name is required");

                verify(classSessionScheduleService, never())
                                .validateScheduleDefinition(any(ClassOffering.class));
                assertNoWriteSideEffects();
        }

        @Test
        void UTCID13_create_rejectsClassNameContainingOnlyWhitespace() {
                stubAuthenticatedCreateDependencies(actor(), publishedCourse(), trainer());

                assertBusinessException(
                                withClassName(validRequest(), "   "),
                                ErrorCode.INVALID_REQUEST,
                                "Class name is required");

                verify(classSessionScheduleService, never())
                                .validateScheduleDefinition(any(ClassOffering.class));
                assertNoWriteSideEffects();
        }

        @Test
        void UTCID14_create_rejectsNullMeetingUrl() {
                assertInvalidMeetingUrl(
                                null,
                                "Google Meet URL is required");
        }

        @Test
        void UTCID15_create_rejectsMeetingUrlContainingOnlyWhitespace() {
                assertInvalidMeetingUrl(
                                "   ",
                                "Google Meet URL is required");
        }

        @Test
        void UTCID16_create_rejectsMeetingUrlLongerThan255Characters() {
                assertInvalidMeetingUrl(
                                "https://meet.google.com/" + "a".repeat(232),
                                "Google Meet URL must not exceed 255 characters");
        }

        @Test
        void UTCID17_create_rejectsMalformedMeetingUri() {
                assertInvalidMeetingUrl(
                                "https://meet.google.com/%",
                                INVALID_MEETING_URL_MESSAGE);
        }

        @Test
        void UTCID18_create_rejectsNonHttpsMeetingUrl() {
                assertInvalidMeetingUrl(
                                "http://meet.google.com/abc-defg-hij",
                                INVALID_MEETING_URL_MESSAGE);
        }

        @Test
        void UTCID19_create_rejectsMeetingUrlFromWrongHost() {
                assertInvalidMeetingUrl(
                                "https://example.com/abc-defg-hij",
                                INVALID_MEETING_URL_MESSAGE);
        }

        @Test
        void UTCID20_create_rejectsMeetingUrlWithExplicitPort() {
                assertInvalidMeetingUrl(
                                "https://meet.google.com:443/abc-defg-hij",
                                INVALID_MEETING_URL_MESSAGE);
        }

        @Test
        void UTCID21_create_rejectsMeetingUrlWithUserInfo() {
                assertInvalidMeetingUrl(
                                "https://user@meet.google.com/abc-defg-hij",
                                INVALID_MEETING_URL_MESSAGE);
        }

        @Test
        void UTCID22_create_rejectsMeetingUrlWithFragment() {
                assertInvalidMeetingUrl(
                                "https://meet.google.com/abc-defg-hij#room",
                                INVALID_MEETING_URL_MESSAGE);
        }

        @Test
        void UTCID23_create_rejectsMeetingUrlWithInvalidMeetingCodePath() {
                assertInvalidMeetingUrl(
                                "https://meet.google.com/abcd-efgh-ijk",
                                INVALID_MEETING_URL_MESSAGE);
        }

        @Test
        void UTCID24_create_acceptsValidMeetingUrlWithTrailingSlash() {
                UserAccount actor = actor();
                Course course = publishedCourse();
                UserAccount trainer = trainer();
                String urlWithTrailingSlash = VALID_MEETING_URL + "/";

                stubAuthenticatedCreateDependencies(actor, course, trainer);
                stubSuccessfulSave();

                ClassResponse response = service.create(withMeetingUrl(
                                validRequest(),
                                urlWithTrailingSlash));

                assertThat(response.meetingUrl()).isEqualTo(urlWithTrailingSlash);
                verifySuccessfulPostSaveSideEffects(actor);
        }

        @Test
        void UTCID25_create_passesNullScheduleToValidatorAfterNormalizationAndDoesNotPersist() {
                assertScheduleRejectedAfterNormalization(
                                null,
                                null,
                                "Class schedule is required");
        }

        @Test
        void UTCID26_create_passesWhitespaceScheduleAsNullToValidatorAndDoesNotPersist() {
                assertScheduleRejectedAfterNormalization(
                                "   ",
                                null,
                                "Class schedule is required");
        }

        @Test
        void UTCID27_create_doesNotPersistWhenScheduleDefinitionIsInvalid() {
                stubAuthenticatedCreateDependencies(actor(), publishedCourse(), trainer());

                BusinessException scheduleError = new BusinessException(
                                ErrorCode.INVALID_REQUEST,
                                "Class schedule contains an unsupported slot");

                doThrow(scheduleError)
                                .when(classSessionScheduleService)
                                .validateScheduleDefinition(any(ClassOffering.class));

                assertThatThrownBy(() -> service.create(validRequest()))
                                .isSameAs(scheduleError);

                verify(classSessionScheduleService)
                                .validateScheduleDefinition(any(ClassOffering.class));
                assertNoWriteSideEffects();
        }

        @Test
        void UTCID28_create_stopsWhenRepositorySaveFails() {
                UserAccount actor = actor();
                stubAuthenticatedCreateDependencies(actor, publishedCourse(), trainer());
                RuntimeException databaseError = new RuntimeException("database unavailable");

                when(classOfferingRepository.saveAndFlush(any(ClassOffering.class)))
                                .thenThrow(databaseError);

                assertThatThrownBy(() -> service.create(validRequest()))
                                .isSameAs(databaseError);

                verify(classSessionScheduleService)
                                .validateScheduleDefinition(any(ClassOffering.class));
                verify(classSessionScheduleService, never())
                                .synchronizeFutureSessions(any(ClassOffering.class));
                verify(auditLogService, never())
                                .record(any(), any(), any(), any());
                verify(notificationService, never())
                                .emit(any(NotificationCreateCommand.class));
        }

        @Test
        void UTCID29_create_stopsWhenFutureSessionSynchronizationFails() {
                UserAccount actor = actor();
                stubAuthenticatedCreateDependencies(actor, publishedCourse(), trainer());
                stubSuccessfulSave();
                RuntimeException synchronizationError = new RuntimeException("session synchronization failed");

                doThrow(synchronizationError)
                                .when(classSessionScheduleService)
                                .synchronizeFutureSessions(any(ClassOffering.class));

                assertThatThrownBy(() -> service.create(validRequest()))
                                .isSameAs(synchronizationError);

                verify(classOfferingRepository)
                                .saveAndFlush(any(ClassOffering.class));
                verify(auditLogService, never())
                                .record(any(), any(), any(), any());
                verify(notificationService, never())
                                .emit(any(NotificationCreateCommand.class));
        }

        @Test
        void UTCID30_create_stopsWhenAuditRecordingFails() {
                UserAccount actor = actor();
                stubAuthenticatedCreateDependencies(actor, publishedCourse(), trainer());
                stubSuccessfulSave();
                RuntimeException auditError = new RuntimeException("audit storage failed");

                doThrow(auditError).when(auditLogService).record(
                                actor.getEmail(),
                                "CLASS_CREATED",
                                "CLASS",
                                SAVED_CLASS_ID.toString());

                assertThatThrownBy(() -> service.create(validRequest()))
                                .isSameAs(auditError);

                verify(classSessionScheduleService)
                                .synchronizeFutureSessions(any(ClassOffering.class));
                verify(notificationService, never())
                                .emit(any(NotificationCreateCommand.class));
        }

        @Test
        void UTCID31_create_propagatesNotificationFailureAfterAudit() {
                UserAccount actor = actor();
                stubAuthenticatedCreateDependencies(actor, publishedCourse(), trainer());
                stubSuccessfulSave();
                RuntimeException notificationError = new RuntimeException("notification delivery failed");

                doThrow(notificationError)
                                .when(notificationService)
                                .emit(any(NotificationCreateCommand.class));

                assertThatThrownBy(() -> service.create(validRequest()))
                                .isSameAs(notificationError);

                verify(auditLogService).record(
                                actor.getEmail(),
                                "CLASS_CREATED",
                                "CLASS",
                                SAVED_CLASS_ID.toString());
                verify(notificationService)
                                .emit(any(NotificationCreateCommand.class));
        }

        private void assertInvalidMeetingUrl(
                        String meetingUrl,
                        String expectedMessage) {
                stubAuthenticatedCreateDependencies(actor(), publishedCourse(), trainer());

                assertBusinessException(
                                withMeetingUrl(validRequest(), meetingUrl),
                                ErrorCode.INVALID_REQUEST,
                                expectedMessage);

                verify(classSessionScheduleService, never())
                                .validateScheduleDefinition(any(ClassOffering.class));
                assertNoWriteSideEffects();
        }

        private void assertScheduleRejectedAfterNormalization(
                        String inputSchedule,
                        String expectedNormalizedSchedule,
                        String message) {
                stubAuthenticatedCreateDependencies(actor(), publishedCourse(), trainer());
                BusinessException scheduleError = new BusinessException(ErrorCode.INVALID_REQUEST, message);
                ArgumentCaptor<ClassOffering> offeringCaptor = ArgumentCaptor.forClass(ClassOffering.class);

                doThrow(scheduleError)
                                .when(classSessionScheduleService)
                                .validateScheduleDefinition(offeringCaptor.capture());

                assertThatThrownBy(() -> service.create(withSchedule(
                                validRequest(),
                                inputSchedule)))
                                .isSameAs(scheduleError);

                assertThat(offeringCaptor.getValue().getScheduleDescription())
                                .isEqualTo(expectedNormalizedSchedule);
                assertNoWriteSideEffects();
        }

        private void assertBusinessException(
                        CreateClassRequest request,
                        ErrorCode expectedCode,
                        String expectedMessage) {
                assertThatThrownBy(() -> service.create(request))
                                .isInstanceOfSatisfying(
                                                BusinessException.class,
                                                error -> {
                                                        assertThat(error.errorCode())
                                                                        .isEqualTo(expectedCode);
                                                        assertThat(error.getMessage())
                                                                        .isEqualTo(expectedMessage);
                                                });
        }

        private void stubAuthenticatedCreateDependencies(
                        UserAccount actor,
                        Course course,
                        UserAccount trainer) {
                when(currentUserService.requireAuthenticatedUser())
                                .thenReturn(actor);
                when(courseRepository.findByIdAndDeletedAtIsNull(course.getId()))
                                .thenReturn(Optional.of(course));
                when(userRepository.findActiveUserByIdAndRole(
                                trainer.getId(),
                                "TRAINER",
                                "active"))
                                .thenReturn(Optional.of(trainer));
        }

        private void stubSuccessfulSave() {
                when(classOfferingRepository.saveAndFlush(any(ClassOffering.class)))
                                .thenAnswer(invocation -> {
                                        ClassOffering saved = invocation.getArgument(0);
                                        saved.setId(SAVED_CLASS_ID);
                                        return saved;
                                });
        }

        private void verifySuccessfulPostSaveSideEffects(UserAccount actor) {
                verify(classSessionScheduleService)
                                .validateScheduleDefinition(any(ClassOffering.class));
                verify(classOfferingRepository)
                                .saveAndFlush(any(ClassOffering.class));
                verify(classSessionScheduleService)
                                .synchronizeFutureSessions(any(ClassOffering.class));
                verify(auditLogService).record(
                                actor.getEmail(),
                                "CLASS_CREATED",
                                "CLASS",
                                SAVED_CLASS_ID.toString());
                verify(notificationService)
                                .emit(any(NotificationCreateCommand.class));
        }

        private void assertNoWriteSideEffects() {
                verify(classOfferingRepository, never())
                                .saveAndFlush(any(ClassOffering.class));
                verify(classSessionScheduleService, never())
                                .synchronizeFutureSessions(any(ClassOffering.class));
                verify(auditLogService, never())
                                .record(any(), any(), any(), any());
                verify(notificationService, never())
                                .emit(any(NotificationCreateCommand.class));
        }

        private CreateClassRequest validRequest() {
                LocalDate startDate = validStartDate();
                return request(
                                COURSE_ID,
                                "Java Backend Cohort",
                                TRAINER_ID,
                                VALID_MEETING_URL,
                                VALID_SCHEDULE,
                                startDate,
                                startDate.plusMonths(1),
                                30,
                                new BigDecimal("500000"));
        }

        private CreateClassRequest withTrainerId(
                        CreateClassRequest source,
                        UUID trainerId) {
                return request(
                                source.courseId(),
                                source.className(),
                                trainerId,
                                source.meetingUrl(),
                                source.scheduleDescription(),
                                source.startDate(),
                                source.endDate(),
                                source.maxStudents(),
                                source.price());
        }

        private CreateClassRequest withDates(
                        CreateClassRequest source,
                        LocalDate startDate,
                        LocalDate endDate) {
                return request(
                                source.courseId(),
                                source.className(),
                                source.trainerId(),
                                source.meetingUrl(),
                                source.scheduleDescription(),
                                startDate,
                                endDate,
                                source.maxStudents(),
                                source.price());
        }

        private CreateClassRequest withClassName(
                        CreateClassRequest source,
                        String className) {
                return request(
                                source.courseId(),
                                className,
                                source.trainerId(),
                                source.meetingUrl(),
                                source.scheduleDescription(),
                                source.startDate(),
                                source.endDate(),
                                source.maxStudents(),
                                source.price());
        }

        private CreateClassRequest withMeetingUrl(
                        CreateClassRequest source,
                        String meetingUrl) {
                return request(
                                source.courseId(),
                                source.className(),
                                source.trainerId(),
                                meetingUrl,
                                source.scheduleDescription(),
                                source.startDate(),
                                source.endDate(),
                                source.maxStudents(),
                                source.price());
        }

        private CreateClassRequest withSchedule(
                        CreateClassRequest source,
                        String scheduleDescription) {
                return request(
                                source.courseId(),
                                source.className(),
                                source.trainerId(),
                                source.meetingUrl(),
                                scheduleDescription,
                                source.startDate(),
                                source.endDate(),
                                source.maxStudents(),
                                source.price());
        }

        private CreateClassRequest request(
                        UUID courseId,
                        String className,
                        UUID trainerId,
                        String meetingUrl,
                        String scheduleDescription,
                        LocalDate startDate,
                        LocalDate endDate,
                        Integer maxStudents,
                        BigDecimal price) {
                return new CreateClassRequest(
                                courseId,
                                className,
                                trainerId,
                                meetingUrl,
                                scheduleDescription,
                                startDate,
                                endDate,
                                maxStudents,
                                price);
        }

        private LocalDate validStartDate() {
                return ClassLifecycle.today().plusDays(14);
        }

        private UserAccount actor() {
                UserAccount actor = new UserAccount();
                actor.setId(ACTOR_ID);
                actor.setEmail("khiem@smartlearnly.dev");
                actor.setFullName("Khiem");
                return actor;
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

        private Course publishedCourse() {
                Course course = new Course();
                course.setId(COURSE_ID);
                course.setTitle("Java Backend");
                course.setStatus(CourseStatus.PUBLISHED);
                return course;
        }
}