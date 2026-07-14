package com.smartlearnly.backend.assignment.dto;


import com.smartlearnly.backend.assignment.entity.SubmissionStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public class AssignmentSubmissionModel {

    // Học sinh nộp bài lần đầu (Create)
    @Getter
    @Setter
    @NoArgsConstructor
    public static class CreateRequest {
        private UUID assignmentId;
        private UUID studentId;
        private String studentName;
        private String submissionText;
        private String fileUrl;
        private String fileName;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class StartRequest {
        private UUID assignmentId;
        private UUID studentId;
        private String studentName;
    }

    // Học sinh nộp lại hoặc sửa bài (Update)
    @Getter
    @Setter
    @NoArgsConstructor
    public static class UpdateRequest {
        private String submissionText;
        private String fileUrl;
        private String fileName;
    }

    // Giảng viên hoặc AI cập nhật điểm và feedback (Grade/Update)
    @Getter
    @Setter
    @NoArgsConstructor
    public static class GradeRequest {
        private BigDecimal score;
        private String aiFeedback;
        private String trainerFeedback;
        private SubmissionStatus status;
        private UUID gradedBy;
    }

    // Phản hồi dữ liệu nộp bài (List/Detail)
    @Getter
    @Setter
    @NoArgsConstructor
    public static class Response {
        private UUID id;
        private UUID assignmentId;
        private UUID studentId;
        private String studentName;
        private String submissionText;
        private String fileUrl;
        private String fileName;
        private Instant startTime;
        private Instant submittedAt;
        private Boolean isLate;
        private BigDecimal score;
        private String aiFeedback;
        private String trainerFeedback;
        private SubmissionStatus status;
        private UUID gradedBy;
        private Instant gradedAt;
        private Instant createdAt;
        private Instant updatedAt;
    }
}
