package com.smartlearnly.backend.classroom.admin.service;

import com.smartlearnly.backend.classroom.schedule.service.ClassSessionScheduleService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.classroom.dto.ClassResponse;
import com.smartlearnly.backend.classroom.admin.dto.CreateClassRequest;
import com.smartlearnly.backend.classroom.admin.dto.UpdateClassRequest;
import com.smartlearnly.backend.classroom.entity.ClassOffering;
import com.smartlearnly.backend.classroom.repository.ClassOfferingRepository;
import com.smartlearnly.backend.classroom.admin.dto.RestoreClassRequest;
import com.smartlearnly.backend.classroom.entity.ClassLifecycle;
import com.smartlearnly.backend.classroom.entity.ClassStatus;
import com.smartlearnly.backend.course.entity.CourseStatus;
import com.smartlearnly.backend.common.audit.AuditLogService;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.course.entity.Course;
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
class ClassAdminServiceTest {
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
        private ClassSessionScheduleService classSessionScheduleService;
        @Mock
        private AuditLogService auditLogService;

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
        void createShouldPersistConfiguredClassAndAuditActor() {
                UserAccount actor = user("admin@smartlearnly.dev");
                UserAccount trainer = user("trainer@smartlearnly.dev");
                Course course = course();
                LocalDate startDate = ClassLifecycle.today().plusDays(1);
                LocalDate endDate = startDate.plusMonths(1);
                CreateClassRequest request = new CreateClassRequest(
                                course.getId(),
                                "Spring Cohort",
                                trainer.getId(),
                                "https://meet.google.com/abc-defg-hij",
                                """
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
                                                """,
                                startDate,
                                endDate,
                                25,
                                new BigDecimal("500000"));
                when(currentUserService.requireAuthenticatedUser()).thenReturn(actor);
                when(courseRepository.findByIdAndDeletedAtIsNull(course.getId())).thenReturn(Optional.of(course));
                when(userRepository.findActiveUserByIdAndRole(
                                trainer.getId(),
                                "TRAINER",
                                "active")).thenReturn(Optional.of(trainer));
                when(classOfferingRepository.saveAndFlush(any(ClassOffering.class)))
                                .thenAnswer(invocation -> {
                                        ClassOffering saved = invocation.getArgument(0);
                                        saved.setId(UUID.randomUUID());
                                        return saved;
                                });

                ClassResponse response = service.create(request);

                assertThat(response.meetingUrl())
                                .isEqualTo("https://meet.google.com/abc-defg-hij");
                assertThat(response.className()).isEqualTo("Spring Cohort");
                assertThat(response.maxStudents()).isEqualTo(25);
                assertThat(response.availableSeats()).isEqualTo(25);
                assertThat(response.status()).isEqualTo("upcoming");
                verify(auditLogService).record(
                                actor.getEmail(),
                                "CLASS_CREATED",
                                "CLASS",
                                response.id().toString());
        }

        @Test
        void createShouldRejectMissingTrainerBeforeSavingClass() {
                UserAccount actor = user("admin@smartlearnly.dev");
                Course course = course();

                LocalDate startDate = ClassLifecycle.today().plusDays(1);
                LocalDate endDate = startDate.plusMonths(1);
                CreateClassRequest request = new CreateClassRequest(
                                course.getId(),
                                "Spring Cohort",
                                null,
                                "https://meet.google.com/abc-defg-hij",
                                """
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
                                                """,
                                LocalDate.of(2026, 7, 25),
                                LocalDate.of(2026, 8, 25),
                                30,
                                new BigDecimal("4000000"));

                when(currentUserService.requireAuthenticatedUser())
                                .thenReturn(actor);

                when(courseRepository.findByIdAndDeletedAtIsNull(course.getId()))
                                .thenReturn(Optional.of(course));

                assertThatThrownBy(() -> service.create(request))
                                .isInstanceOfSatisfying(
                                                BusinessException.class,
                                                exception -> {
                                                        assertThat(exception.errorCode())
                                                                        .isEqualTo(ErrorCode.INVALID_TRAINER);

                                                        assertThat(exception.getMessage())
                                                                        .isEqualTo("Please select a trainer");
                                                });

                verify(classOfferingRepository, never())
                                .saveAndFlush(any(ClassOffering.class));

                verify(classSessionScheduleService, never())
                                .synchronizeFutureSessions(any(ClassOffering.class));

                verify(auditLogService, never())
                                .record(any(), any(), any(), any());
        }

        @Test
        void updateShouldRejectCapacityBelowActiveEnrollmentCount() {
                ClassOffering classOffering = classOffering();
                UpdateClassRequest request = new UpdateClassRequest();
                request.setMaxStudents(2);
                when(classOfferingRepository.findByIdForUpdate(classOffering.getId()))
                                .thenReturn(Optional.of(classOffering));
                when(classEnrollmentRepository.countByClassIdAndStatus(
                                classOffering.getId(),
                                "active")).thenReturn(3L);

                assertThatThrownBy(() -> service.update(classOffering.getId(), request))
                                .isInstanceOfSatisfying(BusinessException.class,
                                                exception -> assertThat(exception.errorCode())
                                                                .isEqualTo(ErrorCode.CLASS_CAPACITY_INVALID));

                verify(classOfferingRepository, never()).save(any());
        }

        @Test
        void updateShouldRejectExplicitNullCapacity() {
                ClassOffering classOffering = classOffering();
                UpdateClassRequest request = new UpdateClassRequest();
                request.setMaxStudents(null);
                when(classOfferingRepository.findByIdForUpdate(classOffering.getId()))
                                .thenReturn(Optional.of(classOffering));

                assertThatThrownBy(() -> service.update(classOffering.getId(), request))
                                .isInstanceOfSatisfying(BusinessException.class,
                                                exception -> assertThat(exception.errorCode())
                                                                .isEqualTo(ErrorCode.INVALID_REQUEST));
        }

        @Test
        void updateShouldPreventCourseChangeAfterCommercialHistory() {
                ClassOffering classOffering = classOffering();
                UpdateClassRequest request = new UpdateClassRequest();
                request.setCourseId(UUID.randomUUID());
                when(classOfferingRepository.findByIdForUpdate(classOffering.getId()))
                                .thenReturn(Optional.of(classOffering));
                when(classOfferingRepository.hasCommercialHistory(classOffering.getId())).thenReturn(true);

                assertThatThrownBy(() -> service.update(classOffering.getId(), request))
                                .isInstanceOfSatisfying(BusinessException.class,
                                                exception -> assertThat(exception.errorCode())
                                                                .isEqualTo(ErrorCode.CONFLICT));

                verify(courseRepository, never()).findByIdAndDeletedAtIsNull(any());
        }

        @Test
        void cancelShouldCancelClassAndDeleteFutureSessions() {
                ClassOffering classOffering = classOffering();
                UserAccount actor = user("admin@smartlearnly.dev");

                when(classOfferingRepository.findByIdForUpdate(
                                classOffering.getId()))
                                .thenReturn(Optional.of(classOffering));

                when(currentUserService.requireAuthenticatedUser())
                                .thenReturn(actor);

                when(courseRepository.findByIdAndDeletedAtIsNull(
                                classOffering.getCourseId()))
                                .thenReturn(Optional.of(course()));

                when(userRepository.findByIdAndDeletedAtIsNull(
                                classOffering.getTrainerId()))
                                .thenReturn(Optional.empty());

                when(classEnrollmentRepository.countByClassIdAndStatus(
                                classOffering.getId(),
                                "active"))
                                .thenReturn(0L);

                ClassResponse response = service.cancel(classOffering.getId());

                assertThat(response.status()).isEqualTo("cancelled");

                verify(classOfferingRepository)
                                .saveAndFlush(classOffering);

                verify(classSessionScheduleService)
                                .deleteFutureSessions(classOffering.getId());

                verify(auditLogService).record(
                                actor.getEmail(),
                                "CLASS_CANCELLED",
                                "CLASS",
                                classOffering.getId().toString());
        }

        @Test
        void restoreShouldRestoreCancelledClassAndRebuildSessions() {
                ClassOffering classOffering = classOffering();
                classOffering.setStatus(ClassStatus.CANCELLED);

                LocalDate newStartDate = ClassLifecycle.today().plusDays(2);

                RestoreClassRequest request = new RestoreClassRequest(
                                newStartDate,
                                newStartDate.plusMonths(1));

                Course course = course();
                course.setId(classOffering.getCourseId());

                UserAccount trainer = user(
                                "trainer@smartlearnly.dev");
                trainer.setId(classOffering.getTrainerId());

                UserAccount actor = user(
                                "admin@smartlearnly.dev");

                when(classOfferingRepository.findByIdForUpdate(
                                classOffering.getId()))
                                .thenReturn(Optional.of(classOffering));

                when(classEnrollmentRepository.countByClassIdAndStatus(
                                classOffering.getId(),
                                "active"))
                                .thenReturn(0L);

                when(courseRepository.findByIdAndDeletedAtIsNull(
                                classOffering.getCourseId()))
                                .thenReturn(Optional.of(course));

                when(userRepository.findActiveUserByIdAndRole(
                                classOffering.getTrainerId(),
                                "TRAINER",
                                "active"))
                                .thenReturn(Optional.of(trainer));

                when(currentUserService.requireAuthenticatedUser())
                                .thenReturn(actor);

                ClassResponse response = service.restore(
                                classOffering.getId(),
                                request);

                assertThat(response.status()).isEqualTo("upcoming");
                assertThat(response.startDate()).isEqualTo(newStartDate);
                assertThat(response.endDate())
                                .isEqualTo(newStartDate.plusMonths(1));

                verify(classOfferingRepository)
                                .saveAndFlush(classOffering);

                verify(classSessionScheduleService)
                                .validateScheduleDefinition(classOffering);

                verify(classSessionScheduleService)
                                .synchronizeFutureSessions(classOffering);

                verify(auditLogService).record(
                                actor.getEmail(),
                                "CLASS_RESTORED",
                                "CLASS",
                                classOffering.getId().toString());
        }

        private Course course() {
                Course course = new Course();
                course.setId(UUID.randomUUID());
                course.setTitle("Course title");
                course.setStatus(CourseStatus.PUBLISHED);
                return course;
        }

        private ClassOffering classOffering() {
                LocalDate startDate = ClassLifecycle.today().plusDays(1);

                ClassOffering classOffering = new ClassOffering();
                classOffering.setId(UUID.randomUUID());
                classOffering.setCourseId(UUID.randomUUID());
                classOffering.setClassName("Existing class");
                classOffering.setTrainerId(UUID.randomUUID());
                classOffering.setMeetingUrl(
                                "https://meet.google.com/abc-defg-hij");
                classOffering.setScheduleDescription(
                                """
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
                                                """);
                classOffering.setStartDate(startDate);
                classOffering.setEndDate(startDate.plusMonths(1));
                classOffering.setMaxStudents(30);
                classOffering.setPrice(new BigDecimal("500000"));
                classOffering.setStatus(ClassStatus.UPCOMING);

                return classOffering;
        }

        private UserAccount user(String email) {
                UserAccount user = new UserAccount();
                user.setId(UUID.randomUUID());
                user.setEmail(email);
                user.setFullName(email);
                return user;
        }
}
