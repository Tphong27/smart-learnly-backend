package com.smartlearnly.backend.classroom.schedule.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlearnly.backend.classroom.entity.ClassTimeSlot;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Parses JSON schedule description into structured TimeRange data by DayOfWeek.
 * Pure transformation logic with no business rules or validation side effects.
 */
@Component
public class ScheduleDescriptionParser {

    private static final Pattern TIME_PATTERN = Pattern.compile("^(?:[01]\\d|2[0-3]):[0-5]\\d$");

    private final ObjectMapper objectMapper;

    public ScheduleDescriptionParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Parses a JSON schedule description into a map of day-of-week to time ranges.
     *
     * @param scheduleDescription JSON string describing the schedule
     * @return map with day-of-week as key and list of time ranges as value
     * @throws ScheduleParseException if the JSON is malformed or contains invalid data
     */
    public Map<DayOfWeek, List<TimeRange>> parse(String scheduleDescription) {
        if (scheduleDescription == null || scheduleDescription.isBlank()) {
            throw new ScheduleParseException("Class schedule is required");
        }

        final JsonNode root;
        try {
            root = objectMapper.readTree(scheduleDescription);
        } catch (Exception exception) {
            throw new ScheduleParseException("Schedule must be valid JSON");
        }

        if (!root.isArray()) {
            throw new ScheduleParseException("Schedule must be a JSON array");
        }

        if (root.isEmpty()) {
            throw new ScheduleParseException("Please select at least one class schedule");
        }

        Map<DayOfWeek, List<TimeRange>> result = new EnumMap<>(DayOfWeek.class);
        Set<DayOfWeek> configuredDays = new HashSet<>();

        for (JsonNode dayNode : root) {
            if (!dayNode.isObject()) {
                throw new ScheduleParseException("Each schedule day must be a JSON object");
            }

            String dayValue = dayNode.path("dayOfWeek").asText("");
            final DayOfWeek dayOfWeek;

            try {
                dayOfWeek = DayOfWeek.valueOf(dayValue);
            } catch (IllegalArgumentException exception) {
                throw new ScheduleParseException("Invalid schedule day: " + dayValue);
            }

            if (!configuredDays.add(dayOfWeek)) {
                throw new ScheduleParseException("Schedule contains duplicate day: " + dayOfWeek);
            }

            JsonNode slotsNode = dayNode.path("slots");

            if (!slotsNode.isArray()) {
                throw new ScheduleParseException("Schedule slots must be an array");
            }

            if (slotsNode.isEmpty()) {
                throw new ScheduleParseException(
                        "Each selected schedule day must contain at least one time slot");
            }

            List<TimeRange> ranges = new ArrayList<>();
            Set<ClassTimeSlot> configuredSlots = EnumSet.noneOf(ClassTimeSlot.class);

            for (JsonNode slotNode : slotsNode) {
                if (!slotNode.isObject()) {
                    throw new ScheduleParseException("Each schedule slot must be a JSON object");
                }

                String startValue = slotNode.path("startTime").asText("");
                String endValue = slotNode.path("endTime").asText("");

                if (!TIME_PATTERN.matcher(startValue).matches()
                        || !TIME_PATTERN.matcher(endValue).matches()) {
                    throw new ScheduleParseException("Schedule time must use HH:mm format");
                }

                final LocalTime startTime;
                final LocalTime endTime;

                try {
                    startTime = LocalTime.parse(startValue);
                    endTime = LocalTime.parse(endValue);
                } catch (DateTimeParseException exception) {
                    throw new ScheduleParseException("Schedule time must use HH:mm format");
                }

                ClassTimeSlot classTimeSlot =
                        ClassTimeSlot.find(startTime, endTime)
                                .orElseThrow(() -> new ScheduleParseException(
                                        "Class schedule may only use: "
                                                + ClassTimeSlot.allowedSlotsDescription()));

                if (!configuredSlots.add(classTimeSlot)) {
                    throw new ScheduleParseException(
                            classTimeSlot.getLabel()
                                    + " is selected more than once on "
                                    + dayOfWeek);
                }

                ranges.add(new TimeRange(
                        classTimeSlot.getStartTime(), classTimeSlot.getEndTime()));
            }

            result.put(dayOfWeek, ranges);
        }

        return result;
    }

    /**
     * Represents a time range with start and end times.
     */
    public record TimeRange(LocalTime startTime, LocalTime endTime) {}
}
