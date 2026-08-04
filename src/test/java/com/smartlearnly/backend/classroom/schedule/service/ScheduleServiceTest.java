package com.smartlearnly.backend.classroom.schedule.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.classroom.schedule.dto.ScheduleResponse;
import com.smartlearnly.backend.classroom.repository.ClassSessionRepository;
import com.smartlearnly.backend.classroom.schedule.repository.ScheduleProjection;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.user.entity.UserAccount;
import com.smartlearnly.backend.user.repository.UserRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScheduleServiceTest {

        private static final UUID TRAINEE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
        private static final UUID TRAINER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
        private static final UUID TMO_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
        private static final UUID ADMIN_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
        private static final UUID REQUESTED_TRAINER_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
        private static final UUID SESSION_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");
        private static final UUID CLASS_ID = UUID.fromString("77777777-7777-7777-7777-777777777777");
        private static final UUID COURSE_ID = UUID.fromString("88888888-8888-8888-8888-888888888888");

        private static final LocalDate REQUESTED_DATE = LocalDate.of(2026, 7, 29);
        private static final LocalDate WEEK_START = LocalDate.of(2026, 7, 27);
        private static final LocalDate WEEK_END = LocalDate.of(2026, 8, 2);
        private static final LocalDate SESSION_DATE = LocalDate.of(2026, 7, 31);

        @Mock
        private ClassSessionRepository classSessionRepository;

        @Mock
        private CurrentUserService currentUserService;

        @Mock
        private UserRepository userRepository;

        private ScheduleService service;

        @BeforeEach
        void setUp() {
                service = new ScheduleService(
                                classSessionRepository,
                                currentUserService,
                                userRepository);
        }

        @Test
        void UTCID01_getStaffSchedule_trainerIgnoresRequestedTrainerAndReadsOwnSchedule() {
                UserAccount trainer = user(
                                TRAINER_ID,
                                "TRAINER",
                                "trainer@example.com",
                                "Trainer Nguyen");
                ScheduleProjection projection = projection(LocalDate.of(2026, 7, 29));

                when(currentUserService.requireAuthenticatedUser()).thenReturn(trainer);
                when(classSessionRepository.findStaffSchedule(
                                TRAINER_ID,
                                WEEK_START,
                                WEEK_END))
                                .thenReturn(List.of(projection));

                ScheduleResponse result = service.getStaffSchedule(
                                LocalDate.of(2026, 7, 28),
                                REQUESTED_TRAINER_ID);

                assertThat(result.weekStart()).isEqualTo(WEEK_START);
                assertThat(result.weekEnd()).isEqualTo(WEEK_END);
                assertThat(result.sessions()).hasSize(1);
                verify(userRepository, never()).findActiveUserByIdAndRole(
                                REQUESTED_TRAINER_ID,
                                "TRAINER",
                                "active");
                verify(classSessionRepository).findStaffSchedule(
                                TRAINER_ID,
                                WEEK_START,
                                WEEK_END);
        }

        @Test
        void UTCID02_getStaffSchedule_tmoReadsSelectedActiveTrainerSchedule() {
                UserAccount tmo = user(
                                TMO_ID,
                                "TMO",
                                "tmo@example.com",
                                "TMO Nguyen");
                UserAccount selectedTrainer = user(
                                REQUESTED_TRAINER_ID,
                                "TRAINER",
                                "selected.trainer@example.com",
                                "Selected Trainer");
                ScheduleProjection projection = projection(LocalDate.of(2026, 7, 29));

                when(currentUserService.requireAuthenticatedUser()).thenReturn(tmo);
                when(userRepository.findActiveUserByIdAndRole(
                                REQUESTED_TRAINER_ID,
                                "TRAINER",
                                "active"))
                                .thenReturn(Optional.of(selectedTrainer));
                when(classSessionRepository.findStaffSchedule(
                                REQUESTED_TRAINER_ID,
                                WEEK_START,
                                WEEK_END))
                                .thenReturn(List.of(projection));

                ScheduleResponse result = service.getStaffSchedule(
                                LocalDate.of(2026, 7, 28),
                                REQUESTED_TRAINER_ID);

                assertThat(result.weekStart()).isEqualTo(WEEK_START);
                assertThat(result.weekEnd()).isEqualTo(WEEK_END);
                assertThat(result.sessions()).hasSize(1);
                verify(userRepository).findActiveUserByIdAndRole(
                                REQUESTED_TRAINER_ID,
                                "TRAINER",
                                "active");
        }

        @Test
        void UTCID03_getStaffSchedule_rejectsAdminRoleForDate20260728() {
                UserAccount admin = user(
                                ADMIN_ID,
                                "ADMIN",
                                "admin@example.com",
                                "Admin Nguyen");
                when(currentUserService.requireAuthenticatedUser()).thenReturn(admin);

                assertThatThrownBy(() -> service.getStaffSchedule(
                                LocalDate.of(2026, 7, 28),
                                null))
                                .isInstanceOfSatisfying(BusinessException.class, error -> {
                                        assertThat(error.errorCode()).isEqualTo(ErrorCode.FORBIDDEN);
                                        assertThat(error.getMessage())
                                                        .isEqualTo("Only trainers and TMO can view staff schedules");
                                });

                verify(classSessionRepository, never()).findStaffSchedule(any(), any(), any());
        }

        @Test
        void UTCID04_getStaffSchedule_tmoWithNullTrainerReadsAllCurrentWeekSessions() {
                UserAccount tmo = user(
                                TMO_ID,
                                "tmo",
                                "tmo@example.com",
                                "TMO Nguyen");
                LocalDate today = LocalDate.now();
                LocalDate currentWeekStart = today.minusDays(today.getDayOfWeek().getValue() - 1L);
                LocalDate currentWeekEnd = currentWeekStart.plusDays(6);
                ScheduleProjection projection = projection(currentWeekStart.plusDays(2));

                when(currentUserService.requireAuthenticatedUser()).thenReturn(tmo);
                when(classSessionRepository.findStaffSchedule(
                                null,
                                currentWeekStart,
                                currentWeekEnd))
                                .thenReturn(List.of(projection));

                ScheduleResponse result = service.getStaffSchedule(null, null);

                assertThat(result.weekStart()).isEqualTo(currentWeekStart);
                assertThat(result.weekEnd()).isEqualTo(currentWeekEnd);
                assertThat(result.sessions()).singleElement().satisfies(session -> {
                        assertThat(session.sessionId()).isEqualTo(SESSION_ID);
                        assertThat(session.classId()).isEqualTo(CLASS_ID);
                        assertThat(session.courseId()).isEqualTo(COURSE_ID);
                        assertThat(session.className()).isEqualTo("Java Class A");
                        assertThat(session.trainerId()).isEqualTo(TRAINER_ID);
                        assertThat(session.trainerName()).isEqualTo("Trainer Nguyen");
                });
                verify(userRepository, never()).findActiveUserByIdAndRole(
                                any(),
                                anyString(),
                                anyString());
        }

        @Test
        void UTCID05_getStaffSchedule_rejectsSelectedTrainerThatIsMissingInactiveOrWrongRole() {
                UserAccount tmo = user(
                                TMO_ID,
                                "TMO",
                                "tmo@example.com",
                                "TMO Nguyen");
                LocalDate requestedDate = LocalDate.of(2026, 8, 3);
                LocalDate weekStart = LocalDate.of(2026, 8, 3);
                LocalDate weekEnd = LocalDate.of(2026, 8, 9);

                when(currentUserService.requireAuthenticatedUser()).thenReturn(tmo);
                when(userRepository.findActiveUserByIdAndRole(
                                REQUESTED_TRAINER_ID,
                                "TRAINER",
                                "active"))
                                .thenReturn(Optional.empty());

                assertThatThrownBy(() -> service.getStaffSchedule(
                                requestedDate,
                                REQUESTED_TRAINER_ID))
                                .isInstanceOfSatisfying(BusinessException.class, error -> {
                                        assertThat(error.errorCode()).isEqualTo(ErrorCode.INVALID_TRAINER);
                                        assertThat(error.getMessage())
                                                        .isEqualTo(
                                                                        "Selected trainer must exist, be active, and have the TRAINER role");
                                });

                verify(classSessionRepository, never()).findStaffSchedule(
                                REQUESTED_TRAINER_ID,
                                weekStart,
                                weekEnd);
        }

        private UserAccount user(
                        UUID id,
                        String role,
                        String email,
                        String fullName) {
                UserAccount user = new UserAccount();
                user.setId(id);
                user.setRole(role);
                user.setStatus("active");
                user.setEmail(email);
                user.setFullName(fullName);
                return user;
        }

        private ScheduleProjection projection(LocalDate sessionDate) {
                ScheduleProjection projection = mock(ScheduleProjection.class);
                when(projection.getSessionId()).thenReturn(SESSION_ID);
                when(projection.getClassId()).thenReturn(CLASS_ID);
                when(projection.getCourseId()).thenReturn(COURSE_ID);
                when(projection.getCourseTitle()).thenReturn("Java Backend");
                when(projection.getClassName()).thenReturn("Java Class A");
                when(projection.getSessionDate()).thenReturn(sessionDate);
                when(projection.getStartTime()).thenReturn(LocalTime.of(9, 45));
                when(projection.getEndTime()).thenReturn(LocalTime.of(11, 45));
                when(projection.getTrainerId()).thenReturn(TRAINER_ID);
                when(projection.getTrainerName()).thenReturn("Trainer Nguyen");
                when(projection.getMeetingUrl())
                                .thenReturn("https://meet.google.com/abc-defg-hij");
                return projection;
        }
}