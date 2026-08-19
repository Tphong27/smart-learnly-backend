package com.smartlearnly.backend.test.definition.dto;


import com.smartlearnly.backend.test.entity.TestType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
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
        private UUID curriculumSectionId;
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
        private Instant opensAt;
        private Instant closesAt;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class UpdateRequest {
        private UUID moduleId;
        private UUID curriculumSectionId;
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
        private Instant opensAt;
        private Instant closesAt;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class DurationUpdateRequest {
        @NotNull
        @Min(1)
        private Integer durationMinutes;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Response {
        private UUID id;
        private UUID moduleId;
        private UUID curriculumSectionId;
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
        private UUID createdBy;
        private Instant createdAt;
        private Instant updatedAt;
        private String accessCode;
        private Instant accessCodeExpiresAt;
        private Instant opensAt;
        private Instant closesAt;
        private Boolean hasAttempts;
        private Boolean hasActiveAttempts;
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
