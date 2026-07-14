package com.smartlearnly.backend.learning.lesson.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartlearnly.backend.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

class QuizContentValidatorTest {

    private final QuizContentValidator validator = new QuizContentValidator();

    @Test
    void validateShouldAcceptTextOnlyQuestionFormat() {
        validator.validate("""
                {
                  "title": "Quiz",
                  "questions": [
                    {
                      "title": "What is Java?",
                      "type": "single_choice",
                      "number_of_options": 2,
                      "options": ["Language", "Database"],
                      "correct_answers": [1]
                    }
                  ]
                }
                """);
    }

    @Test
    void validateShouldAcceptMediaOnlyQuestionAndMediaOptions() {
        validator.validate("""
                {
                  "title": "Quiz",
                  "questions": [
                    {
                      "title": "",
                      "media": {
                        "type": "audio",
                        "url": "https://cdn.example.com/question.mp3",
                        "objectPath": "2026/07/question.mp3"
                      },
                      "type": "single_choice",
                      "number_of_options": 2,
                      "options": [
                        {
                          "text": "",
                          "media": {
                            "type": "audio",
                            "url": "https://cdn.example.com/a.mp3"
                          }
                        },
                        {
                          "text": "Answer B",
                          "media": null
                        }
                      ],
                      "correct_answers": [1]
                    }
                  ]
                }
                """);
    }

    @Test
    void validateShouldRejectQuestionWithoutTitleOrMedia() {
        assertThatThrownBy(() -> validator.validate("""
                {
                  "questions": [
                    {
                      "title": "",
                      "type": "single_choice",
                      "options": ["A", "B"],
                      "correct_answers": [1]
                    }
                  ]
                }
                """))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("title or media is required");
    }

    @Test
    void validateShouldRejectChoiceOptionWithoutTextOrMedia() {
        assertThatThrownBy(() -> validator.validate("""
                {
                  "questions": [
                    {
                      "title": "Question",
                      "type": "single_choice",
                      "options": [
                        { "text": "", "media": null },
                        "B"
                      ],
                      "correct_answers": [1]
                    }
                  ]
                }
                """))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("option 1 must have text or media");
    }

    @Test
    void validateShouldRejectInvalidMediaType() {
        assertThatThrownBy(() -> validator.validate("""
                {
                  "questions": [
                    {
                      "title": "Question",
                      "media": { "type": "document", "url": "https://cdn.example.com/a.pdf" },
                      "type": "single_choice",
                      "options": ["A", "B"],
                      "correct_answers": [1]
                    }
                  ]
                }
                """))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("media type must be image, video, or audio");
    }

    @Test
    void validateShouldRejectMediaWithoutUrl() {
        assertThatThrownBy(() -> validator.validate("""
                {
                  "questions": [
                    {
                      "title": "Question",
                      "media": { "type": "image", "objectPath": "2026/07/image.png" },
                      "type": "single_choice",
                      "options": ["A", "B"],
                      "correct_answers": [1]
                    }
                  ]
                }
                """))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("media url is required");
    }

    @Test
    void validateShouldRejectChoiceWithLessThanTwoOptions() {
        assertThatThrownBy(() -> validator.validate("""
                {
                  "questions": [
                    {
                      "title": "Question",
                      "type": "single_choice",
                      "options": ["A"],
                      "correct_answers": [1]
                    }
                  ]
                }
                """))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("at least two options are required");
    }
}

