package com.smartlearnly.backend.classroom.repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public interface ScheduleProjection {

    UUID getSessionId();

    UUID getClassId();

    UUID getCourseId();

    String getCourseTitle();

    String getClassName();

    LocalDate getSessionDate();

    LocalTime getStartTime();

    LocalTime getEndTime();

    UUID getTrainerId();

    String getTrainerName();

    String getMeetingUrl();
}