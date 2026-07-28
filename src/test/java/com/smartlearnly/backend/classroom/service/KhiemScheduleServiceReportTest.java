package com.smartlearnly.backend.classroom.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.classroom.dto.ScheduleResponse;
import com.smartlearnly.backend.classroom.repository.ClassSessionRepository;
import com.smartlearnly.backend.classroom.repository.ScheduleProjection;
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
class KhiemScheduleServiceReportTest {

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
        void UTCID_KHIEM_BE_519_getMySchedule_resolvesWeekAndMapsSessions() {
                UserAccount trainee = user("TRAINEE");
                LocalDate requestedDate = LocalDate.of(2026, 7, 29);
                LocalDate weekStart = LocalDate.of(2026, 7, 27);
                LocalDate weekEnd = LocalDate.of(2026, 8, 2);
                ScheduleProjection projection = projection(LocalDate.of(2026, 7, 31));
                when(currentUserService.requireAuthenticatedUser()).thenReturn(trainee);
                when(classSessionRepository.findTraineeSchedule(
                                trainee.getId(), weekStart, weekEnd))
                                .thenReturn(List.of(projection));

                ScheduleResponse result = service.getMySchedule(requestedDate);

                assertThat(result.weekStart()).isEqualTo(weekStart);
                assertThat(result.weekEnd()).isEqualTo(weekEnd);
                assertThat(result.sessions()).singleElement().satisfies(session -> {
                        assertThat(session.sessionId()).isEqualTo(projection.getSessionId());
                        assertThat(session.courseTitle()).isEqualTo("Java Backend");
                        assertThat(session.meetingUrl())
                                        .isEqualTo("https://meet.google.com/abc-defg-hij");
                });
        }

        @Test
        void UTCID_KHIEM_BE_520_getMySchedule_usesCurrentWeekWhenDateOmitted() {
                UserAccount trainee = user("TRAINEE");
                LocalDate today = LocalDate.now();
                LocalDate expectedStart = today.minusDays(today.getDayOfWeek().getValue() - 1L);
                when(currentUserService.requireAuthenticatedUser()).thenReturn(trainee);
                when(classSessionRepository.findTraineeSchedule(
                                trainee.getId(), expectedStart, expectedStart.plusDays(6)))
                                .thenReturn(List.of());

                ScheduleResponse result = service.getMySchedule(null);

                assertThat(result.weekStart()).isEqualTo(expectedStart);
                assertThat(result.weekEnd()).isEqualTo(expectedStart.plusDays(6));
                assertThat(result.sessions()).isEmpty();
        }

        @Test
        void UTCID_KHIEM_BE_521_getStaffSchedule_trainerCanOnlySeeOwnSchedule() {
                UserAccount trainer = user("TRAINER");
                UUID ignoredRequestedTrainerId = UUID.randomUUID();
                LocalDate requestedDate = LocalDate.of(2026, 7, 28);
                LocalDate weekStart = LocalDate.of(2026, 7, 27);
                when(currentUserService.requireAuthenticatedUser()).thenReturn(trainer);
                ScheduleProjection selectedTrainerSession = projection(LocalDate.of(2026, 7, 29));

                when(classSessionRepository.findStaffSchedule(
                                trainer.getId(), weekStart, weekStart.plusDays(6)))
                                .thenReturn(List.of(selectedTrainerSession));

                ScheduleResponse result = service.getStaffSchedule(requestedDate, ignoredRequestedTrainerId);

                assertThat(result.weekStart()).isEqualTo(weekStart);
                verify(userRepository, never()).findActiveUserByIdAndRole(
                                ignoredRequestedTrainerId, "TRAINER", "active");
                verify(classSessionRepository).findStaffSchedule(
                                trainer.getId(), weekStart, weekStart.plusDays(6));
        }

        @Test
        void UTCID_KHIEM_BE_522_getStaffSchedule_tmoValidatesSelectedTrainer() {
                UserAccount tmo = user("TMO");
                UserAccount trainer = user("TRAINER");
                LocalDate requestedDate = LocalDate.of(2026, 7, 28);
                LocalDate weekStart = LocalDate.of(2026, 7, 27);

                when(currentUserService.requireAuthenticatedUser())
                                .thenReturn(tmo);

                when(userRepository.findActiveUserByIdAndRole(
                                trainer.getId(), "TRAINER", "active"))
                                .thenReturn(Optional.of(trainer));

                ScheduleProjection selectedTrainerSession = projection(LocalDate.of(2026, 7, 29));

                when(classSessionRepository.findStaffSchedule(
                                trainer.getId(), weekStart, weekStart.plusDays(6)))
                                .thenReturn(List.of(selectedTrainerSession));

                ScheduleResponse result = service.getStaffSchedule(requestedDate, trainer.getId());

                assertThat(result.sessions()).hasSize(1);

                verify(userRepository).findActiveUserByIdAndRole(
                                trainer.getId(), "TRAINER", "active");
        }

        @Test
        void UTCID_KHIEM_BE_523_getStaffSchedule_rejectsUnauthorizedRole() {
                UserAccount admin = user("ADMIN");
                when(currentUserService.requireAuthenticatedUser()).thenReturn(admin);

                assertThatThrownBy(() -> service.getStaffSchedule(
                                LocalDate.of(2026, 7, 28), null))
                                .isInstanceOfSatisfying(BusinessException.class,
                                                error -> assertThat(error.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        }

        private UserAccount user(String role) {
                UserAccount user = new UserAccount();
                user.setId(UUID.randomUUID());
                user.setRole(role);
                user.setStatus("active");
                user.setEmail(role.toLowerCase() + "@example.com");
                user.setFullName(role);
                return user;
        }

        private ScheduleProjection projection(LocalDate sessionDate) {
                ScheduleProjection projection = mock(ScheduleProjection.class);
                when(projection.getSessionId()).thenReturn(UUID.randomUUID());
                when(projection.getClassId()).thenReturn(UUID.randomUUID());
                when(projection.getCourseId()).thenReturn(UUID.randomUUID());
                when(projection.getCourseTitle()).thenReturn("Java Backend");
                when(projection.getClassName()).thenReturn("Java Class");
                when(projection.getSessionDate()).thenReturn(sessionDate);
                when(projection.getStartTime()).thenReturn(LocalTime.of(9, 0));
                when(projection.getEndTime()).thenReturn(LocalTime.of(11, 0));
                when(projection.getTrainerId()).thenReturn(UUID.randomUUID());
                when(projection.getTrainerName()).thenReturn("Trainer");
                when(projection.getMeetingUrl())
                                .thenReturn("https://meet.google.com/abc-defg-hij");
                return projection;
        }
}
