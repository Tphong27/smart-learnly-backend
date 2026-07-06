package com.smartlearnly.backend.test.dto;

import com.smartlearnly.backend.question.dto.QuestionModel;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public class TestQuestionModel {

    @Getter
    @Setter
    @NoArgsConstructor
    public static class AddRequest {
        private UUID testId;
        private UUID questionId;
        private Integer orderIndex;
        private BigDecimal marks;
    }

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
        private String imageUrl;
        private String questionType;
        private List<QuestionModel.AnswerResponse> answers;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class LearnerResponse {
        private UUID testId;
        private UUID questionId;
        private Integer orderIndex;
        private BigDecimal marks;
        private String questionText;
        private String imageUrl;
        private String questionType;
        private List<LearnerAnswerResponse> answers;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class LearnerAnswerResponse {
        private UUID answerId;
        private UUID id;
        private String answerText;
        private Integer displayOrder;
        private Integer orderIndex;
    }
}
