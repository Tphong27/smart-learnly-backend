package com.smartlearnly.backend.question.service;

import com.smartlearnly.backend.common.api.PageResponse;
import com.smartlearnly.backend.common.audit.AuditAction;
import com.smartlearnly.backend.common.audit.CourseAuditRecorder;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.course.access.service.CourseAccessService;
import com.smartlearnly.backend.learning.module.entity.CourseModule;
import com.smartlearnly.backend.learning.module.repository.CourseModuleRepository;
import com.smartlearnly.backend.question.dto.QuestionAnswerMediaResponse;
import com.smartlearnly.backend.question.dto.QuestionImportDtos;
import com.smartlearnly.backend.question.dto.QuestionMediaAttachmentResponse;
import com.smartlearnly.backend.question.dto.QuestionModel;
import com.smartlearnly.backend.question.entity.QuestionAnswerMediaAttachment;
import com.smartlearnly.backend.question.entity.BloomLevel;
import com.smartlearnly.backend.question.entity.Question;
import com.smartlearnly.backend.question.entity.QuestionAnswer;
import com.smartlearnly.backend.question.entity.QuestionMediaAttachment;
import com.smartlearnly.backend.question.entity.QuestionMediaType;
import com.smartlearnly.backend.question.entity.QuestionStatus;
import com.smartlearnly.backend.question.entity.QuestionType;
import com.smartlearnly.backend.question.repository.QuestionAnswerMediaAttachmentRepository;
import com.smartlearnly.backend.question.repository.QuestionAnswerRepository;
import com.smartlearnly.backend.question.repository.QuestionMediaAttachmentRepository;
import com.smartlearnly.backend.question.repository.QuestionRepository;
import com.smartlearnly.backend.test.repository.StudentTestAnswerRepository;
import com.smartlearnly.backend.user.entity.UserAccount;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class QuestionService {
    private static final int MIN_ANSWERS = 2;
    private static final int MAX_MCQ_ANSWERS = 6;
    private static final String SUPPORTED_QUESTION_TYPE_MESSAGE = "Question type must be single_choice, multiple_choice, or true_false";
    private static final Set<QuestionType> SUPPORTED_QUESTION_TYPES = Set.of(
            QuestionType.SINGLE_CHOICE,
            QuestionType.MULTIPLE_CHOICE,
            QuestionType.TRUE_FALSE
    );

    private final QuestionRepository questionRepository;
    private final QuestionAnswerRepository answerRepository;
    private final QuestionAnswerMediaAttachmentRepository answerMediaRepository;
    private final QuestionMediaAttachmentRepository mediaAttachmentRepository;
    private final CourseModuleRepository courseModuleRepository;
    private final CurrentUserService currentUserService;
    private final QuestionMediaImportService questionMediaImportService;
    private final CourseAccessService courseAccessService;
    private final StudentTestAnswerRepository studentTestAnswerRepository;
    private final CourseAuditRecorder courseAuditRecorder;

    @Transactional(readOnly = true)
    public PageResponse<QuestionModel.Response> listByCourse(UUID courseId, UUID moduleId, String search, String type, String status, boolean includeArchived, Short difficulty, int page, int size) {
        courseAccessService.requireReadableCourse(courseId);
        String normalizedSearch = normalizeNullable(search);
        String normalizedType = type == null || type.isBlank()
                ? null
                : parseSupportedQuestionType(type).name().toLowerCase(Locale.ROOT);
        String normalizedStatus = status == null || status.isBlank()
                ? null
                : parseQuestionStatus(status, null).name().toLowerCase(Locale.ROOT);
        Page<Question> questionPage = questionRepository.searchForAdmin(
                courseId,
                moduleId,
                normalizedSearch,
                normalizedType,
                normalizedStatus,
                includeArchived,
                difficulty,
                PageRequest.of(page, size)
        );
        return new PageResponse<>(questionPage.getContent().stream().map(this::toResponse).toList(), questionPage.getNumber(), questionPage.getSize(), questionPage.getTotalElements(), questionPage.getTotalPages());
    }

    /** Liệt kê câu hỏi của đúng module đang mở và từ chối module không thuộc course. */
    @Transactional(readOnly = true)
    public PageResponse<QuestionModel.Response> listByCourseModule(UUID courseId, UUID moduleId, String search, String type, String status, boolean includeArchived, Short difficulty, int page, int size) {
        courseAccessService.requireReadableCourse(courseId);
        UUID resolvedModuleId = validateRequiredCourseModuleId(courseId, moduleId);
        return listByCourse(courseId, resolvedModuleId, search, type, status, includeArchived, difficulty, page, size);
    }

    @Transactional(readOnly = true)
    public QuestionModel.Response getInCourse(UUID courseId, UUID questionId) {
        courseAccessService.requireReadableCourse(courseId);
        Question question = findQuestion(questionId);
        assertQuestionBelongsToCourse(question, courseId);

        return toResponse(question);
    }

    /** Lấy câu hỏi trong đúng module để URL của module khác không thể truy cập chéo dữ liệu. */
    @Transactional(readOnly = true)
    public QuestionModel.Response getInCourse(UUID courseId, UUID moduleId, UUID questionId) {
        courseAccessService.requireReadableCourse(courseId);
        UUID resolvedModuleId = validateRequiredCourseModuleId(courseId, moduleId);
        Question question = findQuestion(questionId);
        assertQuestionBelongsToCourseModule(question, courseId, resolvedModuleId);
        return toResponse(question);
    }

    @Transactional
    public QuestionModel.Response createForCourse(UUID courseId, QuestionModel.CreateRequest request) {
        return createForCourse(courseId, null, request);
    }

    /** Tạo câu hỏi course-wide; chỉ API tương thích cũ mới gắn module lấy từ path. */
    @Transactional
    public QuestionModel.Response createForCourse(UUID courseId, UUID moduleId, QuestionModel.CreateRequest request) {
        courseAccessService.requireUpdatableCourse(courseId);
        QuestionType questionType = parseSupportedQuestionType(request.questionType());
        validateAnswers(questionType, request.answers());
        UserAccount actor = currentUserService.requireAuthenticatedUser();
        String questionText = normalizeRequired(request.questionText(), "Question text is required");
        if (questionRepository.existsActiveDuplicateInCourse(courseId, questionText, null)) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "A question with the same text already exists in this course");
        }

        Question question = new Question();
        question.setCourseId(courseId);
        question.setModuleId(moduleId == null ? null : validateRequiredCourseModuleId(courseId, moduleId));
        question.setQuestionText(questionText);
        question.setQuestionType(questionType);
        question.setBloomLevel(parseBloomLevel(request.bloomLevel()));
        question.setDifficulty(request.difficulty());
        question.setExplanation(normalizeNullable(request.explanation()));
        question.setIsAiGenerated(false);
        question.setStatus(parseQuestionStatus(request.status(), QuestionStatus.DRAFT));
        question.setCreatedBy(actor.getId());

        Question saved = questionRepository.save(question);
        replaceAnswers(saved.getId(), request.answers());
        courseAuditRecorder.recordMaster(
                actor,
                AuditAction.QUESTION_BANK_CREATED,
                "QUESTION",
                saved.getId(),
                courseId,
                "Question created");

        return toResponse(saved);
    }

    @Transactional
    public QuestionModel.Response updateInCourse(UUID courseId, UUID questionId, QuestionModel.UpdateRequest request) {
        return updateInCourse(courseId, null, questionId, request);
    }

    /** Tạo câu hỏi từ DTO công khai không chứa module, rồi dùng module từ path. */
    @Transactional
    public QuestionModel.Response createForCourse(UUID courseId, UUID moduleId, QuestionModel.ModuleCreateRequest request) {
        return createForCourse(courseId, moduleId, request.toCreateRequest());
    }

    /** Cập nhật course-wide; API module cũ vẫn kiểm tra module khi path có truyền moduleId. */
    @Transactional
    public QuestionModel.Response updateInCourse(UUID courseId, UUID moduleId, UUID questionId, QuestionModel.UpdateRequest request) {
        courseAccessService.requireUpdatableCourse(courseId);
        Question question = findQuestion(questionId);
        if (moduleId == null) {
            assertQuestionBelongsToCourse(question, courseId);
        } else {
            UUID resolvedModuleId = validateRequiredCourseModuleId(courseId, moduleId);
            assertQuestionBelongsToCourseModule(question, courseId, resolvedModuleId);
        }
        if (question.getStatus() == QuestionStatus.ARCHIVED) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "Cannot update an archived question");
        }
        requireQuestionNotUsedInAttempt(questionId);
        QuestionType questionType = parseSupportedQuestionType(request.questionType());
        validateAnswers(questionType, request.answers());
        String questionText = normalizeRequired(request.questionText(), "Question text is required");
        if (questionRepository.existsActiveDuplicateInCourse(courseId, questionText, question.getId())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "A question with the same text already exists in this course");
        }

        question.setQuestionText(questionText);
        question.setQuestionType(questionType);
        question.setBloomLevel(parseBloomLevel(request.bloomLevel()));
        question.setDifficulty(request.difficulty());
        question.setExplanation(normalizeNullable(request.explanation()));
        question.setStatus(parseQuestionStatus(request.status(), question.getStatus()));

        Question saved = questionRepository.save(question);
        replaceAnswers(saved.getId(), request.answers());
        courseAuditRecorder.recordMaster(
                currentUserService.requireAuthenticatedUser(),
                AuditAction.QUESTION_BANK_UPDATED,
                "QUESTION",
                saved.getId(),
                courseId,
                "Question updated");

        return toResponse(saved);
    }

    @Transactional
    public void archiveInCourse(UUID courseId, UUID questionId) {
        courseAccessService.requireUpdatableCourse(courseId);
        Question question = findQuestion(questionId);
        assertQuestionBelongsToCourse(question, courseId);
        if (question.getStatus() == QuestionStatus.ARCHIVED) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "Question is already archived");
        }
        requireQuestionNotUsedInAttempt(questionId);
        question.setStatus(QuestionStatus.ARCHIVED);
        questionRepository.save(question);
        courseAuditRecorder.recordMaster(
                currentUserService.requireAuthenticatedUser(),
                AuditAction.QUESTION_BANK_ARCHIVED,
                "QUESTION",
                questionId,
                courseId,
                "Question archived");
    }

    /** Khôi phục câu hỏi đã lưu trữ về draft để nội dung phải được duyệt lại trước khi sử dụng. */
    @Transactional
    public void restoreInCourse(UUID courseId, UUID questionId) {
        courseAccessService.requireUpdatableCourse(courseId);
        Question question = findQuestion(questionId);
        assertQuestionBelongsToCourse(question, courseId);
        if (question.getStatus() != QuestionStatus.ARCHIVED) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "Only archived questions can be restored");
        }
        if (questionRepository.existsActiveDuplicateInCourse(courseId, question.getQuestionText(), question.getId())) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_RULE_VIOLATION,
                    "An active question with the same text already exists in this course");
        }
        question.setStatus(QuestionStatus.DRAFT);
        questionRepository.save(question);
    }

    /** Cập nhật câu hỏi từ DTO công khai không chứa field module. */
    @Transactional
    public QuestionModel.Response updateInCourse(UUID courseId, UUID moduleId, UUID questionId, QuestionModel.ModuleUpdateRequest request) {
        return updateInCourse(courseId, moduleId, questionId, request.toUpdateRequest());
    }

    /** Lưu trữ câu hỏi chỉ khi câu hỏi thực sự thuộc module trên URL. */
    @Transactional
    public void archiveInCourse(UUID courseId, UUID moduleId, UUID questionId) {
        courseAccessService.requireUpdatableCourse(courseId);
        UUID resolvedModuleId = validateRequiredCourseModuleId(courseId, moduleId);
        Question question = findQuestion(questionId);
        assertQuestionBelongsToCourseModule(question, courseId, resolvedModuleId);
        if (question.getStatus() == QuestionStatus.ARCHIVED) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "Question is already archived");
        }
        requireQuestionNotUsedInAttempt(questionId);
        question.setStatus(QuestionStatus.ARCHIVED);
        questionRepository.save(question);
        courseAuditRecorder.recordMaster(
                currentUserService.requireAuthenticatedUser(),
                AuditAction.QUESTION_BANK_ARCHIVED,
                "QUESTION",
                questionId,
                courseId,
                "Question archived");
    }

    /**
     * Trả ID module chuẩn thuộc course; chấp nhận thêm ID curriculum section để tương thích URL cũ.
     * Giá trị không thuộc course hoặc trỏ tới module đã ngừng hoạt động đều bị từ chối.
     */
    private UUID validateRequiredCourseModuleId(UUID courseId, UUID moduleId) {
        if (moduleId == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Question module is required");
        }
        boolean exists = courseModuleRepository.existsByIdAndCourseIdAndSystemFalseAndStatus(
                moduleId,
                courseId,
                CourseModule.STATUS_ACTIVE
        );
        if (exists) {
            return moduleId;
        }
        return courseModuleRepository.findActiveModuleIdByCourseIdAndSectionId(
                        courseId,
                        moduleId,
                        CourseModule.STATUS_ACTIVE
                )
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.INVALID_REQUEST,
                        "Question module must belong to the selected course"
                ));
    }

    @Transactional
    public QuestionImportDtos.ImportBatchResponse importBatchForCourse(UUID courseId, QuestionImportDtos.ImportBatchRequest request) {
        courseAccessService.requireUpdatableCourse(courseId);
        List<Question> savedQuestions = importReviewedRowsForCourse(courseId, null, request.rows(), false, null, normalizeImportMediaSource(request.importSource()));
        List<UUID> createdIds = savedQuestions.stream().map(Question::getId).toList();
        return new QuestionImportDtos.ImportBatchResponse(request.rows().size(), createdIds.size(), createdIds, List.of());
    }

    /** Import toàn bộ batch vào module trên URL và bỏ qua mọi module do client gửi. */
    @Transactional
    public QuestionImportDtos.ImportBatchResponse importBatchForCourse(UUID courseId, UUID moduleId, QuestionImportDtos.ImportBatchRequest request) {
        courseAccessService.requireUpdatableCourse(courseId);
        UUID resolvedModuleId = validateRequiredCourseModuleId(courseId, moduleId);
        List<Question> savedQuestions = importReviewedRowsForCourse(courseId, resolvedModuleId, request.rows(), false, null, normalizeImportMediaSource(request.importSource()));
        List<UUID> createdIds = savedQuestions.stream().map(Question::getId).toList();
        return new QuestionImportDtos.ImportBatchResponse(request.rows().size(), createdIds.size(), createdIds, List.of());
    }

    /** Import DTO module-scoped và gán module path cho mọi row ở phía server. */
    @Transactional
    public QuestionImportDtos.ImportBatchResponse importBatchForCourse(UUID courseId, UUID moduleId, QuestionImportDtos.ModuleImportBatchRequest request) {
        List<QuestionImportDtos.ImportRow> rows = request.rows().stream()
                .map(QuestionImportDtos.ModuleImportRow::toImportRow)
                .toList();
        return importBatchForCourse(
                courseId,
                moduleId,
                new QuestionImportDtos.ImportBatchRequest(rows, request.importSource())
        );
    }

    @Transactional
    public List<Question> importReviewedRowsForCourse(UUID courseId, List<QuestionImportDtos.ImportRow> rows, boolean aiGenerated, String importSource) {
        return importReviewedRowsForCourse(courseId, null, rows, aiGenerated, importSource, importSource == null ? null : importSource);
    }

    private List<Question> importReviewedRowsForCourse(UUID courseId, UUID fixedModuleId, List<QuestionImportDtos.ImportRow> rows, boolean aiGenerated, String importSource, String mediaImportSource) {
        UserAccount actor = currentUserService.requireAuthenticatedUser();
        if (rows == null || rows.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "At least one question row is required");
        }

        List<QuestionImportDtos.ImportRowError> errors = new ArrayList<>();
        List<QuestionImportDtos.ImportRow> validatedRows = new ArrayList<>();
        for (QuestionImportDtos.ImportRow row : rows) {
            List<String> rowErrors = validateImportRowForCourse(courseId, fixedModuleId, row);
            if (!rowErrors.isEmpty()) {
                errors.add(new QuestionImportDtos.ImportRowError(row.rowNumber(), rowErrors));
            } else {
                validatedRows.add(row);
            }
        }

        for (QuestionImportDtos.ImportRow row : validatedRows) {
            String normalizedText = normalizeRequired(row.questionText(), "Question text is required");
            if (questionRepository.existsActiveDuplicateInCourse(courseId, normalizedText, null)) {
                errors.add(new QuestionImportDtos.ImportRowError(row.rowNumber(), List.of("A question with the same text already exists in this course")));
            }
        }

        if (!errors.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, buildImportErrorSummary(errors));
        }

        List<Question> savedQuestions = new ArrayList<>();
        for (QuestionImportDtos.ImportRow row : validatedRows) {
            Question savedQuestion = persistImportedQuestionForCourse(courseId, fixedModuleId, row, actor, aiGenerated, importSource);
            try {
                questionMediaImportService.attachImportedMedia(
                        savedQuestion,
                        row.imageFiles(),
                        row.audioFiles(),
                        mediaImportSource
                );
            } catch (BusinessException exception) {
                throw new BusinessException(
                        exception.errorCode(),
                        "Row " + row.rowNumber() + " media import failed: " + exception.getMessage()
                );
            }
            savedQuestions.add(savedQuestion);
        }
        return savedQuestions;
    }

    private Question persistImportedQuestionForCourse(UUID courseId, UUID fixedModuleId, QuestionImportDtos.ImportRow row, UserAccount actor, boolean aiGenerated, String importSource) {
        QuestionType questionType = parseSupportedQuestionType(row.questionType());
        Question question = new Question();
        question.setCourseId(courseId);
        question.setModuleId(fixedModuleId == null
                ? null
                : validateRequiredCourseModuleId(courseId, fixedModuleId));
        question.setQuestionText(normalizeRequired(row.questionText(), "Question text is required"));
        question.setQuestionType(questionType);
        question.setBloomLevel(parseBloomLevel(row.bloomLevel()));
        question.setDifficulty(row.difficulty());
        question.setExplanation(normalizeNullable(row.explanation()));
        question.setIsAiGenerated(aiGenerated);
        question.setImportSource(normalizeNullable(importSource));
        question.setStatus(QuestionStatus.DRAFT);
        question.setCreatedBy(actor.getId());
        Question saved = questionRepository.save(question);

        List<QuestionModel.AnswerRequest> answers = buildAnswersForImport(row, questionType);
        replaceAnswers(saved.getId(), answers);
        return saved;
    }

    private List<QuestionModel.AnswerRequest> buildAnswersForImport(QuestionImportDtos.ImportRow row, QuestionType questionType) {
        List<String> options = row.options().stream()
                .map(option -> normalizeRequired(option, "Answer text is required"))
                .toList();
        Set<Integer> correctIndexes = resolveCorrectAnswerIndexes(questionType, options, row.correctAnswer());
        List<QuestionModel.AnswerRequest> answers = new ArrayList<>();
        for (int index = 0; index < options.size(); index += 1) {
            boolean correct = correctIndexes.contains(index);
            answers.add(new QuestionModel.AnswerRequest(
                    null,
                    null,
                    options.get(index),
                    correct,
                    correct,
                    index + 1,
                    index + 1
            ));
        }
        return answers;
    }

    private Set<Integer> resolveCorrectAnswerIndexes(QuestionType questionType, List<String> options, String correctAnswer) {
        String normalized = correctAnswer == null ? "" : correctAnswer.trim();
        if (questionType == QuestionType.TRUE_FALSE) {
            boolean isTrue = "true".equalsIgnoreCase(normalized);
            boolean isFalse = "false".equalsIgnoreCase(normalized);
            if (!isTrue && !isFalse) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST, "Correct answer for true/false must be True or False");
            }
            for (int index = 0; index < options.size(); index += 1) {
                String text = options.get(index).trim().toLowerCase(Locale.ROOT);
                if (isTrue && "true".equals(text)) return Set.of(index);
                if (isFalse && "false".equals(text)) return Set.of(index);
            }
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "True/false options must contain True and False answers");
        }
        List<String> letters = List.of(normalized.toUpperCase(Locale.ROOT).split("[,;\\s]+")).stream()
                .map(String::trim)
                .filter(letter -> !letter.isBlank())
                .toList();
        if (questionType == QuestionType.SINGLE_CHOICE && letters.size() != 1) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Single choice correct answer must be one letter A-F");
        }
        if (questionType == QuestionType.MULTIPLE_CHOICE && letters.size() < 2) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Multiple choice correct answer must include at least two letters");
        }
        Set<Integer> indexes = new java.util.LinkedHashSet<>();
        for (String item : letters) {
            if (item.length() != 1) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST, "Correct answer must use letters A, B, C, D, E, or F");
            }
            char letter = item.charAt(0);
            if (letter < 'A' || letter > 'F') {
                throw new BusinessException(ErrorCode.INVALID_REQUEST, "Correct answer must use letters A, B, C, D, E, or F");
            }
            int index = letter - 'A';
            if (index >= options.size()) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST, "Correct answer refers to an option that was not provided");
            }
            if (!indexes.add(index)) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST, "Correct answer contains duplicate letters");
            }
        }
        return indexes;
    }

    private List<String> validateImportRowForCourse(UUID courseId, UUID fixedModuleId, QuestionImportDtos.ImportRow row) {
        List<String> rowErrors = validateImportRowContent(row);

        if (fixedModuleId != null) {
            boolean moduleExists = courseModuleRepository.existsByIdAndCourseIdAndSystemFalseAndStatus(
                    fixedModuleId,
                    courseId,
                    CourseModule.STATUS_ACTIVE
            );
            if (!moduleExists) {
                rowErrors.add("Question module must belong to the selected course");
            }
        }

        rowErrors.addAll(questionMediaImportService.validateMediaReferences(row.imageFiles(), row.audioFiles()));

        return rowErrors;
    }

    private List<String> validateImportRowContent(QuestionImportDtos.ImportRow row) {
        List<String> rowErrors = new ArrayList<>();
        if (row.questionText() == null || row.questionText().isBlank()) {
            rowErrors.add("Question text is required");
        } else if (row.questionText().length() > 10000) {
            rowErrors.add("Question text must not exceed 10000 characters");
        }

        QuestionType type = null;
        String rawType = row.questionType();
        if (rawType == null || rawType.isBlank()) {
            rowErrors.add("Question type is required");
        } else {
            String normalizedType = rawType.trim().replace('-', '_').toUpperCase(Locale.ROOT);
            try {
                type = QuestionType.valueOf(normalizedType);
            } catch (IllegalArgumentException exception) {
                rowErrors.add(SUPPORTED_QUESTION_TYPE_MESSAGE);
            }
            if (type != null && !SUPPORTED_QUESTION_TYPES.contains(type)) {
                rowErrors.add(SUPPORTED_QUESTION_TYPE_MESSAGE);
                type = null;
            }
        }

        List<String> options = row.options();
        if (options == null || options.size() < 2) {
            rowErrors.add("At least two answers are required");
        } else if (options.size() > 6) {
            rowErrors.add("Choice questions support 2 to 6 answers");
        } else {
            for (int index = 0; index < options.size(); index += 1) {
                String option = options.get(index);
                if (option == null || option.isBlank()) {
                    rowErrors.add("Answer " + (char) ('A' + index) + " is required");
                } else if (option.length() > 4000) {
                    rowErrors.add("Answer " + (char) ('A' + index) + " must not exceed 4000 characters");
                }
            }
        }

        if (options != null && type == QuestionType.TRUE_FALSE && options.size() != 2) {
            rowErrors.add("True/false questions must have exactly two answers");
        }

        if (options != null && type == QuestionType.TRUE_FALSE) {
            boolean hasTrue = false;
            boolean hasFalse = false;
            for (String option : options) {
                if (option == null) continue;
                String text = option.trim().toLowerCase(Locale.ROOT);
                if ("true".equals(text)) hasTrue = true;
                if ("false".equals(text)) hasFalse = true;
            }
            if (!hasTrue || !hasFalse) {
                rowErrors.add("True/false answers must be True and False");
            }
        }

        String correctAnswer = row.correctAnswer();
        if (correctAnswer == null || correctAnswer.isBlank()) {
            rowErrors.add("Correct answer is required");
        } else if (type == QuestionType.TRUE_FALSE) {
            String normalized = correctAnswer.trim();
            if (!"true".equalsIgnoreCase(normalized) && !"false".equalsIgnoreCase(normalized)) {
                rowErrors.add("Correct answer for true/false must be True or False");
            }
        } else if (type == QuestionType.SINGLE_CHOICE || type == QuestionType.MULTIPLE_CHOICE) {
            List<String> letters = List.of(correctAnswer.trim().toUpperCase(Locale.ROOT).split("[,;\\s]+")).stream()
                    .map(String::trim)
                    .filter(letter -> !letter.isBlank())
                    .toList();
            if (type == QuestionType.SINGLE_CHOICE && letters.size() != 1) {
                rowErrors.add("Single choice correct answer must be one letter A-F");
            } else if (type == QuestionType.MULTIPLE_CHOICE && letters.size() < 2) {
                rowErrors.add("Multiple choice correct answer must include at least two letters, such as A,C");
            } else {
                Set<String> uniqueLetters = new java.util.LinkedHashSet<>();
                for (String letterValue : letters) {
                    if (letterValue.length() != 1) {
                        rowErrors.add("Correct answer must use letters A, B, C, D, E, or F");
                        continue;
                    }
                    char letter = letterValue.charAt(0);
                    if (letter < 'A' || letter > 'F') {
                        rowErrors.add("Correct answer must use letters A, B, C, D, E, or F");
                    } else if (options != null && (letter - 'A') >= options.size()) {
                        rowErrors.add("Correct answer refers to an option that was not provided");
                    } else if (!uniqueLetters.add(letterValue)) {
                        rowErrors.add("Correct answer contains duplicate letters");
                    }
                }
            }
        }

        if (row.difficulty() != null && (row.difficulty() < 1 || row.difficulty() > 5)) {
            rowErrors.add("Difficulty must be between 1 and 5");
        }

        if (row.bloomLevel() != null && !row.bloomLevel().isBlank()) {
            String normalized = row.bloomLevel().trim().replace('-', '_').toUpperCase(Locale.ROOT);
            try {
                BloomLevel.valueOf(normalized);
            } catch (IllegalArgumentException exception) {
                rowErrors.add("Bloom level is invalid");
            }
        }

        if (row.explanation() != null && row.explanation().length() > 10000) {
            rowErrors.add("Explanation must not exceed 10000 characters");
        }

        return rowErrors;
    }

    /** Chuan hoa nguon media import hien hanh, cac nguon cu khong con ho tro se ve Excel. */
    private String normalizeImportMediaSource(String value) {
        String normalized = normalizeNullable(value);
        if (normalized == null) return "excel_import";
        String apiValue = normalized.trim().replace('-', '_').toLowerCase(Locale.ROOT);
        return switch (apiValue) {
            case "excel_import" -> apiValue;
            default -> "excel_import";
        };
    }
    private String buildImportErrorSummary(List<QuestionImportDtos.ImportRowError> errors) {
        StringBuilder builder = new StringBuilder("Import validation failed:");
        int limit = Math.min(errors.size(), 5);
        for (int index = 0; index < limit; index += 1) {
            QuestionImportDtos.ImportRowError error = errors.get(index);
            builder.append(" Row ").append(error.rowNumber()).append(": ")
                    .append(String.join("; ", error.errors())).append('.');
        }
        if (errors.size() > limit) {
            builder.append(" And ").append(errors.size() - limit).append(" more rows with errors.");
        }
        return builder.toString();
    }

    private Question findQuestion(UUID questionId) {
        return questionRepository.findById(questionId).orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Question not found"));
    }

    private void assertQuestionBelongsToCourse(Question question, UUID courseId) {
        if (!courseId.equals(question.getCourseId())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Question not found");
        }
    }

    /** Che giấu câu hỏi khi course hoặc module trên URL không khớp bản ghi. */
    private void assertQuestionBelongsToCourseModule(Question question, UUID courseId, UUID moduleId) {
        if (!courseId.equals(question.getCourseId()) || !moduleId.equals(question.getModuleId())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Question not found");
        }
    }

    /** Đồng bộ đáp án tại chỗ để giữ ID đã được lịch sử làm quiz tham chiếu. */
    private void replaceAnswers(UUID questionId, List<QuestionModel.AnswerRequest> answers) {
        List<QuestionAnswer> existingAnswers =
                answerRepository.findByQuestionIdOrderByOrderIndexAsc(questionId);
        Map<UUID, QuestionAnswer> existingById = existingAnswers.stream()
                .collect(Collectors.toMap(QuestionAnswer::getId, answer -> answer));
        Set<UUID> retainedAnswerIds = new java.util.HashSet<>();
        List<QuestionAnswer> synchronizedAnswers = new ArrayList<>();

        for (int index = 0; index < answers.size(); index += 1) {
            QuestionModel.AnswerRequest request = answers.get(index);
            UUID requestedId = request.answerId() != null ? request.answerId() : request.id();
            QuestionAnswer answer = requestedId == null
                    ? findLegacyAnswerByPosition(existingAnswers, retainedAnswerIds, index)
                    : existingById.get(requestedId);
            if (requestedId != null && answer == null) {
                throw new BusinessException(
                        ErrorCode.INVALID_REQUEST,
                        "Answer does not belong to this question");
            }
            if (answer == null) {
                answer = new QuestionAnswer();
            } else if (!retainedAnswerIds.add(answer.getId())) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST, "Answer is duplicated");
            }
            answer.setQuestionId(questionId);
            answer.setAnswerText(normalizeRequired(request.answerText(), "Answer text is required"));
            answer.setIsCorrect(request.correctValue());
            answer.setOrderIndex(request.resolvedOrder() == null ? index + 1 : request.resolvedOrder());
            synchronizedAnswers.add(answer);
        }

        List<QuestionAnswer> removedAnswers = existingAnswers.stream()
                .filter(answer -> !retainedAnswerIds.contains(answer.getId()))
                .toList();
        if (removedAnswers.stream()
                .anyMatch(answer -> answerRepository.existsStudentSelectionById(answer.getId()))) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_RULE_VIOLATION,
                    "An answer used in quiz attempts cannot be removed");
        }

        for (QuestionAnswer answer : synchronizedAnswers) {
            answerRepository.save(answer);
        }
        if (!removedAnswers.isEmpty()) {
            answerRepository.deleteAll(removedAnswers);
        }
    }

    /** Ghép client cũ không gửi answerId với đáp án cùng vị trí để tránh thay ID. */
    private QuestionAnswer findLegacyAnswerByPosition(
            List<QuestionAnswer> existingAnswers,
            Set<UUID> retainedAnswerIds,
            int index) {
        if (index >= existingAnswers.size()) {
            return null;
        }
        QuestionAnswer candidate = existingAnswers.get(index);
        return retainedAnswerIds.contains(candidate.getId()) ? null : candidate;
    }

    private void requireQuestionNotUsedInAttempt(UUID questionId) {
        if (studentTestAnswerRepository.existsByQuestionId(questionId)) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Cannot update or delete a question that already has trainee answers");
        }
    }

    private void validateAnswers(QuestionType questionType, List<QuestionModel.AnswerRequest> answers) {
        if (answers == null || answers.size() < MIN_ANSWERS) throw new BusinessException(ErrorCode.INVALID_REQUEST, "At least two answers are required");
        long correctCount = answers.stream().filter(QuestionModel.AnswerRequest::correctValue).count();
        for (QuestionModel.AnswerRequest answer : answers) normalizeRequired(answer.answerText(), "Answer text is required");
        if ((questionType == QuestionType.SINGLE_CHOICE || questionType == QuestionType.MULTIPLE_CHOICE) && answers.size() > MAX_MCQ_ANSWERS) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Choice questions support 2 to 6 answers");
        }
        if ((questionType == QuestionType.SINGLE_CHOICE || questionType == QuestionType.TRUE_FALSE) && correctCount != 1) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Exactly one correct answer is required");
        }
        if (questionType == QuestionType.MULTIPLE_CHOICE && correctCount < 2) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Multiple choice requires at least two correct answers");
        }
        if (questionType == QuestionType.TRUE_FALSE) validateTrueFalseAnswers(answers);
    }

    private void validateTrueFalseAnswers(List<QuestionModel.AnswerRequest> answers) {
        if (answers.size() != 2) throw new BusinessException(ErrorCode.INVALID_REQUEST, "True/false questions must have exactly two answers");
        boolean hasTrue = false;
        boolean hasFalse = false;
        for (QuestionModel.AnswerRequest answer : answers) {
            String text = normalizeRequired(answer.answerText(), "Answer text is required").toLowerCase(Locale.ROOT);
            hasTrue = hasTrue || "true".equals(text);
            hasFalse = hasFalse || "false".equals(text);
        }
        if (!hasTrue || !hasFalse) throw new BusinessException(ErrorCode.INVALID_REQUEST, "True/false answers must be True and False");
    }

    private QuestionModel.Response toResponse(Question question) {
        List<QuestionAnswer> answerEntities = answerRepository.findByQuestionIdOrderByOrderIndexAsc(question.getId());
        Map<UUID, List<QuestionAnswerMediaAttachment>> mediaByAnswer = answerEntities.isEmpty()
                ? Collections.emptyMap()
                : answerMediaRepository.findByAnswerIdIn(answerEntities.stream().map(QuestionAnswer::getId).toList()).stream()
                        .collect(Collectors.groupingBy(QuestionAnswerMediaAttachment::getAnswerId));
        List<QuestionModel.AnswerResponse> answers = answerEntities.stream().map(answer -> {
            List<QuestionAnswerMediaResponse> answerMedia = (mediaByAnswer.getOrDefault(answer.getId(), List.of())).stream()
                    .map(this::toAnswerMediaResponse)
                    .toList();
            return new QuestionModel.AnswerResponse(
                    answer.getId(),
                    answer.getId(),
                    answer.getAnswerText(),
                    Boolean.TRUE.equals(answer.getIsCorrect()),
                    Boolean.TRUE.equals(answer.getIsCorrect()),
                    answer.getOrderIndex() == null ? 0 : answer.getOrderIndex(),
                    answer.getOrderIndex() == null ? 0 : answer.getOrderIndex(),
                    answerMedia
            );
        }).toList();
        List<QuestionMediaAttachment> imageAttachments = mediaAttachmentRepository.findByQuestionIdAndMediaTypeOrderByDisplayOrderAsc(question.getId(), QuestionMediaType.IMAGE);
        List<QuestionMediaAttachment> audioAttachments = mediaAttachmentRepository.findByQuestionIdAndMediaTypeOrderByDisplayOrderAsc(question.getId(), QuestionMediaType.AUDIO);
        List<QuestionMediaAttachment> videoAttachments = mediaAttachmentRepository.findByQuestionIdAndMediaTypeOrderByDisplayOrderAsc(question.getId(), QuestionMediaType.VIDEO);
        List<QuestionMediaAttachmentResponse> mediaAttachments = new ArrayList<>();
        mediaAttachments.addAll(imageAttachments.stream().map(this::toMediaResponse).toList());
        mediaAttachments.addAll(audioAttachments.stream().map(this::toMediaResponse).toList());
        mediaAttachments.addAll(videoAttachments.stream().map(this::toMediaResponse).toList());
        String imageUrl = imageAttachments.isEmpty() ? null : imageAttachments.get(0).getMediaUrl();
        String audioUrl = audioAttachments.isEmpty() ? null : audioAttachments.get(0).getMediaUrl();
        return new QuestionModel.Response(question.getId(), question.getId(), question.getCourseId(), question.getModuleId(), question.getQuestionText(), toApiValue(question.getQuestionType()), toApiValue(question.getBloomLevel()), question.getDifficulty(), question.getExplanation(), imageUrl, audioUrl, mediaAttachments, Boolean.TRUE.equals(question.getIsAiGenerated()), question.getImportSource(), toApiValue(question.getStatus()), answers.size(), answers, question.getCreatedBy(), question.getReviewedBy(), question.getReviewedAt(), question.getCreatedAt(), question.getUpdatedAt());
    }

    private QuestionMediaAttachmentResponse toMediaResponse(QuestionMediaAttachment attachment) {
        return new QuestionMediaAttachmentResponse(
                attachment.getId(),
                attachment.getId(),
                attachment.getQuestionId(),
                toApiValue(attachment.getMediaType()),
                attachment.getMediaUrl(),
                attachment.getObjectKey(),
                attachment.getBucket(),
                attachment.getContentType(),
                attachment.getFileSize() == null ? 0 : attachment.getFileSize(),
                attachment.getOriginalFileName(),
                attachment.getDisplayOrder() == null ? 0 : attachment.getDisplayOrder(),
                attachment.getImportSource(),
                attachment.getCreatedAt(),
                attachment.getUpdatedAt()
        );
    }

    private QuestionAnswerMediaResponse toAnswerMediaResponse(QuestionAnswerMediaAttachment attachment) {
        return new QuestionAnswerMediaResponse(
                attachment.getId(),
                attachment.getAnswerId(),
                toApiValue(attachment.getMediaType()),
                attachment.getMediaUrl(),
                attachment.getObjectKey(),
                attachment.getBucket(),
                attachment.getContentType(),
                attachment.getFileSize() == null ? 0 : attachment.getFileSize(),
                attachment.getOriginalFileName(),
                attachment.getDisplayOrder() == null ? 0 : attachment.getDisplayOrder(),
                attachment.getImportSource(),
                attachment.getCreatedAt(),
                attachment.getUpdatedAt()
        );
    }
    private QuestionType parseSupportedQuestionType(String value) {
        QuestionType type = parseEnum(value, QuestionType.class, SUPPORTED_QUESTION_TYPE_MESSAGE);
        if (!SUPPORTED_QUESTION_TYPES.contains(type)) throw new BusinessException(ErrorCode.INVALID_REQUEST, SUPPORTED_QUESTION_TYPE_MESSAGE);
        return type;
    }

    private QuestionStatus parseQuestionStatus(String value, QuestionStatus defaultStatus) {
        if (value == null || value.isBlank()) return defaultStatus;
        return parseEnum(value, QuestionStatus.class, "Question status is invalid");
    }

    private BloomLevel parseBloomLevel(String value) {
        if (value == null || value.isBlank()) return null;
        return parseEnum(value, BloomLevel.class, "Bloom level is invalid");
    }

    private <T extends Enum<T>> T parseEnum(String value, Class<T> enumClass, String message) {
        if (value == null || value.isBlank()) throw new BusinessException(ErrorCode.INVALID_REQUEST, message);
        String normalized = value.trim().replace('-', '_').toUpperCase(Locale.ROOT);
        try {
            return Enum.valueOf(enumClass, normalized);
        }
        catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, message);
        }
    }

    private String toApiValue(Enum<?> value) {
        return value == null ? null : value.name().toLowerCase(Locale.ROOT);
    }

    private String normalizeRequired(String value, String message) {
        String normalized = normalizeNullable(value);
        if (normalized == null) throw new BusinessException(ErrorCode.INVALID_REQUEST, message);
        return normalized;
    }

    private String normalizeNullable(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
