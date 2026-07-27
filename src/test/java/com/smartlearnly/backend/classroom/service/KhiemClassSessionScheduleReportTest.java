package com.smartlearnly.backend.classroom.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlearnly.backend.classroom.entity.ClassOffering;
import com.smartlearnly.backend.classroom.entity.ClassSession;
import com.smartlearnly.backend.classroom.repository.ClassSessionRepository;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KhiemClassSessionScheduleReportTest {

    @Mock
    private ClassSessionRepository classSessionRepository;

    private ClassSessionScheduleService service;

    @BeforeEach
    void setUp() {
        service = new ClassSessionScheduleService(
                classSessionRepository,
                new ObjectMapper());
    }

    @Test
    void UTCID_KHIEM_BE_468_synchronizeFutureSessions_rejectsBlankSchedule() {
        ClassOffering classOffering = offeringForTomorrow(null);

        assertThatThrownBy(() -> service.synchronizeFutureSessions(classOffering))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        error -> {
                            assertThat(error.errorCode())
                                    .isEqualTo(ErrorCode.INVALID_REQUEST);
                            assertThat(error.getMessage())
                                    .isEqualTo("Class schedule is required");
                        });

        verify(classSessionRepository, never()).saveAll(any());
    }

    @Test
    void UTCID_KHIEM_BE_469_synchronizeFutureSessions_rejectsOverlappingSlots() {
        LocalDate sessionDate = LocalDate.now().plusDays(1);
        ClassOffering classOffering = offering(
                sessionDate,
                overlappingSchedule(sessionDate.getDayOfWeek()));

        assertThatThrownBy(() -> service.synchronizeFutureSessions(classOffering))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        error -> {
                            assertThat(error.errorCode())
                                    .isEqualTo(ErrorCode.INVALID_REQUEST);
                            assertThat(error.getMessage())
                                    .contains("Schedule slots overlap");
                        });

        verify(classSessionRepository, never()).saveAll(any());
    }

    @Test
    void UTCID_KHIEM_BE_470_synchronizeFutureSessions_rejectsTrainerConflict() {
        LocalDate sessionDate = LocalDate.now().plusDays(1);
        ClassOffering classOffering = offering(
                sessionDate,
                oneSlotSchedule(sessionDate.getDayOfWeek()));
        ClassSession conflicting = session(
                UUID.randomUUID(),
                sessionDate,
                LocalTime.of(9, 30),
                LocalTime.of(10, 30),
                classOffering.getTrainerId());

        when(classSessionRepository.findTrainerSessionsForConflictCheck(
                classOffering.getTrainerId(),
                classOffering.getId(),
                sessionDate,
                sessionDate))
                .thenReturn(List.of(conflicting));

        assertThatThrownBy(() -> service.synchronizeFutureSessions(classOffering))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        error -> {
                            assertThat(error.errorCode())
                                    .isEqualTo(ErrorCode.INVALID_REQUEST);
                            assertThat(error.getMessage())
                                    .contains("Trainer already has another class");
                        });

        verify(classSessionRepository, never()).saveAll(any());
    }

    @Test
    void UTCID_KHIEM_BE_471_synchronizeFutureSessions_createsDesiredSession() {
        LocalDate sessionDate = LocalDate.now().plusDays(1);
        ClassOffering classOffering = offering(
                sessionDate,
                oneSlotSchedule(sessionDate.getDayOfWeek()));
        when(classSessionRepository.findTrainerSessionsForConflictCheck(
                classOffering.getTrainerId(),
                classOffering.getId(),
                sessionDate,
                sessionDate))
                .thenReturn(List.of());
        when(classSessionRepository
                .findByClassIdAndSessionDateGreaterThanEqualOrderBySessionDateAscStartTimeAsc(
                        classOffering.getId(),
                        LocalDate.now()))
                .thenReturn(List.of());

        service.synchronizeFutureSessions(classOffering);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ClassSession>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(classSessionRepository).saveAll(captor.capture());
        assertThat(captor.getValue())
                .singleElement()
                .satisfies(saved -> {
                    assertThat(saved.getClassId()).isEqualTo(classOffering.getId());
                    assertThat(saved.getTrainerId())
                            .isEqualTo(classOffering.getTrainerId());
                    assertThat(saved.getSessionDate()).isEqualTo(sessionDate);
                    assertThat(saved.getStartTime()).isEqualTo(LocalTime.of(9, 0));
                    assertThat(saved.getEndTime()).isEqualTo(LocalTime.of(10, 0));
                });
    }

    @Test
    void UTCID_KHIEM_BE_472_synchronizeFutureSessions_updatesMatchingAndDeletesStale() {
        LocalDate sessionDate = LocalDate.now().plusDays(1);
        ClassOffering classOffering = offering(
                sessionDate,
                oneSlotSchedule(sessionDate.getDayOfWeek()));
        UUID oldTrainerId = UUID.randomUUID();
        ClassSession matching = session(
                classOffering.getId(),
                sessionDate,
                LocalTime.of(9, 0),
                LocalTime.of(10, 0),
                oldTrainerId);
        ClassSession stale = session(
                classOffering.getId(),
                sessionDate,
                LocalTime.of(11, 0),
                LocalTime.of(12, 0),
                oldTrainerId);

        when(classSessionRepository.findTrainerSessionsForConflictCheck(
                classOffering.getTrainerId(),
                classOffering.getId(),
                sessionDate,
                sessionDate))
                .thenReturn(List.of());
        when(classSessionRepository
                .findByClassIdAndSessionDateGreaterThanEqualOrderBySessionDateAscStartTimeAsc(
                        classOffering.getId(),
                        LocalDate.now()))
                .thenReturn(List.of(matching, stale));

        service.synchronizeFutureSessions(classOffering);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ClassSession>> saveCaptor =
                ArgumentCaptor.forClass(List.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<ClassSession>> deleteCaptor =
                ArgumentCaptor.forClass(Collection.class);
        verify(classSessionRepository).saveAll(saveCaptor.capture());
        verify(classSessionRepository).deleteAll(deleteCaptor.capture());

        assertThat(saveCaptor.getValue()).containsExactly(matching);
        assertThat(matching.getTrainerId()).isEqualTo(classOffering.getTrainerId());
        assertThat(deleteCaptor.getValue()).containsExactly(stale);
    }

    private ClassOffering offeringForTomorrow(String schedule) {
        return offering(LocalDate.now().plusDays(1), schedule);
    }

    private ClassOffering offering(LocalDate sessionDate, String schedule) {
        ClassOffering classOffering = new ClassOffering();
        classOffering.setId(UUID.randomUUID());
        classOffering.setCourseId(UUID.randomUUID());
        classOffering.setTrainerId(UUID.randomUUID());
        classOffering.setStartDate(sessionDate);
        classOffering.setEndDate(sessionDate);
        classOffering.setScheduleDescription(schedule);
        return classOffering;
    }

    private ClassSession session(
            UUID classId,
            LocalDate sessionDate,
            LocalTime startTime,
            LocalTime endTime,
            UUID trainerId) {
        ClassSession session = new ClassSession();
        session.setId(UUID.randomUUID());
        session.setClassId(classId);
        session.setSessionDate(sessionDate);
        session.setStartTime(startTime);
        session.setEndTime(endTime);
        session.setTrainerId(trainerId);
        return session;
    }

    private String oneSlotSchedule(DayOfWeek day) {
        return """
                [
                  {
                    "dayOfWeek": "%s",
                    "slots": [
                      {
                        "startTime": "09:00",
                        "endTime": "10:00"
                      }
                    ]
                  }
                ]
                """.formatted(day.name());
    }

    private String overlappingSchedule(DayOfWeek day) {
        return """
                [
                  {
                    "dayOfWeek": "%s",
                    "slots": [
                      {
                        "startTime": "09:00",
                        "endTime": "10:30"
                      },
                      {
                        "startTime": "10:00",
                        "endTime": "11:00"
                      }
                    ]
                  }
                ]
                """.formatted(day.name());
    }
}
