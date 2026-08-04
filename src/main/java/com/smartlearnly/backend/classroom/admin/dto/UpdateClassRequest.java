package com.smartlearnly.backend.classroom.admin.dto;

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

    /** Lấy khóa học mới nếu người quản trị đã gửi trường này trong yêu cầu cập nhật. */
    public UUID getCourseId() {
        return courseId;
    }

    /** Ghi nhận khóa học mới và đánh dấu trường này đã được gửi. */
    public void setCourseId(UUID courseId) {
        this.courseId = courseId;
        this.courseIdProvided = true;
    }

    /** Lấy tên lớp mới nếu có. */
    public String getClassName() {
        return className;
    }

    /** Ghi nhận tên lớp mới và đánh dấu trường này đã được gửi. */
    public void setClassName(String className) {
        this.className = className;
        this.classNameProvided = true;
    }

    /** Lấy giảng viên mới nếu có. */
    public UUID getTrainerId() {
        return trainerId;
    }

    /** Ghi nhận giảng viên mới và đánh dấu trường này đã được gửi. */
    public void setTrainerId(UUID trainerId) {
        this.trainerId = trainerId;
        this.trainerIdProvided = true;
    }

    /** Lấy liên kết phòng học trực tuyến mới nếu có. */
    public String getMeetingUrl() {
        return meetingUrl;
    }

    /** Ghi nhận liên kết phòng học và đánh dấu trường này đã được gửi. */
    public void setMeetingUrl(String meetingUrl) {
        this.meetingUrl = meetingUrl;
        this.meetingUrlProvided = true;
    }

    /** Lấy mô tả lịch học mới nếu có. */
    public String getScheduleDescription() {
        return scheduleDescription;
    }

    /** Ghi nhận mô tả lịch học và đánh dấu trường này đã được gửi. */
    public void setScheduleDescription(
            String scheduleDescription) {
        this.scheduleDescription = scheduleDescription;
        this.scheduleDescriptionProvided = true;
    }

    /** Lấy ngày bắt đầu mới nếu có. */
    public LocalDate getStartDate() {
        return startDate;
    }

    /** Ghi nhận ngày bắt đầu và đánh dấu trường này đã được gửi. */
    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
        this.startDateProvided = true;
    }

    /** Lấy ngày kết thúc mới nếu có. */
    public LocalDate getEndDate() {
        return endDate;
    }

    /** Ghi nhận ngày kết thúc và đánh dấu trường này đã được gửi. */
    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
        this.endDateProvided = true;
    }

    /** Lấy sức chứa mới nếu có. */
    public Integer getMaxStudents() {
        return maxStudents;
    }

    /** Ghi nhận sức chứa và đánh dấu trường này đã được gửi. */
    public void setMaxStudents(Integer maxStudents) {
        this.maxStudents = maxStudents;
        this.maxStudentsProvided = true;
    }

    /** Lấy trạng thái lớp mới nếu có. */
    public String getStatus() {
        return status;
    }

    /** Ghi nhận trạng thái lớp và đánh dấu trường này đã được gửi. */
    public void setStatus(String status) {
        this.status = status;
        this.statusProvided = true;
    }

    /** Lấy học phí mới nếu có. */
    public BigDecimal getPrice() {
        return price;
    }

    /** Ghi nhận học phí và đánh dấu trường này đã được gửi. */
    public void setPrice(BigDecimal price) {
        this.price = price;
        this.priceProvided = true;
    }

    /** Kiểm tra người gọi có gửi trường khóa học hay không. */
    public boolean isCourseIdProvided() {
        return courseIdProvided;
    }

    /** Kiểm tra người gọi có gửi trường tên lớp hay không. */
    public boolean isClassNameProvided() {
        return classNameProvided;
    }

    /** Kiểm tra người gọi có gửi trường giảng viên hay không. */
    public boolean isTrainerIdProvided() {
        return trainerIdProvided;
    }

    /** Kiểm tra người gọi có gửi trường liên kết phòng học hay không. */
    public boolean isMeetingUrlProvided() {
        return meetingUrlProvided;
    }

    /** Kiểm tra người gọi có gửi trường mô tả lịch học hay không. */
    public boolean isScheduleDescriptionProvided() {
        return scheduleDescriptionProvided;
    }

    /** Kiểm tra người gọi có gửi trường ngày bắt đầu hay không. */
    public boolean isStartDateProvided() {
        return startDateProvided;
    }

    /** Kiểm tra người gọi có gửi trường ngày kết thúc hay không. */
    public boolean isEndDateProvided() {
        return endDateProvided;
    }

    /** Kiểm tra người gọi có gửi trường sức chứa hay không. */
    public boolean isMaxStudentsProvided() {
        return maxStudentsProvided;
    }

    /** Kiểm tra người gọi có gửi trường trạng thái hay không. */
    public boolean isStatusProvided() {
        return statusProvided;
    }

    /** Kiểm tra người gọi có gửi trường học phí hay không. */
    public boolean isPriceProvided() {
        return priceProvided;
    }

    /** Kiểm tra yêu cầu PATCH có ít nhất một trường nghiệp vụ để cập nhật. */
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
