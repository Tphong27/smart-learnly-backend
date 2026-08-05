package com.smartlearnly.backend.classroom.schedule.service;

import com.smartlearnly.backend.classroom.entity.ClassOffering;
import com.smartlearnly.backend.classroom.entity.ClassSession;
import com.smartlearnly.backend.classroom.repository.ClassSessionRepository;
import com.smartlearnly.backend.classroom.schedule.validation.ScheduleValidationException;
import com.smartlearnly.backend.classroom.schedule.validation.ScheduleDescriptionParser.TimeRange;
import com.smartlearnly.backend.classroom.schedule.validation.ScheduleValidator.DesiredSession;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import org.springframework.stereotype.Component;

/**
 * Handles synchronization of class sessions based on schedule definitions.
 * Contains pure session management logic with database access.
 */
@Component
public class SessionSyncHandler {

    private final ClassSessionRepository classSessionRepository;

    public SessionSyncHandler(ClassSessionRepository classSessionRepository) {
        this.classSessionRepository = classSessionRepository;
    }

    /**
     * Synchronizes future sessions for a class offering.
     *
     * @param classOffering the class offering to sync
     * @param weeklySchedule parsed schedule by day of week
     * @param desiredSessions list of desired sessions to create/update
     */
    public void synchronizeFutureSessions(
            ClassOffering classOffering,
            Map<DayOfWeek, List<TimeRange>> weeklySchedule,
            List<DesiredSession> desiredSessions) {

        LocalDateTime now = LocalDateTime.now();

        if (desiredSessions.isEmpty()) {
            throw new ScheduleValidationException(
                    "The schedule must create at least one future class session");
        }

        validateTrainerAvailability(classOffering, desiredSessions);

        List<ClassSession> existingSessions =
                classSessionRepository.findByClassIdAndSessionDateGreaterThanEqualOrderBySessionDateAscStartTimeAsc(
                        classOffering.getId(), now.toLocalDate());

        Map<SessionKey, ClassSession> mutableExistingByKey = indexFutureSessions(existingSessions, now);

        List<ClassSession> sessionsToSave = new ArrayList<>();

        for (DesiredSession desired : desiredSessions) {
            SessionKey key = new SessionKey(
                    desired.sessionDate(), desired.startTime(), desired.endTime());

            ClassSession existing = mutableExistingByKey.remove(key);

            if (existing != null) {
                existing.setTrainerId(desired.trainerId());
                sessionsToSave.add(existing);
            } else {
                ClassSession newSession = createNewSession(classOffering, desired);
                sessionsToSave.add(newSession);
            }
        }

        if (!mutableExistingByKey.isEmpty()) {
            classSessionRepository.deleteAll(mutableExistingByKey.values());
        }

        if (!sessionsToSave.isEmpty()) {
            classSessionRepository.saveAll(sessionsToSave);
        }
    }

    /**
     * Deletes future sessions for a class.
     *
     * @param classId the class ID
     */
    public void deleteFutureSessions(UUID classId) {
        LocalDateTime now = LocalDateTime.now();

        List<ClassSession> sessions =
                classSessionRepository.findByClassIdAndSessionDateGreaterThanEqualOrderBySessionDateAscStartTimeAsc(
                        classId, now.toLocalDate());

        List<ClassSession> mutableSessions =
                sessions.stream()
                        .filter(session ->
                                LocalDateTime.of(
                                                session.getSessionDate(),
                                                session.getStartTime())
                                        .isAfter(now))
                        .toList();

        if (!mutableSessions.isEmpty()) {
            classSessionRepository.deleteAll(mutableSessions);
        }
    }

    /**
     * Indexes sessions that haven't started yet.
     */
    Map<SessionKey, ClassSession> indexFutureSessions(
            List<ClassSession> existingSessions, LocalDateTime now) {
        Map<SessionKey, ClassSession> indexed = new HashMap<>();

        for (ClassSession existing : existingSessions) {
            LocalDateTime sessionStart =
                    LocalDateTime.of(existing.getSessionDate(), existing.getStartTime());

            if (!sessionStart.isAfter(now)) {
                continue;
            }

            SessionKey key = new SessionKey(
                    existing.getSessionDate(), existing.getStartTime(), existing.getEndTime());
            indexed.put(key, existing);
        }

        return indexed;
    }

    /**
     * Creates a new session entity from desired session data.
     */
    ClassSession createNewSession(ClassOffering classOffering, DesiredSession desired) {
        ClassSession newSession = new ClassSession();
        newSession.setClassId(classOffering.getId());
        newSession.setSessionDate(desired.sessionDate());
        newSession.setStartTime(desired.startTime());
        newSession.setEndTime(desired.endTime());
        newSession.setTrainerId(desired.trainerId());
        return newSession;
    }

    /**
     * Validates trainer has no conflicting sessions.
     */
    void validateTrainerAvailability(
            ClassOffering classOffering, List<DesiredSession> desiredSessions) {

        if (desiredSessions.isEmpty()) {
            return;
        }

        LocalDate fromDate = desiredSessions.stream()
                .map(DesiredSession::sessionDate)
                .min(LocalDate::compareTo)
                .orElseThrow();

        LocalDate toDate = desiredSessions.stream()
                .map(DesiredSession::sessionDate)
                .max(LocalDate::compareTo)
                .orElseThrow();

        List<ClassSession> trainerSessions =
                classSessionRepository.findTrainerSessionsForConflictCheck(
                        classOffering.getTrainerId(),
                        classOffering.getId(),
                        fromDate,
                        toDate);

        for (DesiredSession desired : desiredSessions) {
            for (ClassSession existing : trainerSessions) {
                if (!desired.sessionDate().equals(existing.getSessionDate())) {
                    continue;
                }

                boolean overlaps =
                        desired.startTime().isBefore(existing.getEndTime())
                                && existing.getStartTime().isBefore(desired.endTime());

                if (overlaps) {
                    throw new ScheduleValidationException(
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

    /**
     * Key for session matching.
     */
    static class SessionKey {
        private final LocalDate sessionDate;
        private final LocalTime startTime;
        private final LocalTime endTime;

        SessionKey(LocalDate sessionDate, LocalTime startTime, LocalTime endTime) {
            this.sessionDate = sessionDate;
            this.startTime = startTime;
            this.endTime = endTime;
        }

        public LocalDate sessionDate() {
            return sessionDate;
        }

        public LocalTime startTime() {
            return startTime;
        }

        public LocalTime endTime() {
            return endTime;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SessionKey that = (SessionKey) o;
            return Objects.equals(sessionDate, that.sessionDate)
                    && Objects.equals(startTime, that.startTime)
                    && Objects.equals(endTime, that.endTime);
        }

        @Override
        public int hashCode() {
            return Objects.hash(sessionDate, startTime, endTime);
        }
    }
}
