package com.smartlearnly.backend.assignment.dto;


import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public class AssignmentModel {

    // Request dùng cho API Thêm mới (Create)
    @Getter
    @Setter
    @NoArgsConstructor
    public static class CreateRequest {
        private UUID classId;
        private String title;
        private String description;
        private String instructionFileUrl;
        private String instructionFileName;
        private Instant dueDate;
        private Boolean allowLateSubmission;
        private Instant lockoutDate;
        private BigDecimal maxScore;
        private UUID testId;
    }

    // Request dùng cho API Cập nhật (Update)
    @Getter
    @Setter
    @NoArgsConstructor
    public static class UpdateRequest {
        private String title;
        private String description;
        private String instructionFileUrl;
        private String instructionFileName;
        private Instant dueDate;
        private Boolean allowLateSubmission;
        private Instant lockoutDate;
        private BigDecimal maxScore;
        private Boolean isArchived;
        private UUID testId;
    }

    // Response dùng cho API Xem chi tiết và Danh sách (Get/List)
    @Getter
    @Setter
    @NoArgsConstructor
    public static class Response {
        private UUID id;
        private UUID classId;
        private String title;
        private String description;
        private String instructionFileUrl;
        private String instructionFileName;
        private Instant dueDate;
        private Boolean allowLateSubmission;
        private Instant lockoutDate;
        private BigDecimal maxScore;
        private Boolean isArchived;
        private UUID createdBy;
        private Instant createdAt;
        private Instant updatedAt;
        private UUID testId;
    }
}
