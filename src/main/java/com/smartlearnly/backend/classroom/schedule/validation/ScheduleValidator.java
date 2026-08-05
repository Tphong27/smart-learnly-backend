package com.smartlearnly.backend.classroom.schedule.validation;

import com.smartlearnly.backend.classroom.entity.ClassOffering;
import com.smartlearnly.backend.classroom.schedule.validation.ScheduleDescriptionParser.TimeRange;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Validates class offering schedule definitions.
 * Pure business validation logic with no database or repository calls.
 */
@Component
public class ScheduleValidator {

    /**
     * Validates that a class offering has all required schedule fields.
     *
     * @param classOffering the class offering to validate
     * @throws ScheduleValidationException if validation fails
     */
    public void validateScheduleDefinition(ClassOffering classOffering) {
        if (classOffering.getTrainerId() == null) {
            throw new ScheduleValidationException("Please select a trainer");
        }

        if (classOffering.getStartDate() == null) {
            throw new ScheduleValidationException("Start date is required");
        }

        if (classOffering.getEndDate() == null) {
            throw new ScheduleValidationException("End date is required");
        }

        LocalDate startDate = classOffering.getStartDate();
        LocalDate endDate = classOffering.getEndDate();

        if (endDate.isBefore(startDate)) {
            throw new ScheduleValidationException("End date must be after start date");
        }

        Map<DayOfWeek, List<TimeRange>> weeklySchedule =
                parseScheduleSafe(classOffering.getScheduleDescription());

        List<DesiredSession> desiredSessions = buildDesiredSessions(
                startDate, endDate, weeklySchedule, classOffering.getTrainerId(), LocalDateTime.now());

        if (desiredSessions.isEmpty()) {
            throw new ScheduleValidationException(
                    "The schedule must create at least one future class session");
        }
    }

    /**
     * Builds desired sessions from schedule configuration.
     */
    public List<DesiredSession> buildDesiredSessions(
            LocalDate startDate,
            LocalDate endDate,
            Map<DayOfWeek, List<TimeRange>> weeklySchedule,
            UUID trainerId,
            LocalDateTime cutoff) {

        List<DesiredSession> desired = new ArrayList<>();

        if (startDate == null || endDate == null || weeklySchedule.isEmpty()) {
            return desired;
        }

        LocalDate generationStart =
                startDate.isBefore(cutoff.toLocalDate()) ? cutoff.toLocalDate() : startDate;

        if (generationStart.isAfter(endDate)) {
            return desired;
        }

        LocalDate currentDate = generationStart;

        while (!currentDate.isAfter(endDate)) {
            List<TimeRange> ranges =
                    weeklySchedule.getOrDefault(currentDate.getDayOfWeek(), List.of());

            for (TimeRange range : ranges) {
                LocalDateTime sessionStart = LocalDateTime.of(currentDate, range.startTime());

                if (!sessionStart.isAfter(cutoff)) {
                    continue;
                }

                desired.add(new DesiredSession(
                        currentDate, range.startTime(), range.endTime(), trainerId));
            }

            currentDate = currentDate.plusDays(1);
        }

        return desired;
    }

    private Map<DayOfWeek, List<TimeRange>> parseScheduleSafe(String scheduleDescription) {
        try {
            return new ScheduleDescriptionParser(
                    new com.fasterxml.jackson.databind.ObjectMapper())
                    .parse(scheduleDescription);
        } catch (ScheduleParseException e) {
            throw new ScheduleValidationException(e.getMessage());
        }
    }

    /**
     * Represents a desired class session.
     */
    public record DesiredSession(
            LocalDate sessionDate,
            LocalTime startTime,
            LocalTime endTime,
            UUID trainerId) {}
}
