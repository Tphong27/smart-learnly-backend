package com.smartlearnly.backend.test.dto;


import com.smartlearnly.backend.test.entity.AttemptStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public class TestAttemptModel {

    // Sinh viên bắt đầu ấn làm bài test (Create)
    @Getter
    @Setter
    @NoArgsConstructor
    public static class StartRequest {
        private UUID testId;
        private UUID studentId;
        private String studentName;
        private UUID assignmentId;
        private String accessCode;
    }

    // Sinh viên nộp bài / Hoặc hết giờ tự thu bài (Update)
    @Getter
    @Setter
    @NoArgsConstructor
    public static class SubmitRequest {
        private Boolean forceSubmit;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Response {
        private UUID id;
        private UUID testId;
        private UUID studentId;
        private String studentName;
        private Instant startTime;
        private Instant endTime;
        private BigDecimal score;
        private BigDecimal percentage;
        private AttemptStatus status;
        private Instant createdAt;
        private UUID assignmentId;
        private Boolean retakeAllowed;
    }
}
