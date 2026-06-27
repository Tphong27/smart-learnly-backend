package com.smartlearnly.backend.learning.lesson.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Validate nội dung quiz (JSON string lưu trong lesson.content) phía server.
 * Hỗ trợ cả định dạng cũ (question/correctIndex) lẫn định dạng mới.
 */
@Component
public class QuizContentValidator {

    private static final String SINGLE_CHOICE = "single_choice";
    private static final String MULTIPLE_CHOICE = "multiple_choice";
    private static final String FILL_IN_THE_BLANK = "fill_in_the_blank";
    private static final Set<String> VALID_TYPES =
            Set.of(SINGLE_CHOICE, MULTIPLE_CHOICE, FILL_IN_THE_BLANK);

    private final ObjectMapper objectMapper = new ObjectMapper();

    public void validate(String content) {
        if (content == null || content.isBlank()) {
            return; // quiz rỗng được phép (chưa có câu hỏi)
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(content);
        } catch (Exception exception) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_FAILED, "Quiz content is not valid JSON.");
        }

        if (!root.isObject()) {
            throw invalid("Quiz content must be a JSON object with a questions array.");
        }

        JsonNode questions = root.get("questions");
        if (questions == null || questions.isNull()) {
            return; // không có questions -> hợp lệ (chỉ có title)
        }
        if (!questions.isArray()) {
            throw invalid("questions must be an array.");
        }

        for (int i = 0; i < questions.size(); i++) {
            validateQuestion(questions.get(i), i + 1);
        }
    }

    private void validateQuestion(JsonNode question, int qNum) {
        if (question == null || !question.isObject()) {
            throw invalid("Question " + qNum + ": must be an object.");
        }

        // Bỏ qua câu hỏi định dạng cũ để giữ tương thích dữ liệu cũ.
        if (isLegacyQuestion(question)) {
            return;
        }

        if (!hasText(question, "title")) {
            throw invalid("Question " + qNum + ": title is required.");
        }

        String type = question.path("type").asText(null);
        if (type == null || !VALID_TYPES.contains(type)) {
            throw invalid(
                    "Question " + qNum
                            + ": type must be one of single_choice, multiple_choice, fill_in_the_blank.");
        }

        JsonNode correctAnswers = question.get("correct_answers");
        if (correctAnswers == null || !correctAnswers.isArray()) {
            throw invalid(
                    "Question " + qNum + ": correct_answers is required and must be an array.");
        }

        switch (type) {
            case SINGLE_CHOICE -> validateChoice(question, correctAnswers, qNum, true);
            case MULTIPLE_CHOICE -> validateChoice(question, correctAnswers, qNum, false);
            case FILL_IN_THE_BLANK -> validateFill(correctAnswers, qNum);
            default -> { /* unreachable */ }
        }
    }

    private void validateChoice(
            JsonNode question, JsonNode correctAnswers, int qNum, boolean single) {
        JsonNode options = question.get("options");
        if (options == null || !options.isArray() || options.isEmpty()) {
            throw invalid("Question " + qNum + ": options is required.");
        }
        int optionCount = options.size();

        JsonNode numberOfOptions = question.get("number_of_options");
        if (numberOfOptions != null && numberOfOptions.isNumber()
                && numberOfOptions.asInt() != optionCount) {
            throw invalid(
                    "Question " + qNum + ": number_of_options (" + numberOfOptions.asInt()
                            + ") must equal options.length (" + optionCount + ").");
        }

        if (single && correctAnswers.size() != 1) {
            throw invalid(
                    "Question " + qNum + ": single_choice must have exactly one correct answer.");
        }
        if (!single && correctAnswers.isEmpty()) {
            throw invalid(
                    "Question " + qNum + ": multiple_choice must have at least one correct answer.");
        }

        Set<Integer> seen = new HashSet<>();
        for (JsonNode answer : correctAnswers) {
            if (!answer.isInt()) {
                throw invalid(
                        "Question " + qNum + ": correct_answers must contain integers.");
            }
            int idx = answer.asInt();
            if (idx < 1 || idx > optionCount) {
                throw invalid(
                        "Question " + qNum
                                + ": correct_answers contains invalid option index " + idx + ".");
            }
            if (!seen.add(idx)) {
                throw invalid(
                        "Question " + qNum
                                + ": correct_answers contains duplicate option index " + idx + ".");
            }
        }
    }

    private void validateFill(JsonNode correctAnswers, int qNum) {
        if (correctAnswers.isEmpty()) {
            throw invalid(
                    "Question " + qNum
                            + ": fill_in_the_blank must have at least one accepted answer.");
        }
        for (JsonNode answer : correctAnswers) {
            if (!answer.isTextual() || answer.asText().isBlank()) {
                throw invalid(
                        "Question " + qNum
                                + ": fill_in_the_blank correct_answers must be non-empty strings.");
            }
        }
    }

    private boolean isLegacyQuestion(JsonNode question) {
        return !question.has("type")
                && (question.has("question") || question.has("correctIndex"));
    }

    private boolean hasText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isTextual() && !value.asText().isBlank();
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.VALIDATION_FAILED, message);
    }
}
