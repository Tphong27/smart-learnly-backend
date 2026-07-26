package com.smartlearnly.backend.test.dto;


import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public class StudentTestAnswerModel {

    // Sinh viên tick chọn đáp án trắc nghiệm hoặc gõ tự luận (Create/Update liên tục khi làm bài)
    @Getter
    @Setter
    @NoArgsConstructor
    public static class SaveRequest {
        private UUID attemptId;
        private UUID questionId;
        private UUID selectedAnswerId; // Dùng cho trắc nghiệm
        private String essayAnswer;     // Dùng cho tự luận
    }

    // Chấm điểm cho từng câu (Dùng khi AI hoặc Trainer chấm câu tự luận)
    @Getter
    @Setter
    @NoArgsConstructor
    public static class GradeRequest {
        private Boolean isCorrect;
        private BigDecimal scoreAwarded;
        private String issueReported;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Response {
        private UUID id;
        private UUID attemptId;
        private UUID questionId;
        private UUID selectedAnswerId;
        private UUID correctAnswerId;
        private String essayAnswer;
        private Boolean isCorrect;
        private BigDecimal scoreAwarded;
        private String issueReported;
    }
}
