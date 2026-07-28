package com.smartlearnly.backend.classroom.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlearnly.backend.classroom.entity.ClassOffering;
import com.smartlearnly.backend.classroom.entity.ClassSession;
import com.smartlearnly.backend.classroom.entity.ClassTimeSlot;
import com.smartlearnly.backend.classroom.repository.ClassSessionRepository;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ClassSessionScheduleService {
    private static final Pattern TIME_PATTERN = Pattern.compile("^(?:[01]\\d|2[0-3]):[0-5]\\d$");
    private final ClassSessionRepository classSessionRepository;
    private final ObjectMapper objectMapper;

    public void synchronizeFutureSessions(ClassOffering classOffering) {
        LocalDateTime now = LocalDateTime.now();
        Map<DayOfWeek, List<TimeRange>> weeklySchedule = parseSchedule(classOffering.getScheduleDescription());
        Map<SessionKey, DesiredSession> desiredSessions = buildDesiredSessions(classOffering, weeklySchedule, now);

        if (desiredSessions.isEmpty()) {
            throw invalidSchedule("The schedule must create at least one future class session");
        }

        validateTrainerAvailability(classOffering, desiredSessions);

        List<ClassSession> existingSessions = classSessionRepository
                .findByClassIdAndSessionDateGreaterThanEqualOrderBySessionDateAscStartTimeAsc(
                        classOffering.getId(),
                        now.toLocalDate());

        Map<SessionKey, ClassSession> mutableExistingByKey = new HashMap<>();

        for (ClassSession existing : existingSessions) {
            LocalDateTime sessionStart = LocalDateTime.of(existing.getSessionDate(), existing.getStartTime());

            // Session đã bắt đầu hoặc đang diễn ra là lịch sử, không sửa/xóa.
            if (!sessionStart.isAfter(now)) {
                continue;
            }

            SessionKey key = new SessionKey(
                    existing.getSessionDate(),
                    existing.getStartTime(),
                    existing.getEndTime());

            mutableExistingByKey.put(key, existing);
        }

        List<ClassSession> sessionsToSave = new ArrayList<>();

        for (Map.Entry<SessionKey, DesiredSession> entry : desiredSessions.entrySet()) {
            SessionKey key = entry.getKey();
            DesiredSession desired = entry.getValue();

            ClassSession existing = mutableExistingByKey.remove(key);

            if (existing != null) {
                existing.setTrainerId(desired.trainerId());
                sessionsToSave.add(existing);
                continue;
            }

            ClassSession newSession = new ClassSession();
            newSession.setClassId(classOffering.getId());
            newSession.setSessionDate(desired.sessionDate());
            newSession.setStartTime(desired.startTime());
            newSession.setEndTime(desired.endTime());
            newSession.setTrainerId(desired.trainerId());

            sessionsToSave.add(newSession);
        }

        if (!mutableExistingByKey.isEmpty()) {
            classSessionRepository.deleteAll(mutableExistingByKey.values());
        }

        if (!sessionsToSave.isEmpty()) {
            classSessionRepository.saveAll(sessionsToSave);
        }
    }

    private Map<SessionKey, DesiredSession> buildDesiredSessions(
            ClassOffering classOffering,
            Map<DayOfWeek, List<TimeRange>> weeklySchedule,
            LocalDateTime cutoff) {
        Map<SessionKey, DesiredSession> desired = new HashMap<>();

        LocalDate startDate = classOffering.getStartDate();
        LocalDate endDate = classOffering.getEndDate();

        if (startDate == null || endDate == null || weeklySchedule.isEmpty()) {
            return desired;
        }

        LocalDate generationStart = startDate.isBefore(cutoff.toLocalDate()) ? cutoff.toLocalDate() : startDate;

        if (generationStart.isAfter(endDate)) {
            return desired;
        }

        LocalDate currentDate = generationStart;

        while (!currentDate.isAfter(endDate)) {
            List<TimeRange> ranges = weeklySchedule.getOrDefault(currentDate.getDayOfWeek(), List.of());

            for (TimeRange range : ranges) {
                LocalDateTime sessionStart = LocalDateTime.of(currentDate, range.startTime());

                if (!sessionStart.isAfter(cutoff)) {
                    continue;
                }

                SessionKey key = new SessionKey(currentDate, range.startTime(), range.endTime());

                desired.put(key, new DesiredSession(currentDate, range.startTime(), range.endTime(),
                        classOffering.getTrainerId()));
            }

            currentDate = currentDate.plusDays(1);
        }

        return desired;
    }

    private Map<DayOfWeek, List<TimeRange>> parseSchedule(String scheduleDescription) {
        if (scheduleDescription == null || scheduleDescription.isBlank()) {
            throw invalidSchedule("Class schedule is required");
        }

        final JsonNode root;

        try {
            root = objectMapper.readTree(scheduleDescription);
        } catch (Exception exception) {
            throw invalidSchedule("Schedule must be valid JSON");
        }

        if (!root.isArray()) {
            throw invalidSchedule("Schedule must be a JSON array");
        }

        if (root.isEmpty()) {
            throw invalidSchedule("Please select at least one class schedule");
        }

        Map<DayOfWeek, List<TimeRange>> result = new EnumMap<>(DayOfWeek.class);
        Set<DayOfWeek> configuredDays = new HashSet<>();

        for (JsonNode dayNode : root) {
            if (!dayNode.isObject()) {
                throw invalidSchedule("Each schedule day must be a JSON object");
            }

            String dayValue = dayNode.path("dayOfWeek").asText("");

            final DayOfWeek dayOfWeek;

            try {
                dayOfWeek = DayOfWeek.valueOf(dayValue);
            } catch (IllegalArgumentException exception) {
                throw invalidSchedule("Invalid schedule day: " + dayValue);
            }

            if (!configuredDays.add(dayOfWeek)) {
                throw invalidSchedule("Schedule contains duplicate day: " + dayOfWeek);
            }

            JsonNode slotsNode = dayNode.path("slots");

            if (!slotsNode.isArray()) {
                throw invalidSchedule("Schedule slots must be an array");
            }

            if (slotsNode.isEmpty()) {
                throw invalidSchedule("Each selected schedule day must contain at least one time slot");
            }

            List<TimeRange> ranges = new ArrayList<>();
            Set<ClassTimeSlot> configuredSlots = EnumSet.noneOf(ClassTimeSlot.class);

            for (JsonNode slotNode : slotsNode) {
                if (!slotNode.isObject()) {
                    throw invalidSchedule("Each schedule slot must be a JSON object");
                }

                String startValue = slotNode.path("startTime").asText("");
                String endValue = slotNode.path("endTime").asText("");

                if (!TIME_PATTERN.matcher(startValue).matches() || !TIME_PATTERN.matcher(endValue).matches()) {
                    throw invalidSchedule("Schedule time must use HH:mm format");
                }

                final LocalTime startTime;
                final LocalTime endTime;

                try {
                    startTime = LocalTime.parse(startValue);
                    endTime = LocalTime.parse(endValue);
                } catch (DateTimeParseException exception) {
                    throw invalidSchedule("Schedule time must use HH:mm format");
                }

                ClassTimeSlot classTimeSlot = ClassTimeSlot
                        .find(startTime, endTime)
                        .orElseThrow(() -> invalidSchedule(
                                "Class schedule may only use: "
                                        + ClassTimeSlot
                                                .allowedSlotsDescription()));

                if (!configuredSlots.add(classTimeSlot)) {
                    throw invalidSchedule(
                            classTimeSlot.getLabel()
                                    + " is selected more than once on "
                                    + dayOfWeek);
                }

                ranges.add(new TimeRange(
                        classTimeSlot.getStartTime(),
                        classTimeSlot.getEndTime()));
            }

            result.put(dayOfWeek, ranges);
        }

        return result;
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

    private record DesiredSession(LocalDate sessionDate, LocalTime startTime, LocalTime endTime,
            java.util.UUID trainerId) {
    }

    public void validateScheduleDefinition(ClassOffering classOffering) {
        if (classOffering.getTrainerId() == null) {
            throw invalidSchedule("Please select a trainer");
        }

        if (classOffering.getStartDate() == null) {
            throw invalidSchedule("Start date is required");
        }

        if (classOffering.getEndDate() == null) {
            throw invalidSchedule("End date is required");
        }

        Map<DayOfWeek, List<TimeRange>> weeklySchedule = parseSchedule(classOffering.getScheduleDescription());

        Map<SessionKey, DesiredSession> desiredSessions = buildDesiredSessions(
                classOffering,
                weeklySchedule,
                LocalDateTime.now());

        if (desiredSessions.isEmpty()) {
            throw invalidSchedule(
                    "The schedule must create at least one future class session");
        }
    }

    public void deleteFutureSessions(UUID classId) {
        LocalDateTime now = LocalDateTime.now();
        List<ClassSession> sessions = classSessionRepository
                .findByClassIdAndSessionDateGreaterThanEqualOrderBySessionDateAscStartTimeAsc(classId,
                        now.toLocalDate());
        List<ClassSession> mutableSessions = sessions.stream()
                .filter(session -> LocalDateTime.of(
                        session.getSessionDate(),
                        session.getStartTime()).isAfter(now))
                .toList();

        if (!mutableSessions.isEmpty()) {
            classSessionRepository.deleteAll(mutableSessions);
        }
    }

    private void validateTrainerAvailability(ClassOffering classOffering,
            Map<SessionKey, DesiredSession> desiredSessions) {
        LocalDate fromDate = desiredSessions.values()
                .stream()
                .map(DesiredSession::sessionDate)
                .min(LocalDate::compareTo)
                .orElseThrow();

        LocalDate toDate = desiredSessions.values()
                .stream()
                .map(DesiredSession::sessionDate)
                .max(LocalDate::compareTo)
                .orElseThrow();

        List<ClassSession> trainerSessions = classSessionRepository.findTrainerSessionsForConflictCheck(
                classOffering.getTrainerId(),
                classOffering.getId(),
                fromDate,
                toDate);

        for (DesiredSession desired : desiredSessions.values()) {
            for (ClassSession existing : trainerSessions) {
                if (!desired.sessionDate().equals(existing.getSessionDate())) {
                    continue;
                }

                boolean overlaps = desired.startTime().isBefore(existing.getEndTime())
                        && existing.getStartTime().isBefore(desired.endTime());

                if (overlaps) {
                    throw invalidSchedule(
                            "Trainer already has another class on "
                                    + desired.sessionDate()
                                    + " from "
                                    + existing.getStartTime()
                                    + " to "
                                    + existing.getEndTime());
                }
            }
        }
    }
}