package com.smartlearnly.backend.classroom.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public class UpdateClassRequest {
    private UUID courseId;

    @Size(max = 255)
    private String className;

    private UUID trainerId;

    @Size(max = 2000)
    private String scheduleDescription;

    private LocalDate startDate;
    private LocalDate endDate;

    @Positive
    private Integer maxStudents;
    private String status;

    private boolean courseIdProvided;
    private boolean classNameProvided;
    private boolean trainerIdProvided;
    private boolean scheduleDescriptionProvided;
    private boolean startDateProvided;
    private boolean endDateProvided;
    private boolean maxStudentsProvided;
    private boolean statusProvided;

    public UUID getCourseId() {
        return courseId;
    }

    public void setCourseId(UUID courseId) {
        this.courseId = courseId;
        this.courseIdProvided = true;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
        this.classNameProvided = true;
    }

    public UUID getTrainerId() {
        return trainerId;
    }

    public void setTrainerId(UUID trainerId) {
        this.trainerId = trainerId;
        this.trainerIdProvided = true;
    }

    public String getScheduleDescription() {
        return scheduleDescription;
    }

    public void setScheduleDescription(String scheduleDescription) {
        this.scheduleDescription = scheduleDescription;
        this.scheduleDescriptionProvided = true;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
        this.startDateProvided = true;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
        this.endDateProvided = true;
    }

    public Integer getMaxStudents() {
        return maxStudents;
    }

    public void setMaxStudents(Integer maxStudents) {
        this.maxStudents = maxStudents;
        this.maxStudentsProvided = true;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
        this.statusProvided = true;
    }

    public boolean isCourseIdProvided() {
        return courseIdProvided;
    }

    public boolean isClassNameProvided() {
        return classNameProvided;
    }

    public boolean isTrainerIdProvided() {
        return trainerIdProvided;
    }

    public boolean isScheduleDescriptionProvided() {
        return scheduleDescriptionProvided;
    }

    public boolean isStartDateProvided() {
        return startDateProvided;
    }

    public boolean isEndDateProvided() {
        return endDateProvided;
    }

    public boolean isMaxStudentsProvided() {
        return maxStudentsProvided;
    }

    public boolean isStatusProvided() {
        return statusProvided;
    }

    @JsonIgnore
    public boolean hasAnyField() {
        return courseIdProvided || classNameProvided || trainerIdProvided
                || scheduleDescriptionProvided || startDateProvided
                || endDateProvided || maxStudentsProvided || statusProvided;
    }
}
