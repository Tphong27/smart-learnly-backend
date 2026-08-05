package com.smartlearnly.backend.classroom.schedule.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartlearnly.backend.classroom.entity.ClassOffering;
import com.smartlearnly.backend.classroom.schedule.validation.ScheduleDescriptionParser.TimeRange;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ScheduleValidatorTest {

    private ScheduleValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ScheduleValidator();
    }

    @Test
    void validateScheduleDefinition_validOffering_succeeds() {
        ClassOffering classOffering = createValidClassOffering();
        
        // Should not throw
        validator.validateScheduleDefinition(classOffering);
    }

    @Test
    void validateScheduleDefinition_noTrainer_throwsException() {
        ClassOffering classOffering = createValidClassOffering();
        classOffering.setTrainerId(null);

        assertThatThrownBy(() -> validator.validateScheduleDefinition(classOffering))
                .isInstanceOf(ScheduleValidationException.class)
                .hasMessageContaining("trainer");
    }

    @Test
    void validateScheduleDefinition_noStartDate_throwsException() {
        ClassOffering classOffering = createValidClassOffering();
        classOffering.setStartDate(null);

        assertThatThrownBy(() -> validator.validateScheduleDefinition(classOffering))
                .isInstanceOf(ScheduleValidationException.class)
                .hasMessageContaining("Start date");
    }

    @Test
    void validateScheduleDefinition_noEndDate_throwsException() {
        ClassOffering classOffering = createValidClassOffering();
        classOffering.setEndDate(null);

        assertThatThrownBy(() -> validator.validateScheduleDefinition(classOffering))
                .isInstanceOf(ScheduleValidationException.class)
                .hasMessageContaining("End date");
    }

    @Test
    void validateScheduleDefinition_invalidJson_throwsException() {
        ClassOffering classOffering = createValidClassOffering();
        classOffering.setScheduleDescription("not valid json");

        assertThatThrownBy(() -> validator.validateScheduleDefinition(classOffering))
                .isInstanceOf(ScheduleValidationException.class);
    }

    @Test
    void validateScheduleDefinition_noFutureSessions_throwsException() {
        ClassOffering classOffering = createValidClassOffering();
        classOffering.setStartDate(LocalDate.now().minusDays(30));
        classOffering.setEndDate(LocalDate.now().minusDays(1));
        classOffering.setScheduleDescription("""
            [{"dayOfWeek": "MONDAY", "slots": [{"startTime": "07:30", "endTime": "09:30"}]}]
            """);

        assertThatThrownBy(() -> validator.validateScheduleDefinition(classOffering))
                .isInstanceOf(ScheduleValidationException.class)
                .hasMessageContaining("at least one future class session");
    }

    @Test
    void buildDesiredSessions_validSchedule_generatesCorrectSessions() {
        LocalDate startDate = LocalDate.of(2026, 8, 3); // Monday
        LocalDate endDate = LocalDate.of(2026, 8, 7); // Friday
        UUID trainerId = UUID.randomUUID();
        LocalDateTime cutoff = LocalDateTime.of(2026, 8, 1, 0, 0);

        Map<DayOfWeek, List<TimeRange>> weeklySchedule = Map.of(
                DayOfWeek.MONDAY, List.of(new TimeRange(LocalTime.of(7, 30), LocalTime.of(9, 30))),
                DayOfWeek.WEDNESDAY, List.of(new TimeRange(LocalTime.of(19, 30), LocalTime.of(21, 30))));

        List<ScheduleValidator.DesiredSession> sessions =
                validator.buildDesiredSessions(startDate, endDate, weeklySchedule, trainerId, cutoff);

        // Should have 2 sessions: Monday and Wednesday
        assertThat(sessions).hasSize(2);
        assertThat(sessions.get(0).sessionDate()).isEqualTo(LocalDate.of(2026, 8, 3));
        assertThat(sessions.get(0).startTime()).isEqualTo(LocalTime.of(7, 30));
        assertThat(sessions.get(1).sessionDate()).isEqualTo(LocalDate.of(2026, 8, 5));
        assertThat(sessions.get(1).startTime()).isEqualTo(LocalTime.of(19, 30));
    }

    @Test
    void buildDesiredSessions_emptySchedule_returnsEmpty() {
        LocalDate startDate = LocalDate.now();
        LocalDate endDate = LocalDate.now().plusDays(7);
        UUID trainerId = UUID.randomUUID();

        List<ScheduleValidator.DesiredSession> sessions =
                validator.buildDesiredSessions(startDate, endDate, Map.of(), trainerId, LocalDateTime.now());

        assertThat(sessions).isEmpty();
    }

    @Test
    void buildDesiredSessions_pastCutoff_skipsPastSessions() {
        LocalDate startDate = LocalDate.of(2026, 8, 3); // Monday
        LocalDate endDate = LocalDate.of(2026, 8, 7); // Friday
        UUID trainerId = UUID.randomUUID();
        // Cutoff is Thursday - should only include Friday session
        LocalDateTime cutoff = LocalDateTime.of(2026, 8, 6, 12, 0);

        Map<DayOfWeek, List<TimeRange>> weeklySchedule = Map.of(
                DayOfWeek.MONDAY, List.of(new TimeRange(LocalTime.of(7, 30), LocalTime.of(9, 30))),
                DayOfWeek.WEDNESDAY, List.of(new TimeRange(LocalTime.of(19, 30), LocalTime.of(21, 30))),
                DayOfWeek.FRIDAY, List.of(new TimeRange(LocalTime.of(13, 0), LocalTime.of(15, 0))));

        List<ScheduleValidator.DesiredSession> sessions =
                validator.buildDesiredSessions(startDate, endDate, weeklySchedule, trainerId, cutoff);

        // Should only have Friday (Aug 7) since Monday and Wednesday are before cutoff
        assertThat(sessions).hasSize(1);
        assertThat(sessions.get(0).sessionDate()).isEqualTo(LocalDate.of(2026, 8, 7));
    }

    private ClassOffering createValidClassOffering() {
        ClassOffering offering = new ClassOffering();
        offering.setId(UUID.randomUUID());
        offering.setTrainerId(UUID.randomUUID());
        offering.setStartDate(LocalDate.now().plusDays(1));
        offering.setEndDate(LocalDate.now().plusDays(30));
        offering.setScheduleDescription("""
            [{"dayOfWeek": "MONDAY", "slots": [{"startTime": "07:30", "endTime": "09:30"}]}]
            """);
        return offering;
    }
}
