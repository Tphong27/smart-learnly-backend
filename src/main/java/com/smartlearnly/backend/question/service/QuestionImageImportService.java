package com.smartlearnly.backend.question.service;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.question.dto.QuestionImageImportDtos;
import com.smartlearnly.backend.question.dto.QuestionImportDtos;
import com.smartlearnly.backend.question.entity.Question;
import com.smartlearnly.backend.question.entity.QuestionBank;
import com.smartlearnly.backend.question.entity.QuestionMediaType;
import com.smartlearnly.backend.question.image.ImageImportFile;
import com.smartlearnly.backend.question.image.ImageImportParseResult;
import com.smartlearnly.backend.question.image.ImageImportRequest;
import com.smartlearnly.backend.question.image.ImageQuestionImportProvider;
import com.smartlearnly.backend.question.image.QuestionImageImportProperties;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class QuestionImageImportService {
    private static final String STATUS_VALID = "valid";
    private static final String STATUS_WARNING = "warning";
    private static final String STATUS_ERROR = "error";
    private static final String IMPORT_SOURCE_IMAGE = "image_import";

    private final QuestionBankService questionBankService;
    private final QuestionService questionService;
    private final QuestionMediaService questionMediaService;
    private final ImageQuestionImportProvider provider;
    private final QuestionImageImportProperties properties;
    private final Tika tika = new Tika();

    @Transactional(readOnly = true)
    public QuestionImageImportDtos.PreviewResponse preview(UUID bankId, List<MultipartFile> files, String language) {
        questionBankService.findActiveBankEntity(bankId);
        List<ImageImportFile> imageFiles = readAndValidateFiles(files);
        ImageImportParseResult parseResult = provider.parse(new ImageImportRequest(imageFiles, language));

        List<String> batchWarnings = normalizeMessages(parseResult.warnings());
        List<QuestionImageImportDtos.PreviewQuestion> questions = new ArrayList<>();
        List<QuestionImageImportDtos.PreviewQuestion> parsedQuestions = parseResult.questions() == null
                ? List.of()
                : parseResult.questions();
        for (int index = 0; index < parsedQuestions.size(); index += 1) {
            questions.add(normalizePreviewQuestion(parsedQuestions.get(index), index + 1));
        }
        if (questions.isEmpty()) {
            batchWarnings.add("No questions could be parsed from the uploaded images");
        }

        return new QuestionImageImportDtos.PreviewResponse(
                parseResult.ocrText() == null ? "" : parseResult.ocrText(),
                questions,
                batchWarnings
        );
    }

    @Transactional
    public QuestionImageImportDtos.ConfirmResponse confirm(QuestionImageImportDtos.ConfirmRequest request) {
        return confirm(request, List.of());
    }

    @Transactional
    public QuestionImageImportDtos.ConfirmResponse confirm(QuestionImageImportDtos.ConfirmRequest request, List<MultipartFile> sourceImages) {
        List<MultipartFile> normalizedSourceImages = sourceImages == null ? List.of() : sourceImages;
        List<List<Integer>> sourceImageMappings = validateSourceImageMappings(request.questions(), normalizedSourceImages.size());
        List<QuestionImportDtos.ImportRow> rows = new ArrayList<>();
        for (int index = 0; index < request.questions().size(); index += 1) {
            rows.add(toImportRow(index + 1, request.questions().get(index)));
        }

        List<Question> savedQuestions = questionService.importReviewedRows(
                request.bankId(),
                rows,
                true,
                IMPORT_SOURCE_IMAGE
        );
        attachMappedSourceImages(savedQuestions, sourceImageMappings, normalizedSourceImages);

        List<QuestionImageImportDtos.ConfirmItem> items = savedQuestions.stream()
                .map(question -> new QuestionImageImportDtos.ConfirmItem(
                        question.getId(),
                        question.getQuestionText(),
                        question.getStatus().name().toLowerCase(Locale.ROOT)
                ))
                .toList();
        return new QuestionImageImportDtos.ConfirmResponse(items.size(), 0, items, List.of());
    }

    private void attachMappedSourceImages(List<Question> savedQuestions, List<List<Integer>> sourceImageMappings, List<MultipartFile> sourceImages) {
        for (int questionIndex = 0; questionIndex < savedQuestions.size(); questionIndex += 1) {
            List<Integer> mappedIndexes = sourceImageMappings.get(questionIndex);
            if (mappedIndexes.isEmpty()) {
                continue;
            }
            List<MultipartFile> mappedFiles = mappedIndexes.stream()
                    .map(sourceImages::get)
                    .toList();
            questionMediaService.attachImportedFiles(savedQuestions.get(questionIndex), QuestionMediaType.IMAGE, mappedFiles, IMPORT_SOURCE_IMAGE);
        }
    }

    private List<List<Integer>> validateSourceImageMappings(List<QuestionImageImportDtos.ConfirmQuestion> questions, int sourceImageCount) {
        List<List<Integer>> mappings = new ArrayList<>();
        for (int questionIndex = 0; questionIndex < questions.size(); questionIndex += 1) {
            List<Integer> indexes = questions.get(questionIndex).sourceImageIndexes() == null
                    ? List.of()
                    : questions.get(questionIndex).sourceImageIndexes();
            if (indexes.size() > QuestionMediaService.MAX_IMAGES_PER_QUESTION) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST, "A question can attach at most 5 source images");
            }
            if (!indexes.isEmpty() && sourceImageCount == 0) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST, "Mapped source images must be submitted with the confirm request");
            }
            Set<Integer> seenIndexes = new HashSet<>();
            for (Integer sourceIndex : indexes) {
                if (sourceIndex == null) {
                    throw new BusinessException(ErrorCode.INVALID_REQUEST, "Source image index is required");
                }
                if (!seenIndexes.add(sourceIndex)) {
                    throw new BusinessException(ErrorCode.INVALID_REQUEST, "Source image indexes must be unique per question");
                }
                if (sourceIndex < 0 || sourceIndex >= sourceImageCount) {
                    throw new BusinessException(ErrorCode.INVALID_REQUEST, "Source image index is out of range");
                }
            }
            mappings.add(List.copyOf(indexes));
        }
        return mappings;
    }

    private List<ImageImportFile> readAndValidateFiles(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "At least one image file is required");
        }
        if (files.size() > properties.getMaxFiles()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "A maximum of " + properties.getMaxFiles() + " images can be imported at once");
        }

        List<ImageImportFile> imageFiles = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST, "Image file is required");
            }
            if (file.getSize() > properties.getMaxFileSize().toBytes()) {
                throw new BusinessException(ErrorCode.PAYLOAD_TOO_LARGE, "Each image must not exceed " + properties.getMaxFileSize());
            }
            byte[] content = readBytes(file);
            String contentType = detectContentType(content);
            if (!properties.getAllowedContentTypes().contains(contentType)) {
                throw new BusinessException(ErrorCode.UNSUPPORTED_MEDIA_TYPE, "Image import supports png, jpg, jpeg, and webp files");
            }
            imageFiles.add(new ImageImportFile(file.getOriginalFilename(), contentType, content));
        }
        return imageFiles;
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            byte[] content = file.getBytes();
            if (content.length == 0) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST, "Image file is required");
            }
            return content;
        }
        catch (IOException exception) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Image file could not be read");
        }
    }

    private String detectContentType(byte[] content) {
        try {
            return tika.detect(content);
        }
        catch (Exception exception) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_MEDIA_TYPE, "Image type could not be detected");
        }
    }

    private QuestionImageImportDtos.PreviewQuestion normalizePreviewQuestion(QuestionImageImportDtos.PreviewQuestion question, int questionNumber) {
        List<String> warnings = normalizeMessages(question.warnings());
        List<String> errors = normalizeMessages(question.errors());
        errors.addAll(validateQuestionShape(
                question.questionText(),
                question.questionType(),
                question.answers(),
                question.difficulty(),
                question.explanation()
        ));
        String status = errors.isEmpty() ? (warnings.isEmpty() ? STATUS_VALID : STATUS_WARNING) : STATUS_ERROR;
        return new QuestionImageImportDtos.PreviewQuestion(
                "tmp-" + questionNumber,
                questionNumber,
                status,
                normalizeNullable(question.questionText()),
                normalizeQuestionType(question.questionType()),
                question.answers() == null ? List.of() : question.answers(),
                question.difficulty(),
                normalizeNullable(question.explanation()),
                warnings,
                errors
        );
    }

    private List<String> validateQuestionShape(String questionText, String questionType, List<QuestionImageImportDtos.Answer> answers, Short difficulty, String explanation) {
        List<String> errors = new ArrayList<>();
        String type = normalizeQuestionType(questionType);
        if (questionText == null || questionText.isBlank()) {
            errors.add("Question text is required");
        } else if (questionText.length() > 10000) {
            errors.add("Question text must not exceed 10000 characters");
        }
        if (!"multiple_choice".equals(type) && !"true_false".equals(type)) {
            errors.add("Question type must be multiple_choice or true_false");
        }
        if (answers == null || answers.size() < 2) {
            errors.add("At least two answers are required");
        } else if (answers.size() > 6) {
            errors.add("Multiple choice questions support 2 to 6 answers");
        }
        long correctCount = answers == null ? 0 : answers.stream().filter(QuestionImageImportDtos.Answer::correctValue).count();
        if (correctCount != 1) {
            errors.add("Correct answer key must be explicit and exactly one answer must be marked correct");
        }
        if ("true_false".equals(type)) {
            validateTrueFalsePreviewAnswers(answers, errors);
        }
        if (difficulty != null && (difficulty < 1 || difficulty > 5)) {
            errors.add("Difficulty must be between 1 and 5");
        }
        if (explanation != null && explanation.length() > 10000) {
            errors.add("Explanation must not exceed 10000 characters");
        }
        return errors;
    }

    private void validateTrueFalsePreviewAnswers(List<QuestionImageImportDtos.Answer> answers, List<String> errors) {
        if (answers == null || answers.size() != 2) {
            errors.add("True/false questions must have exactly two answers");
            return;
        }
        boolean hasTrue = false;
        boolean hasFalse = false;
        for (QuestionImageImportDtos.Answer answer : answers) {
            String text = normalizeNullable(answer.answerText());
            if (text == null) continue;
            hasTrue = hasTrue || "true".equalsIgnoreCase(text);
            hasFalse = hasFalse || "false".equalsIgnoreCase(text);
        }
        if (!hasTrue || !hasFalse) {
            errors.add("True/false answers must be True and False");
        }
    }

    private QuestionImportDtos.ImportRow toImportRow(int rowNumber, QuestionImageImportDtos.ConfirmQuestion question) {
        List<String> options = question.answers().stream()
                .map(QuestionImageImportDtos.Answer::answerText)
                .toList();
        String correctAnswer = resolveCorrectAnswer(question.questionType(), question.answers());
        return new QuestionImportDtos.ImportRow(
                rowNumber,
                question.questionText(),
                question.questionType(),
                options,
                correctAnswer,
                question.explanation(),
                question.difficulty(),
                question.bloomLevel(),
                question.moduleId(),
                List.of(),
                List.of()
        );
    }

    private String resolveCorrectAnswer(String questionType, List<QuestionImageImportDtos.Answer> answers) {
        String type = normalizeQuestionType(questionType);
        for (int index = 0; index < answers.size(); index += 1) {
            QuestionImageImportDtos.Answer answer = answers.get(index);
            if (!answer.correctValue()) continue;
            if ("true_false".equals(type)) {
                return answer.answerText();
            }
            return String.valueOf((char) ('A' + index));
        }
        return "";
    }

    private String normalizeQuestionType(String value) {
        if (value == null) return null;
        return value.trim().replace('-', '_').toLowerCase(Locale.ROOT);
    }

    private List<String> normalizeMessages(List<String> messages) {
        if (messages == null) return new ArrayList<>();
        List<String> normalized = new ArrayList<>();
        for (String message : messages) {
            String value = normalizeNullable(message);
            if (value != null) normalized.add(value);
        }
        return normalized;
    }

    private String normalizeNullable(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
