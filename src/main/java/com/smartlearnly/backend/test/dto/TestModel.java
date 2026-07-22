package com.smartlearnly.backend.test.dto;


import com.smartlearnly.backend.test.entity.TestType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public class TestModel {

    @Getter
    @Setter
    @NoArgsConstructor
    public static class CreateRequest {
        private UUID moduleId;
        private UUID classId;
        private UUID courseId;
        private String title;
        private String description;
        private TestType testType;
        private Integer durationMinutes;
        private Integer maxAttempts;
        private BigDecimal passScore;
        private Boolean shuffleQuestions;
        private Boolean shuffleAnswers;
        private Boolean showAnswersAfter;
        private Boolean isFlashtest;
        private Instant opensAt;
        private Instant closesAt;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class UpdateRequest {
        private UUID moduleId;
        private UUID classId;
        private UUID courseId;
        private String title;
        private String description;
        private TestType testType;
        private Integer durationMinutes;
        private Integer maxAttempts;
        private BigDecimal passScore;
        private Boolean shuffleQuestions;
        private Boolean shuffleAnswers;
        private Boolean showAnswersAfter;
        private Boolean isPublished;
        private Boolean isArchived;
        private Boolean isFlashtest;
        private Instant opensAt;
        private Instant closesAt;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Response {
        private UUID id;
        private UUID moduleId;
        private UUID classId;
        private UUID courseId;
        private String title;
        private String description;
        private TestType testType;
        private Integer durationMinutes;
        private Integer maxAttempts;
        private BigDecimal passScore;
        private Boolean shuffleQuestions;
        private Boolean shuffleAnswers;
        private Boolean showAnswersAfter;
        private Boolean isPublished;
        private Boolean isArchived;
        private Boolean isFlashtest;
        private UUID createdBy;
        private Instant createdAt;
        private Instant updatedAt;
        private String accessCode;
        private Instant accessCodeExpiresAt;
        private Instant opensAt;
        private Instant closesAt;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class AccessCodeVerifyRequest {
        private String accessCode;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class AccessCodeVerifyResponse {
        private Boolean valid;
        private Instant expiresAt;
    }
}
