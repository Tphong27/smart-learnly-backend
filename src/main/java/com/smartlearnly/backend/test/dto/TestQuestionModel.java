package com.smartlearnly.backend.test.dto;


import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import com.smartlearnly.backend.question.dto.QuestionModel;

public class TestQuestionModel {

    // Thêm một câu hỏi vào đề thi
    @Getter
    @Setter
    @NoArgsConstructor
    public static class AddRequest {
        private UUID testId;
        private UUID questionId;
        private Integer orderIndex;
        private BigDecimal marks;
    }

    // Cập nhật lại số điểm hoặc thứ tự của câu hỏi trong đề
    @Getter
    @Setter
    @NoArgsConstructor
    public static class UpdateRequest {
        private Integer orderIndex;
        private BigDecimal marks;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Response {
        private UUID testId;
        private UUID questionId;
        private Integer orderIndex;
        private BigDecimal marks;
        private String questionText;
        private String questionType;
        private List<QuestionModel.AnswerResponse> answers;
    }
}
