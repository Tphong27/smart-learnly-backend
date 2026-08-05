package com.smartlearnly.backend.classroom.schedule.validation;

/**
 * Exception thrown when schedule description parsing fails.
 */
public class ScheduleParseException extends RuntimeException {

    public ScheduleParseException(String message) {
        super(message);
    }

    public ScheduleParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
