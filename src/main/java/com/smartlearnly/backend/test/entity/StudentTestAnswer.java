package com.smartlearnly.backend.test.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "student_test_answers", schema = "public")
public class StudentTestAnswer {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "attempt_id", nullable = false)
    private UUID attemptId;

    @Column(name = "question_id", nullable = false)
    private UUID questionId;

    @Column(name = "selected_answer_id")
    private UUID selectedAnswerId;

    @Column(name = "essay_answer", columnDefinition = "TEXT")
    private String essayAnswer;

    @Column(name = "is_correct")
    private Boolean isCorrect;

    @Column(name = "score_awarded")
    private BigDecimal scoreAwarded;

    @Column(name = "issue_reported", columnDefinition = "TEXT")
    private String issueReported;
}