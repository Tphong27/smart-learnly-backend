package com.smartlearnly.backend.classroom.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlearnly.backend.classroom.entity.ClassOffering;
import com.smartlearnly.backend.classroom.entity.ClassSession;
import com.smartlearnly.backend.classroom.repository.ClassSessionRepository;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ClassSessionScheduleService {

    private final ClassSessionRepository classSessionRepository;
    private final ObjectMapper objectMapper;

    public void synchronizeFutureSessions(
            ClassOffering classOffering) {
        LocalDate today = LocalDate.now();

        Map<DayOfWeek, List<TimeRange>> weeklySchedule = parseSchedule(
                classOffering.getScheduleDescription());

        Map<SessionKey, DesiredSession> desiredSessions = buildDesiredSessions(
                classOffering,
                weeklySchedule,
                today);

        List<ClassSession> existingSessions = classSessionRepository
                .findByClassIdAndSessionDateGreaterThanEqualOrderBySessionDateAscStartTimeAsc(
                        classOffering.getId(),
                        today);

        Map<SessionKey, ClassSession> existingByKey = new HashMap<>();

        for (ClassSession existing : existingSessions) {
            SessionKey key = new SessionKey(
                    existing.getSessionDate(),
                    existing.getStartTime(),
                    existing.getEndTime());

            existingByKey.put(key, existing);
        }

        List<ClassSession> sessionsToSave = new ArrayList<>();

        for (Map.Entry<SessionKey, DesiredSession> entry : desiredSessions.entrySet()) {

            SessionKey key = entry.getKey();
            DesiredSession desired = entry.getValue();

            ClassSession existing = existingByKey.remove(key);

            if (existing != null) {
                /*
                 * Keep meetingUrl because it may have been assigned
                 * specifically to this session.
                 */
                existing.setTrainerId(
                        desired.trainerId());

                sessionsToSave.add(existing);
                continue;
            }

            ClassSession newSession = new ClassSession();
            newSession.setClassId(
                    classOffering.getId());
            newSession.setSessionDate(
                    desired.sessionDate());
            newSession.setStartTime(
                    desired.startTime());
            newSession.setEndTime(
                    desired.endTime());
            newSession.setTrainerId(
                    desired.trainerId());

            /*
             * WeeklySchedulePicker currently does not collect
             * meeting URLs. They can be assigned later.
             */
            newSession.setMeetingUrl(null);

            sessionsToSave.add(newSession);
        }

        /*
         * Sessions remaining in existingByKey no longer appear
         * in the updated weekly schedule.
         */
        if (!existingByKey.isEmpty()) {
            classSessionRepository.deleteAll(
                    existingByKey.values());
        }

        if (!sessionsToSave.isEmpty()) {
            classSessionRepository.saveAll(
                    sessionsToSave);
        }
    }

    private Map<SessionKey, DesiredSession> buildDesiredSessions(
            ClassOffering classOffering,
            Map<DayOfWeek, List<TimeRange>> weeklySchedule,
            LocalDate today) {

        Map<SessionKey, DesiredSession> desired = new HashMap<>();

        LocalDate startDate = classOffering.getStartDate();

        LocalDate endDate = classOffering.getEndDate();

        if (startDate == null
                || endDate == null
                || weeklySchedule.isEmpty()) {

            return desired;
        }

        LocalDate generationStart = startDate.isBefore(today)
                ? today
                : startDate;

        if (generationStart.isAfter(endDate)) {
            return desired;
        }

        LocalDate currentDate = generationStart;

        while (!currentDate.isAfter(endDate)) {
            List<TimeRange> ranges = weeklySchedule.getOrDefault(
                    currentDate.getDayOfWeek(),
                    List.of());

            for (TimeRange range : ranges) {
                SessionKey key = new SessionKey(
                        currentDate,
                        range.startTime(),
                        range.endTime());

                desired.put(
                        key,
                        new DesiredSession(
                                currentDate,
                                range.startTime(),
                                range.endTime(),
                                classOffering.getTrainerId()));
            }

            currentDate = currentDate.plusDays(1);
        }

        return desired;
    }

    private Map<DayOfWeek, List<TimeRange>> parseSchedule(String scheduleDescription) {

        Map<DayOfWeek, List<TimeRange>> result = new EnumMap<>(DayOfWeek.class);

        if (scheduleDescription == null
                || scheduleDescription.isBlank()) {

            return result;
        }

        final JsonNode root;

        try {
            root = objectMapper.readTree(
                    scheduleDescription);
        } catch (Exception exception) {
            throw invalidSchedule(
                    "Schedule must be valid JSON");
        }

        if (!root.isArray()) {
            throw invalidSchedule(
                    "Schedule must be a JSON array");
        }

        for (JsonNode dayNode : root) {
            String dayValue = dayNode.path("dayOfWeek").asText("");

            final DayOfWeek dayOfWeek;

            try {
                dayOfWeek = DayOfWeek.valueOf(
                        dayValue);
            } catch (IllegalArgumentException exception) {
                throw invalidSchedule(
                        "Invalid schedule day: " + dayValue);
            }

            JsonNode slotsNode = dayNode.path("slots");

            if (!slotsNode.isArray()) {
                throw invalidSchedule(
                        "Schedule slots must be an array");
            }

            List<TimeRange> ranges = result.computeIfAbsent(
                    dayOfWeek,
                    ignored -> new ArrayList<>());

            for (JsonNode slotNode : slotsNode) {
                String startValue = slotNode.path("startTime").asText("");

                String endValue = slotNode.path("endTime").asText("");

                final LocalTime startTime;
                final LocalTime endTime;

                try {
                    startTime = LocalTime.parse(
                            startValue);
                    endTime = LocalTime.parse(
                            endValue);
                } catch (DateTimeParseException exception) {
                    throw invalidSchedule(
                            "Schedule time must use HH:mm format");
                }

                if (!endTime.isAfter(startTime)) {
                    throw invalidSchedule(
                            "Schedule end time must be after start time");
                }

                ranges.add(
                        new TimeRange(
                                startTime,
                                endTime));
            }
        }

        validateNoOverlaps(result);

        return result;
    }

    private void validateNoOverlaps(
            Map<DayOfWeek, List<TimeRange>> schedule) {
        for (Map.Entry<DayOfWeek, List<TimeRange>> entry : schedule.entrySet()) {

            List<TimeRange> ranges = new ArrayList<>(entry.getValue());

            ranges.sort(Comparator.comparing(TimeRange::startTime));

            for (int index = 1; index < ranges.size(); index++) {

                TimeRange previous = ranges.get(index - 1);

                TimeRange current = ranges.get(index);

                if (current.startTime()
                        .isBefore(previous.endTime())) {

                    throw invalidSchedule(
                            "Schedule slots overlap on "
                                    + entry.getKey());
                }
            }
        }
    }

    private BusinessException invalidSchedule(String message) {
        return new BusinessException(
                ErrorCode.INVALID_REQUEST,
                message);
    }

    private record TimeRange(LocalTime startTime, LocalTime endTime) {
    }

    private record SessionKey(LocalDate sessionDate, LocalTime startTime, LocalTime endTime) {
    }

    private record DesiredSession(LocalDate sessionDate, LocalTime startTime, LocalTime endTime, java.util.UUID trainerId) {
    }
}