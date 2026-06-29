package com.smartlearnly.backend.test.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "test_questions", schema = "public")
public class TestQuestion {

    @EmbeddedId
    private TestQuestionId id;

    @Column(name = "order_index", nullable = false)
    private Integer orderIndex;

    @Column(nullable = false)
    private BigDecimal marks;
    
    // Static Inner Class cho Composite Key
    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class TestQuestionId implements Serializable {
        @Column(name = "test_id")
        private UUID testId;

        @Column(name = "question_id")
        private UUID questionId;
    }
}
