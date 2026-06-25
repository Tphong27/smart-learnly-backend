package com.smartlearnly.backend.question.dto;


import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public class QuestionAnswerModel {

    @Getter
    @Setter
    @NoArgsConstructor
    public static class CreateRequest {
        private UUID questionId;
        private String answerText;
        private Boolean isCorrect;
        private Integer orderIndex;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class UpdateRequest {
        private String answerText;
        private Boolean isCorrect;
        private Integer orderIndex;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Response {
        private UUID id;
        private UUID questionId;
        private String answerText;
        private Boolean isCorrect;
        private Integer orderIndex;
    }
}