package com.smartlearnly.backend.assignment.definition.dto;


import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Khai báo request và response cho nghiệp vụ quản lý bài tập. */
public class AssignmentModel {

    /** Dữ liệu tạo bài tập mới, có thể gắn với một lesson và lớp cụ thể. */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class CreateRequest {
        private UUID classId;
        private UUID lessonId;
        private String title;
        private String description;
        private String rubric;
        private String instructionFileUrl;
        private String instructionFileName;
        private Instant dueDate;
        private Boolean allowLateSubmission;
        private Instant lockoutDate;
        private BigDecimal maxScore;
        private UUID testId;
        private Boolean isFlashtest;
    }

    /** Dữ liệu cập nhật bài tập, bao gồm classId để sửa liên kết lớp bị thiếu. */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class UpdateRequest {
        private UUID classId;
        private String title;
        private UUID lessonId;
        private String description;
        private String rubric;
        private String instructionFileUrl;
        private String instructionFileName;
        private Instant dueDate;
        private Boolean allowLateSubmission;
        private Instant lockoutDate;
        private BigDecimal maxScore;
        private Boolean isArchived;
        private UUID testId;
        private Boolean isFlashtest;
    }

    /** Dữ liệu bài tập trả về cho màn hình chi tiết và danh sách. */
    /** Dữ liệu lớp có thể được nhân sự chọn khi giao bài tập. */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class Response {
        private UUID id;
        private UUID classId;
        private UUID courseId;
        private UUID lessonId;
        private String title;
        private String description;
        private String rubric;
        private String instructionFileUrl;
        private String instructionFileName;
        private Instant dueDate;
        private Boolean allowLateSubmission;
        private Instant lockoutDate;
        private BigDecimal maxScore;
        private Boolean isArchived;
        private Boolean isFlashtest;
        private UUID createdBy;
        private Instant createdAt;
        private Instant updatedAt;
        private UUID testId;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class ClassOptionResponse {
        private UUID id;
        private UUID courseId;
        private String courseTitle;
        private String className;
        private UUID trainerId;
        private String trainerName;
        private String status;
        private Long activeEnrollmentCount;
        private Integer maxStudents;
    }
}
