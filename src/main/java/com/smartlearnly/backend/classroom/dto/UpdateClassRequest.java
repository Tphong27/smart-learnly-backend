package com.smartlearnly.backend.classroom.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public class UpdateClassRequest {
    private UUID courseId;

    @Size(min = 3, max = 255, message = "Class name must contain between 3 and 255 characters")
    private String className;

    private UUID trainerId;

    @Size(max = 255, message = "Google Meet URL must not exceed 255 characters")
    private String meetingUrl;

    @Size(max = 2000, message = "Class schedule must not exceed 2000 characters")
    private String scheduleDescription;

    private LocalDate startDate;
    private LocalDate endDate;

    @Positive(message = "Capacity must be greater than 0")
    @Max(value = 500, message = "Capacity must not exceed 500")
    private Integer maxStudents;

    @Pattern(regexp = "(?i)upcoming|ongoing|completed|cancelled", message = "Class status must be upcoming, ongoing, completed, or cancelled")
    private String status;

    @DecimalMin(value = "0.0", inclusive = true, message = "Class price must be greater than or equal to 0")
    @DecimalMax(value = "9999999999.99", message = "Class price is too large")
    @Digits(integer = 10, fraction = 2, message = "Class price must contain at most 2 decimal places")
    private BigDecimal price;

    private boolean courseIdProvided;
    private boolean classNameProvided;
    private boolean trainerIdProvided;
    private boolean meetingUrlProvided;
    private boolean scheduleDescriptionProvided;
    private boolean startDateProvided;
    private boolean endDateProvided;
    private boolean maxStudentsProvided;
    private boolean statusProvided;
    private boolean priceProvided;

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

    public String getMeetingUrl() {
        return meetingUrl;
    }

    public void setMeetingUrl(String meetingUrl) {
        this.meetingUrl = meetingUrl;
        this.meetingUrlProvided = true;
    }

    public String getScheduleDescription() {
        return scheduleDescription;
    }

    public void setScheduleDescription(
            String scheduleDescription) {
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

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
        this.priceProvided = true;
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

    public boolean isMeetingUrlProvided() {
        return meetingUrlProvided;
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

    public boolean isPriceProvided() {
        return priceProvided;
    }

    @JsonIgnore
    public boolean hasAnyField() {
        return courseIdProvided
                || classNameProvided
                || trainerIdProvided
                || meetingUrlProvided
                || scheduleDescriptionProvided
                || startDateProvided
                || endDateProvided
                || maxStudentsProvided
                || statusProvided
                || priceProvided;
    }
}