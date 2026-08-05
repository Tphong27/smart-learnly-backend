package com.smartlearnly.backend.classroom.schedule.service;

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
import com.smartlearnly.backend.classroom.schedule.validation.ScheduleDescriptionParser;
import com.smartlearnly.backend.classroom.schedule.validation.ScheduleValidator;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
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
class ClassSessionScheduleTest {

        private static final UUID CLASS_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
        private static final UUID COURSE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
        private static final UUID TRAINER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
        private static final UUID OTHER_CLASS_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
        private static final UUID OLD_TRAINER_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
        private static final UUID MATCHING_SESSION_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");
        private static final UUID STALE_SESSION_ID = UUID.fromString("77777777-7777-7777-7777-777777777777");
        private static final UUID HISTORICAL_SESSION_ID = UUID.fromString("88888888-8888-8888-8888-888888888888");
        private static final UUID CONFLICTING_SESSION_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");
        private static final UUID ADJACENT_SESSION_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        private static final UUID OTHER_DAY_SESSION_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

        private static final LocalDate FUTURE_MONDAY = LocalDate.of(2099, 1, 5);
        private static final LocalDate FUTURE_TUESDAY = LocalDate.of(2099, 1, 6);
        private static final LocalDate PAST_MONDAY = LocalDate.of(2000, 1, 3);

        private static final String MONDAY_SLOT_2_SCHEDULE = """
                        [
                          {
                            "dayOfWeek": "MONDAY",
                            "slots": [
                              {
                                "startTime": "09:45",
                                "endTime": "11:45"
                              }
                            ]
                          }
                        ]
                        """.strip();

        private static final String MONDAY_UNSUPPORTED_TIME_SCHEDULE = """
                        [
                          {
                            "dayOfWeek": "MONDAY",
                            "slots": [
                              {
                                "startTime": "09:00",
                                "endTime": "10:30"
                              }
                            ]
                          }
                        ]
                        """.strip();

        private static final String MONDAY_DUPLICATE_SLOT_2_SCHEDULE = """
                        [
                          {
                            "dayOfWeek": "MONDAY",
                            "slots": [
                              {
                                "startTime": "09:45",
                                "endTime": "11:45"
                              },
                              {
                                "startTime": "09:45",
                                "endTime": "11:45"
                              }
                            ]
                          }
                        ]
                        """.strip();

        @Mock
        private ClassSessionRepository classSessionRepository;

        private ClassSessionScheduleService service;

        @BeforeEach
        void setUp() {
                ObjectMapper objectMapper = new ObjectMapper();
                service = new ClassSessionScheduleService(
                                new ScheduleDescriptionParser(objectMapper),
                                new ScheduleValidator(),
                                new SessionSyncHandler(classSessionRepository));
        }

        // synchronizeFutureSessions(): UTCID restarts from UTCID01.

        @Test
        void UTCID01_synchronizeFutureSessions_rejectsNullScheduleDescription() {
                ClassOffering classOffering = offering(
                                null,
                                FUTURE_MONDAY,
                                FUTURE_MONDAY,
                                TRAINER_ID);

                assertInvalidSchedule(
                                () -> service.synchronizeFutureSessions(classOffering),
                                "Class schedule is required");

                verify(classSessionRepository, never()).saveAll(any());
        }

        @Test
        void UTCID02_synchronizeFutureSessions_rejectsMonday0900To1030BecauseItIsNotAStandardSlot() {
                ClassOffering classOffering = offering(
                                MONDAY_UNSUPPORTED_TIME_SCHEDULE,
                                FUTURE_MONDAY,
                                FUTURE_MONDAY,
                                TRAINER_ID);

                assertThatThrownBy(() -> service.synchronizeFutureSessions(classOffering))
                                .isInstanceOfSatisfying(BusinessException.class, error -> {
                                        assertThat(error.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST);
                                        assertThat(error.getMessage()).contains("Class schedule may only use");
                                });

                verify(classSessionRepository, never()).saveAll(any());
        }

        @Test
        void UTCID03_synchronizeFutureSessions_rejectsDuplicateMondaySlot2From0945To1145() {
                ClassOffering classOffering = offering(
                                MONDAY_DUPLICATE_SLOT_2_SCHEDULE,
                                FUTURE_MONDAY,
                                FUTURE_MONDAY,
                                TRAINER_ID);

                assertThatThrownBy(() -> service.synchronizeFutureSessions(classOffering))
                                .isInstanceOfSatisfying(BusinessException.class, error -> {
                                        assertThat(error.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST);
                                        assertThat(error.getMessage())
                                                        .isEqualTo("Slot 2 is selected more than once on MONDAY");
                                });

                verify(classSessionRepository, never()).saveAll(any());
        }

        @Test
        void UTCID04_synchronizeFutureSessions_rejectsTrainerConflictFrom1030To1130On20990105() {
                ClassOffering classOffering = offering(
                                MONDAY_SLOT_2_SCHEDULE,
                                FUTURE_MONDAY,
                                FUTURE_MONDAY,
                                TRAINER_ID);
                ClassSession conflictingSession = session(
                                CONFLICTING_SESSION_ID,
                                OTHER_CLASS_ID,
                                FUTURE_MONDAY,
                                LocalTime.of(10, 30),
                                LocalTime.of(11, 30),
                                TRAINER_ID);

                when(classSessionRepository.findTrainerSessionsForConflictCheck(
                                TRAINER_ID,
                                CLASS_ID,
                                FUTURE_MONDAY,
                                FUTURE_MONDAY))
                                .thenReturn(List.of(conflictingSession));

                assertThatThrownBy(() -> service.synchronizeFutureSessions(classOffering))
                                .isInstanceOfSatisfying(BusinessException.class, error -> {
                                        assertThat(error.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST);
                                        assertThat(error.getMessage())
                                                        .isEqualTo(
                                                                        "Trainer already has another class on 2099-01-05 from 10:30 to 11:30");
                                });

                verify(classSessionRepository, never()).saveAll(any());
        }

        @Test
        void UTCID05_synchronizeFutureSessions_createsMondaySlot2SessionForClassAndTrainer() {
                ClassOffering classOffering = offering(
                                MONDAY_SLOT_2_SCHEDULE,
                                FUTURE_MONDAY,
                                FUTURE_MONDAY,
                                TRAINER_ID);

                when(classSessionRepository.findTrainerSessionsForConflictCheck(
                                TRAINER_ID,
                                CLASS_ID,
                                FUTURE_MONDAY,
                                FUTURE_MONDAY))
                                .thenReturn(List.of());
                when(classSessionRepository
                                .findByClassIdAndSessionDateGreaterThanEqualOrderBySessionDateAscStartTimeAsc(
                                                CLASS_ID,
                                                LocalDate.now()))
                                .thenReturn(List.of());

                service.synchronizeFutureSessions(classOffering);

                @SuppressWarnings("unchecked")
                ArgumentCaptor<List<ClassSession>> captor = ArgumentCaptor.forClass(List.class);
                verify(classSessionRepository).saveAll(captor.capture());
                assertThat(captor.getValue())
                                .singleElement()
                                .satisfies(saved -> {
                                        assertThat(saved.getClassId()).isEqualTo(CLASS_ID);
                                        assertThat(saved.getTrainerId()).isEqualTo(TRAINER_ID);
                                        assertThat(saved.getSessionDate()).isEqualTo(FUTURE_MONDAY);
                                        assertThat(saved.getStartTime()).isEqualTo(LocalTime.of(9, 45));
                                        assertThat(saved.getEndTime()).isEqualTo(LocalTime.of(11, 45));
                                });
                verify(classSessionRepository, never()).deleteAll(any());
        }

        @Test
        void UTCID06_synchronizeFutureSessions_updatesMatchingTrainerAndDeletesStaleSlot3() {
                ClassOffering classOffering = offering(
                                MONDAY_SLOT_2_SCHEDULE,
                                FUTURE_MONDAY,
                                FUTURE_MONDAY,
                                TRAINER_ID);
                ClassSession matchingSlot2 = session(
                                MATCHING_SESSION_ID,
                                CLASS_ID,
                                FUTURE_MONDAY,
                                LocalTime.of(9, 45),
                                LocalTime.of(11, 45),
                                OLD_TRAINER_ID);
                ClassSession staleSlot3 = session(
                                STALE_SESSION_ID,
                                CLASS_ID,
                                FUTURE_MONDAY,
                                LocalTime.of(13, 0),
                                LocalTime.of(15, 0),
                                OLD_TRAINER_ID);

                when(classSessionRepository.findTrainerSessionsForConflictCheck(
                                TRAINER_ID,
                                CLASS_ID,
                                FUTURE_MONDAY,
                                FUTURE_MONDAY))
                                .thenReturn(List.of());
                when(classSessionRepository
                                .findByClassIdAndSessionDateGreaterThanEqualOrderBySessionDateAscStartTimeAsc(
                                                CLASS_ID,
                                                LocalDate.now()))
                                .thenReturn(List.of(matchingSlot2, staleSlot3));

                service.synchronizeFutureSessions(classOffering);

                @SuppressWarnings("unchecked")
                ArgumentCaptor<List<ClassSession>> saveCaptor = ArgumentCaptor.forClass(List.class);
                @SuppressWarnings("unchecked")
                ArgumentCaptor<Collection<ClassSession>> deleteCaptor = ArgumentCaptor.forClass(Collection.class);
                verify(classSessionRepository).saveAll(saveCaptor.capture());
                verify(classSessionRepository).deleteAll(deleteCaptor.capture());

                assertThat(saveCaptor.getValue()).containsExactly(matchingSlot2);
                assertThat(matchingSlot2.getTrainerId()).isEqualTo(TRAINER_ID);
                assertThat(deleteCaptor.getValue()).containsExactly(staleSlot3);
        }

        @Test
        void UTCID07_synchronizeFutureSessions_rejectsPastOnlyMondayScheduleEnding20000103() {
                ClassOffering classOffering = offering(
                                MONDAY_SLOT_2_SCHEDULE,
                                PAST_MONDAY,
                                PAST_MONDAY,
                                TRAINER_ID);

                assertInvalidSchedule(
                                () -> service.synchronizeFutureSessions(classOffering),
                                "The schedule must create at least one future class session");

                verify(classSessionRepository, never()).findTrainerSessionsForConflictCheck(
                                any(), any(), any(), any());
                verify(classSessionRepository, never()).saveAll(any());
        }

        @Test
        void UTCID08_synchronizeFutureSessions_preservesHistoricalRowAndAcceptsAdjacentTrainerRows() {
                ClassOffering classOffering = offering(
                                MONDAY_SLOT_2_SCHEDULE,
                                FUTURE_MONDAY,
                                FUTURE_MONDAY,
                                TRAINER_ID);
                ClassSession adjacentSameDay = session(
                                ADJACENT_SESSION_ID,
                                OTHER_CLASS_ID,
                                FUTURE_MONDAY,
                                LocalTime.of(7, 30),
                                LocalTime.of(9, 45),
                                TRAINER_ID);
                ClassSession sameTimeDifferentDay = session(
                                OTHER_DAY_SESSION_ID,
                                OTHER_CLASS_ID,
                                FUTURE_TUESDAY,
                                LocalTime.of(9, 45),
                                LocalTime.of(11, 45),
                                TRAINER_ID);
                ClassSession historicalSlot2 = session(
                                HISTORICAL_SESSION_ID,
                                CLASS_ID,
                                PAST_MONDAY,
                                LocalTime.of(9, 45),
                                LocalTime.of(11, 45),
                                OLD_TRAINER_ID);

                when(classSessionRepository.findTrainerSessionsForConflictCheck(
                                TRAINER_ID,
                                CLASS_ID,
                                FUTURE_MONDAY,
                                FUTURE_MONDAY))
                                .thenReturn(List.of(adjacentSameDay, sameTimeDifferentDay));
                when(classSessionRepository
                                .findByClassIdAndSessionDateGreaterThanEqualOrderBySessionDateAscStartTimeAsc(
                                                CLASS_ID,
                                                LocalDate.now()))
                                .thenReturn(List.of(historicalSlot2));

                service.synchronizeFutureSessions(classOffering);

                verify(classSessionRepository, never()).deleteAll(any());
                verify(classSessionRepository).saveAll(any());
                assertThat(historicalSlot2.getTrainerId()).isEqualTo(OLD_TRAINER_ID);
        }

        // validateScheduleDefinition(): UTCID restarts from UTCID01.

        @Test
        void UTCID01_validateScheduleDefinition_rejectsNullTrainerId() {
                ClassOffering classOffering = offering(
                                MONDAY_SLOT_2_SCHEDULE,
                                FUTURE_MONDAY,
                                FUTURE_MONDAY,
                                null);

                assertInvalidSchedule(
                                () -> service.validateScheduleDefinition(classOffering),
                                "Please select a trainer");
        }

        @Test
        void UTCID02_validateScheduleDefinition_acceptsTrainerAndMondaySlot2On20990105() {
                ClassOffering classOffering = offering(
                                MONDAY_SLOT_2_SCHEDULE,
                                FUTURE_MONDAY,
                                FUTURE_MONDAY,
                                TRAINER_ID);

                service.validateScheduleDefinition(classOffering);

                verify(classSessionRepository, never()).saveAll(any());
        }

        @Test
        void UTCID03_validateScheduleDefinition_rejectsPastOnlyDateRange20000103To20000103() {
                ClassOffering classOffering = offering(
                                MONDAY_SLOT_2_SCHEDULE,
                                PAST_MONDAY,
                                PAST_MONDAY,
                                TRAINER_ID);

                assertInvalidSchedule(
                                () -> service.validateScheduleDefinition(classOffering),
                                "The schedule must create at least one future class session");
        }

        @Test
        void UTCID04_validateScheduleDefinition_rejectsNullStartDate() {
                ClassOffering classOffering = offering(
                                MONDAY_SLOT_2_SCHEDULE,
                                null,
                                FUTURE_MONDAY,
                                TRAINER_ID);

                assertInvalidSchedule(
                                () -> service.validateScheduleDefinition(classOffering),
                                "Start date is required");
        }

        @Test
        void UTCID05_validateScheduleDefinition_rejectsNullEndDate() {
                ClassOffering classOffering = offering(
                                MONDAY_SLOT_2_SCHEDULE,
                                FUTURE_MONDAY,
                                null,
                                TRAINER_ID);

                assertInvalidSchedule(
                                () -> service.validateScheduleDefinition(classOffering),
                                "End date is required");
        }

        @Test
        void UTCID06_validateScheduleDefinition_rejectsMalformedJsonOpeningBracketOnly() {
                assertInvalidDefinition("[{", "Schedule must be valid JSON");
        }

        @Test
        void UTCID07_validateScheduleDefinition_rejectsObjectRootInsteadOfArray() {
                assertInvalidDefinition("{}", "Schedule must be a JSON array");
        }

        @Test
        void UTCID08_validateScheduleDefinition_rejectsEmptyScheduleArray() {
                assertInvalidDefinition("[]", "Please select at least one class schedule");
        }

        @Test
        void UTCID09_validateScheduleDefinition_rejectsStringDayNodeMonday() {
                assertInvalidDefinition(
                                "[\"MONDAY\"]",
                                "Each schedule day must be a JSON object");
        }

        @Test
        void UTCID10_validateScheduleDefinition_rejectsDayOfWeekFunday() {
                assertInvalidDefinition(
                                "[{\"dayOfWeek\":\"FUNDAY\",\"slots\":[]}]",
                                "Invalid schedule day: FUNDAY");
        }

        @Test
        void UTCID11_validateScheduleDefinition_rejectsDuplicateMondayEntries() {
                String duplicateMondaySchedule = """
                                [
                                  {"dayOfWeek":"MONDAY","slots":[{"startTime":"07:30","endTime":"09:30"}]},
                                  {"dayOfWeek":"MONDAY","slots":[{"startTime":"09:45","endTime":"11:45"}]}
                                ]
                                """.strip();

                assertInvalidDefinition(
                                duplicateMondaySchedule,
                                "Schedule contains duplicate day: MONDAY");
        }

        @Test
        void UTCID12_validateScheduleDefinition_rejectsObjectSlotsInsteadOfArray() {
                assertInvalidDefinition(
                                "[{\"dayOfWeek\":\"MONDAY\",\"slots\":{}}]",
                                "Schedule slots must be an array");
        }

        @Test
        void UTCID13_validateScheduleDefinition_rejectsMondayWithEmptySlotsArray() {
                assertInvalidDefinition(
                                "[{\"dayOfWeek\":\"MONDAY\",\"slots\":[]}]",
                                "Each selected schedule day must contain at least one time slot");
        }

        @Test
        void UTCID14_validateScheduleDefinition_rejectsStringSlotValueSlot1() {
                assertInvalidDefinition(
                                "[{\"dayOfWeek\":\"MONDAY\",\"slots\":[\"Slot 1\"]}]",
                                "Each schedule slot must be a JSON object");
        }

        @Test
        void UTCID15_validateScheduleDefinition_rejectsStartTime730WithoutLeadingZero() {
                assertInvalidDefinition(
                                "[{\"dayOfWeek\":\"MONDAY\",\"slots\":[{\"startTime\":\"7:30\",\"endTime\":\"09:30\"}]}]",
                                "Schedule time must use HH:mm format");
        }

        // deleteFutureSessions(): UTCID restarts from UTCID01.

        @Test
        void UTCID01_deleteFutureSessions_deletes20990105ButPreserves20000103() {
                ClassSession historicalSession = session(
                                HISTORICAL_SESSION_ID,
                                CLASS_ID,
                                PAST_MONDAY,
                                LocalTime.of(9, 45),
                                LocalTime.of(11, 45),
                                OLD_TRAINER_ID);
                ClassSession futureSession = session(
                                MATCHING_SESSION_ID,
                                CLASS_ID,
                                FUTURE_MONDAY,
                                LocalTime.of(9, 45),
                                LocalTime.of(11, 45),
                                TRAINER_ID);

                when(classSessionRepository
                                .findByClassIdAndSessionDateGreaterThanEqualOrderBySessionDateAscStartTimeAsc(
                                                CLASS_ID,
                                                LocalDate.now()))
                                .thenReturn(List.of(historicalSession, futureSession));

                service.deleteFutureSessions(CLASS_ID);

                @SuppressWarnings("unchecked")
                ArgumentCaptor<List<ClassSession>> captor = ArgumentCaptor.forClass(List.class);
                verify(classSessionRepository).deleteAll(captor.capture());
                assertThat(captor.getValue()).containsExactly(futureSession);
        }

        @Test
        void UTCID02_deleteFutureSessions_skipsDeleteWhenRepositoryReturnsEmptyList() {
                when(classSessionRepository
                                .findByClassIdAndSessionDateGreaterThanEqualOrderBySessionDateAscStartTimeAsc(
                                                CLASS_ID,
                                                LocalDate.now()))
                                .thenReturn(List.of());

                service.deleteFutureSessions(CLASS_ID);

                verify(classSessionRepository, never()).deleteAll(any());
        }

        private void assertInvalidDefinition(String scheduleDescription, String expectedMessage) {
                ClassOffering classOffering = offering(
                                scheduleDescription,
                                FUTURE_MONDAY,
                                FUTURE_MONDAY,
                                TRAINER_ID);

                assertInvalidSchedule(
                                () -> service.validateScheduleDefinition(classOffering),
                                expectedMessage);
        }

        private void assertInvalidSchedule(Runnable invocation, String expectedMessage) {
                assertThatThrownBy(invocation::run)
                                .isInstanceOfSatisfying(BusinessException.class, error -> {
                                        assertThat(error.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST);
                                        assertThat(error.getMessage()).isEqualTo(expectedMessage);
                                });
        }

        private ClassOffering offering(
                        String scheduleDescription,
                        LocalDate startDate,
                        LocalDate endDate,
                        UUID trainerId) {
                ClassOffering classOffering = new ClassOffering();
                classOffering.setId(CLASS_ID);
                classOffering.setCourseId(COURSE_ID);
                classOffering.setTrainerId(trainerId);
                classOffering.setStartDate(startDate);
                classOffering.setEndDate(endDate);
                classOffering.setScheduleDescription(scheduleDescription);
                return classOffering;
        }

        private ClassSession session(
                        UUID sessionId,
                        UUID classId,
                        LocalDate sessionDate,
                        LocalTime startTime,
                        LocalTime endTime,
                        UUID trainerId) {
                ClassSession session = new ClassSession();
                session.setId(sessionId);
                session.setClassId(classId);
                session.setSessionDate(sessionDate);
                session.setStartTime(startTime);
                session.setEndTime(endTime);
                session.setTrainerId(trainerId);
                return session;
        }
}