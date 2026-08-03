package com.smartlearnly.backend.classroom.entity;

import java.time.LocalDate;
import java.time.ZoneId;

public final class ClassLifecycle {

    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private ClassLifecycle() {
    }

    public static LocalDate today() {
        return LocalDate.now(BUSINESS_ZONE);
    }

    public static ClassStatus resolveStatus(LocalDate startDate, LocalDate endDate, ClassStatus currentStatus) {
        return resolveStatus(startDate, endDate, currentStatus, today());
    }

    public static ClassStatus resolveStatus(LocalDate startDate, LocalDate endDate, ClassStatus currentStatus,
            LocalDate today) {

        if (startDate == null) {
            throw new IllegalArgumentException("Start date is required to resolve class status");
        }

        if (endDate == null) {
            throw new IllegalArgumentException("End date is required to resolve class status");
        }

        if (today == null) {
            throw new IllegalArgumentException("Current date is required to resolve class status");
        }

        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("End date must not be before start date");
        }

        if (currentStatus == ClassStatus.CANCELLED) {
            return ClassStatus.CANCELLED;
        }

        if (endDate.isBefore(today)) {
            return ClassStatus.COMPLETED;
        }

        // Class dates do not carry a start time. Keep registration open for the
        // whole start date and transition to ONGOING on the following day.
        if (!startDate.isBefore(today)) {
            return ClassStatus.UPCOMING;
        }

        return ClassStatus.ONGOING;
    }
}
