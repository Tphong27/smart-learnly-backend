package com.smartlearnly.backend.classroom.schedule.service;

import com.smartlearnly.backend.classroom.entity.ClassOffering;
import com.smartlearnly.backend.classroom.schedule.validation.ScheduleDescriptionParser;
import com.smartlearnly.backend.classroom.schedule.validation.ScheduleDescriptionParser.TimeRange;
import com.smartlearnly.backend.classroom.schedule.validation.ScheduleParseException;
import com.smartlearnly.backend.classroom.schedule.validation.ScheduleValidator;
import com.smartlearnly.backend.classroom.schedule.validation.ScheduleValidator.DesiredSession;
import com.smartlearnly.backend.classroom.schedule.validation.ScheduleValidationException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Main service for class session scheduling operations.
 * Delegates to specialized components for parsing, validation, and synchronization.
 */
@Service
public class ClassSessionScheduleService {

    private final ScheduleDescriptionParser parser;
    private final ScheduleValidator validator;
    private final SessionSyncHandler syncHandler;

    public ClassSessionScheduleService(
            ScheduleDescriptionParser parser,
            ScheduleValidator validator,
            SessionSyncHandler syncHandler) {
        this.parser = parser;
        this.validator = validator;
        this.syncHandler = syncHandler;
    }

    /**
     * Synchronizes future sessions for a class offering based on its schedule definition.
     *
     * @param classOffering the class offering to sync
     */
    public void synchronizeFutureSessions(ClassOffering classOffering) {
        LocalDateTime now = LocalDateTime.now();

        Map<DayOfWeek, List<TimeRange>> weeklySchedule;
        try {
            weeklySchedule = parser.parse(classOffering.getScheduleDescription());
        } catch (ScheduleParseException e) {
            throw new ScheduleValidationException(e.getMessage());
        }

        List<DesiredSession> desiredSessions =
                validator.buildDesiredSessions(
                        classOffering.getStartDate(),
                        classOffering.getEndDate(),
                        weeklySchedule,
                        classOffering.getTrainerId(),
                        now);

        syncHandler.synchronizeFutureSessions(classOffering, weeklySchedule, desiredSessions);
    }

    /**
     * Validates that a class offering has a complete and valid schedule definition.
     *
     * @param classOffering the class offering to validate
     * @throws ScheduleValidationException if validation fails
     */
    public void validateScheduleDefinition(ClassOffering classOffering) {
        validator.validateScheduleDefinition(classOffering);
    }

    /**
     * Deletes all future sessions for a class.
     *
     * @param classId the class ID
     */
    public void deleteFutureSessions(UUID classId) {
        syncHandler.deleteFutureSessions(classId);
    }
}
