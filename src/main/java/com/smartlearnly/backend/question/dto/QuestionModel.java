package com.smartlearnly.backend.question.dto;



import com.smartlearnly.backend.question.entity.BloomLevel;
import com.smartlearnly.backend.question.entity.QuestionStatus;
import com.smartlearnly.backend.question.entity.QuestionType;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public class QuestionModel {

    @Getter
    @Setter
    @NoArgsConstructor
    public static class CreateRequest {
        private UUID questionBankId;
        private UUID courseId;
        private UUID cloId;
        private String questionText;
        private QuestionType questionType;
        private BloomLevel bloomLevel;
        private Short difficulty;
        private String explanation;
        private Boolean isAiGenerated;
        private QuestionStatus status;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class UpdateRequest {
        private UUID cloId;
        private String questionText;
        private QuestionType questionType;
        private BloomLevel bloomLevel;
        private Short difficulty;
        private String explanation;
        private QuestionStatus status;
        private UUID reviewedBy;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Response {
        private UUID id;
        private UUID questionBankId;
        private UUID courseId;
        private UUID cloId;
        private String questionText;
        private QuestionType questionType;
        private BloomLevel bloomLevel;
        private Short difficulty;
        private String explanation;
        private Boolean isAiGenerated;
        private QuestionStatus status;
        private UUID createdBy;
        private UUID reviewedBy;
        private Instant reviewedAt;
        private Instant createdAt;
        private Instant updatedAt;
    }
}