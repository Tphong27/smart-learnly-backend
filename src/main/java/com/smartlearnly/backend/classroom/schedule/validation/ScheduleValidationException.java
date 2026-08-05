package com.smartlearnly.backend.classroom.schedule.validation;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;

/**
 * Exception thrown when schedule validation fails.
 */
public class ScheduleValidationException extends BusinessException {

    public ScheduleValidationException(String message) {
        super(ErrorCode.INVALID_REQUEST, message);
    }
}
