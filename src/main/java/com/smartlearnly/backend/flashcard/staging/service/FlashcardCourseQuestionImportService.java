package com.smartlearnly.backend.flashcard.staging.service;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.course.access.service.CourseAccessService;
import com.smartlearnly.backend.course.entity.Course;
import com.smartlearnly.backend.curriculum.entity.CurriculumLesson;
import com.smartlearnly.backend.curriculum.entity.CurriculumScope;
import com.smartlearnly.backend.curriculum.repository.CurriculumLessonRepository;
import com.smartlearnly.backend.curriculum.service.TrainerClassCurriculumService;
import com.smartlearnly.backend.flashcard.entity.FlashcardCard;
import com.smartlearnly.backend.flashcard.entity.FlashcardSet;
import com.smartlearnly.backend.flashcard.repository.FlashcardCardRepository;
import com.smartlearnly.backend.flashcard.repository.FlashcardSetRepository;
import com.smartlearnly.backend.flashcard.staging.dto.AdminFlashcardStagingDtos.ImportCourseQuestionsRequest;
import com.smartlearnly.backend.flashcard.staging.dto.AdminFlashcardStagingDtos.SourceQuestionAnswerResponse;
import com.smartlearnly.backend.flashcard.staging.dto.AdminFlashcardStagingDtos.SourceQuestionResponse;
import com.smartlearnly.backend.flashcard.staging.dto.AdminFlashcardStagingDtos.StagingBatchResponse;
import com.smartlearnly.backend.flashcard.staging.dto.AdminFlashcardStagingDtos.StagingCardResponse;
import com.smartlearnly.backend.flashcard.staging.dto.AdminFlashcardStagingDtos.TemporaryFlashcardCandidateBatchResponse;
import com.smartlearnly.backend.flashcard.staging.dto.AdminFlashcardStagingDtos.TemporaryFlashcardCandidateResponse;
import com.smartlearnly.backend.flashcard.staging.entity.FlashcardStagingBatch;
import com.smartlearnly.backend.flashcard.staging.entity.FlashcardStagingCard;
import com.smartlearnly.backend.flashcard.staging.repository.FlashcardStagingBatchRepository;
import com.smartlearnly.backend.flashcard.staging.repository.FlashcardStagingCardRepository;
import com.smartlearnly.backend.learning.lesson.entity.Lesson;
import com.smartlearnly.backend.learning.lesson.entity.LessonType;
import com.smartlearnly.backend.question.entity.Question;
import com.smartlearnly.backend.question.entity.QuestionAnswer;
import com.smartlearnly.backend.question.entity.QuestionStatus;
import com.smartlearnly.backend.question.entity.QuestionType;
import com.smartlearnly.backend.question.repository.QuestionAnswerRepository;
import com.smartlearnly.backend.question.repository.QuestionRepository;
import com.smartlearnly.backend.user.entity.UserAccount;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

/**
 * Quản lý nguồn câu hỏi khóa học dùng để tạo flashcard staging và bản xem trước.
 */
@Service
@RequiredArgsConstructor
public class FlashcardCourseQuestionImportService {
    private static final String SOURCE_TYPE_COURSE_QUESTIONS = "COURSE_QUESTIONS";
    private static final String STATUS_DRAFT = "draft";
    private static final String STATUS_APPROVED = "approved";
    private static final Set<String> IMPORTED_SOURCE_STATUSES = Set.of(STATUS_DRAFT, STATUS_APPROVED);
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[a-zA-Z][^>]*>");

    private final FlashcardSetRepository flashcardSetRepository;
    private final FlashcardCardRepository flashcardCardRepository;
    private final FlashcardStagingBatchRepository stagingBatchRepository;
    private final FlashcardStagingCardRepository stagingCardRepository;
    private final QuestionRepository questionRepository;
    private final QuestionAnswerRepository questionAnswerRepository;
    private final CurrentUserService currentUserService;
    @Autowired
    private CurriculumLessonRepository curriculumLessonRepository;
    @Autowired
    private CourseAccessService courseAccessService;
    @Autowired
    private TrainerClassCurriculumService trainerClassCurriculumService;

    /** Liệt kê câu hỏi cùng khóa học và đánh dấu câu nào đã được nhập vào set hiện tại. */
    @Transactional(readOnly = true)
    public List<SourceQuestionResponse> listSourceQuestions(
            UUID setId,
            UUID moduleId,
            String keyword,
            Short difficulty,
            String status
    ) {
        SetContext context = resolveSetContext(setId);
        QuestionStatus parsedStatus = parseQuestionStatus(status);
        List<Question> questions = questionRepository.searchForAdmin(
                context.course().getId(),
                moduleId,
                keyword,
                null,
                parsedStatus == null ? null : toApiValue(parsedStatus),
                true,
                difficulty,
                Pageable.unpaged()
        ).stream()
                .filter(question -> context.course().getId().equals(question.getCourseId()))
                .toList();
        Map<UUID, List<QuestionAnswer>> answersByQuestionId = answersByQuestionId(questions);
        Set<UUID> importedQuestionIds = importedSourceQuestionIds(
                setId,
                questions.stream().map(Question::getId).toList()
        );
        return questions.stream()
                .map(question -> toSourceQuestionResponse(
                        question,
                        "Course questions",
                        answersByQuestionId.getOrDefault(question.getId(), List.of()),
                        importedQuestionIds.contains(question.getId())
                ))
                .toList();
    }

    /** Kiểm tra câu hỏi đã duyệt rồi lưu một batch staging để người dùng tiếp tục review. */
    @Transactional
    public StagingBatchResponse importCourseQuestions(UUID setId, ImportCourseQuestionsRequest request) {
        SetContext context = resolveSetContext(setId);
        List<UUID> questionIds = requireQuestionIds(request);
        assertNoDuplicates(questionIds, "Question import list contains duplicate ids");
        List<Question> questions = loadQuestionsInRequestOrder(questionIds);
        if (questions.size() != questionIds.size()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "All submitted questions must exist");
        }
        Set<UUID> importedQuestionIds = importedSourceQuestionIds(setId, questionIds);
        if (!importedQuestionIds.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.CONFLICT,
                    "One or more questions were already imported into this flashcard set"
            );
        }
        questions.forEach(question -> validateImportQuestion(question, context.course().getId()));

        UserAccount actor = currentUserService.requireAuthenticatedUser();
        Map<UUID, List<QuestionAnswer>> answersByQuestionId = answersByQuestionId(questions);
        FlashcardStagingBatch batch = new FlashcardStagingBatch();
        batch.setFlashcardSet(context.flashcardSet());
        batch.setLesson(context.lesson());
        batch.setCurriculumLessonId(context.curriculumLessonId());
        batch.setCourse(context.course());
        batch.setCreatedBy(actor);
        batch.setSourceType(SOURCE_TYPE_COURSE_QUESTIONS);
        batch.setStatus(STATUS_DRAFT);
        batch.setSourceName(context.course().getTitle() + " - Course questions");
        FlashcardStagingBatch savedBatch = stagingBatchRepository.save(batch);

        List<FlashcardStagingCard> cards = new ArrayList<>();
        for (int index = 0; index < questions.size(); index += 1) {
            Question question = questions.get(index);
            List<QuestionAnswer> answers = answersByQuestionId.getOrDefault(question.getId(), List.of());
            FlashcardStagingCard card = new FlashcardStagingCard();
            card.setBatch(savedBatch);
            card.setSourceQuestionId(question.getId());
            card.setFrontText(buildFrontText(question, answers));
            card.setBackText(buildBackText(answers));
            card.setExplanation(normalizeQuestionContent(question.getExplanation()));
            card.setSourceExcerpt(normalizeQuestionContent(question.getQuestionText()));
            card.setStatus(STATUS_DRAFT);
            card.setSortOrder(index);
            validateCard(card);
            cards.add(card);
        }
        return toBatchResponse(savedBatch, stagingCardRepository.saveAll(cards));
    }

    /** Tạo ứng viên từ câu hỏi khóa học nhưng không ghi staging trước khi người dùng xác nhận. */
    @Transactional(readOnly = true)
    public TemporaryFlashcardCandidateBatchResponse previewCourseQuestions(
            UUID setId,
            ImportCourseQuestionsRequest request
    ) {
        SetContext context = resolveSetContext(setId);
        List<UUID> questionIds = requireQuestionIds(request);
        assertNoDuplicates(questionIds, "Question import contains duplicate ids");
        List<Question> questions = loadQuestionsInRequestOrder(questionIds);
        if (questions.size() != questionIds.size()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "One or more questions were not found");
        }
        for (Question question : questions) {
            if (!context.course().getId().equals(question.getCourseId())) {
                throw new BusinessException(
                        ErrorCode.INVALID_REQUEST,
                        "Question must belong to the same course as the flashcard set"
                );
            }
            if (question.getStatus() != QuestionStatus.APPROVED) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST, "Only approved questions can be imported");
            }
        }
        Map<UUID, List<QuestionAnswer>> answersByQuestionId = answersByQuestionId(questions);
        List<TemporaryCandidateSeed> candidates = questions.stream()
                .map(question -> {
                    List<QuestionAnswer> answers = answersByQuestionId.getOrDefault(question.getId(), List.of());
                    return new TemporaryCandidateSeed(
                            question.getId(),
                            buildFrontText(question, answers),
                            buildBackText(answers),
                            normalizeQuestionContent(question.getExplanation()),
                            normalizeQuestionContent(question.getQuestionText())
                    );
                })
                .toList();
        return toTemporaryBatchResponse(
                context,
                candidates,
                questionIds.size(),
                context.course().getTitle() + " - Course questions"
        );
    }

    /** Bắt buộc request có ít nhất một mã câu hỏi. */
    private List<UUID> requireQuestionIds(ImportCourseQuestionsRequest request) {
        if (request == null || request.questionIds() == null || request.questionIds().isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "At least one question id is required");
        }
        return request.questionIds();
    }

    /** Bảo đảm câu hỏi đã được duyệt và thuộc đúng khóa học của flashcard set. */
    private void validateImportQuestion(Question question, UUID courseId) {
        if (question.getStatus() != QuestionStatus.APPROVED) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Only approved questions can be imported");
        }
        if (!courseId.equals(question.getCourseId())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Question does not belong to this flashcard course");
        }
    }

    /** Nạp câu hỏi theo đúng thứ tự ID người dùng gửi lên. */
    private List<Question> loadQuestionsInRequestOrder(List<UUID> questionIds) {
        Map<UUID, Question> questionsById = questionRepository.findAllById(questionIds).stream()
                .collect(Collectors.toMap(Question::getId, Function.identity()));
        return questionIds.stream().map(questionsById::get).filter(java.util.Objects::nonNull).toList();
    }

    /** Gom đáp án theo mã câu hỏi và giữ thứ tự đáp án từ repository. */
    private Map<UUID, List<QuestionAnswer>> answersByQuestionId(List<Question> questions) {
        List<UUID> questionIds = questions.stream().map(Question::getId).toList();
        if (questionIds.isEmpty()) {
            return Map.of();
        }
        return questionAnswerRepository.findByQuestionIdInOrderByQuestionIdAscOrderIndexAsc(questionIds).stream()
                .collect(Collectors.groupingBy(QuestionAnswer::getQuestionId, LinkedHashMap::new, Collectors.toList()));
    }

    /** Tạo mặt trước flashcard, kèm lựa chọn đối với câu hỏi có options. */
    private String buildFrontText(Question question, List<QuestionAnswer> answers) {
        String questionText = normalizeRequiredQuestionContent(question.getQuestionText(), "Question text is required");
        if (!hasOptions(question, answers)) {
            return questionText;
        }
        List<String> options = orderedAnswers(answers).stream()
                .map(QuestionAnswer::getAnswerText)
                .map(this::normalizeQuestionContent)
                .filter(java.util.Objects::nonNull)
                .toList();
        if (options.isEmpty()) {
            return questionText;
        }
        StringBuilder builder = new StringBuilder(questionText).append("\n\nOptions:");
        for (int index = 0; index < options.size(); index += 1) {
            builder.append("\n").append(index + 1).append(". ").append(options.get(index));
        }
        return builder.toString();
    }

    /** Xác định loại câu hỏi nào cần đưa các lựa chọn lên mặt trước. */
    private boolean hasOptions(Question question, List<QuestionAnswer> answers) {
        if (answers == null || answers.isEmpty()) {
            return false;
        }
        QuestionType type = question.getQuestionType();
        return type == QuestionType.SINGLE_CHOICE
                || type == QuestionType.MULTIPLE_CHOICE
                || type == QuestionType.TRUE_FALSE;
    }

    /** Sắp xếp đáp án theo orderIndex để giữ nguyên thứ tự hiển thị. */
    private List<QuestionAnswer> orderedAnswers(List<QuestionAnswer> answers) {
        if (answers == null || answers.isEmpty()) {
            return List.of();
        }
        return answers.stream()
                .sorted(Comparator.comparing(answer -> answer.getOrderIndex() == null ? 0 : answer.getOrderIndex()))
                .toList();
    }

    /** Tạo mặt sau từ các đáp án đúng và chặn câu hỏi chưa có đáp án đúng. */
    private String buildBackText(List<QuestionAnswer> answers) {
        List<String> correctAnswers = orderedAnswers(answers).stream()
                .filter(answer -> Boolean.TRUE.equals(answer.getIsCorrect()))
                .map(QuestionAnswer::getAnswerText)
                .map(this::normalizeQuestionContent)
                .filter(java.util.Objects::nonNull)
                .toList();
        if (correctAnswers.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Question must have at least one correct answer");
        }
        return String.join("\n", correctAnswers);
    }

    /** Tạo bản xem trước và đánh dấu các ứng viên trùng với flashcard hiện tại. */
    private TemporaryFlashcardCandidateBatchResponse toTemporaryBatchResponse(
            SetContext context,
            List<TemporaryCandidateSeed> candidates,
            int requestedCount,
            String sourceName
    ) {
        List<FlashcardCard> currentCards = flashcardCardRepository
                .findActiveBySetIdOrderByOrderIndex(context.flashcardSet().getId());
        Set<String> currentKeys = (currentCards == null ? List.<FlashcardCard>of() : currentCards).stream()
                .map(card -> duplicateKey(card.getFrontText(), card.getBackText()))
                .collect(Collectors.toSet());
        Map<String, Long> candidateCounts = candidates.stream()
                .map(candidate -> duplicateKey(candidate.frontText(), candidate.backText()))
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        List<TemporaryFlashcardCandidateResponse> cards = new ArrayList<>();
        for (int index = 0; index < candidates.size(); index += 1) {
            TemporaryCandidateSeed candidate = candidates.get(index);
            String key = duplicateKey(candidate.frontText(), candidate.backText());
            boolean duplicateCurrent = currentKeys.contains(key);
            boolean duplicateCandidates = candidateCounts.getOrDefault(key, 0L) > 1L;
            List<String> issues = new ArrayList<>();
            if (duplicateCurrent) {
                issues.add("Matches Current Flashcards");
            }
            if (duplicateCandidates) {
                issues.add("Duplicate in candidates");
            }
            boolean duplicate = duplicateCurrent || duplicateCandidates;
            cards.add(new TemporaryFlashcardCandidateResponse(
                    UUID.randomUUID(),
                    candidate.sourceQuestionId(),
                    candidate.frontText(),
                    candidate.backText(),
                    null,
                    null,
                    null,
                    candidate.explanation(),
                    candidate.sourceExcerpt(),
                    !duplicate,
                    duplicate,
                    false,
                    issues,
                    index
            ));
        }
        return new TemporaryFlashcardCandidateBatchResponse(
                UUID.randomUUID(),
                context.flashcardSet().getId(),
                context.lesson() == null ? context.curriculumLessonId() : context.lesson().getId(),
                context.curriculumLessonId(),
                context.course().getId(),
                SOURCE_TYPE_COURSE_QUESTIONS,
                sourceName,
                requestedCount,
                cards.size(),
                cards
        );
    }

    /** Tạo khóa so sánh trùng lặp từ nội dung đã bỏ HTML và khoảng trắng thừa. */
    private String duplicateKey(String frontText, String backText) {
        return normalizeForDuplicate(frontText) + "\n" + normalizeForDuplicate(backText);
    }

    /** Chuẩn hóa nội dung trước khi kiểm tra trùng lặp. */
    private String normalizeForDuplicate(String value) {
        if (value == null) {
            return "";
        }
        String decoded = HtmlUtils.htmlUnescape(value).replace('\u00A0', ' ');
        String plainText = looksLikeHtml(decoded) ? HtmlPlainTextExtractor.extract(decoded) : decoded;
        return plainText.replaceAll("<[^>]+>", " ")
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    /** Nạp flashcard set và kiểm tra quyền đọc course hoặc class curriculum tương ứng. */
    private SetContext resolveSetContext(UUID setId) {
        FlashcardSet flashcardSet = flashcardSetRepository.findByIdAndDeletedAtIsNull(setId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Flashcard set was not found"));
        Lesson lesson = flashcardSet.getLesson();
        if (lesson != null) {
            if (lesson.getCourse() == null || lesson.getCourse().getDeletedAt() != null) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Flashcard lesson was not found");
            }
            if (lesson.getType() != LessonType.FLASHCARD) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST, "Flashcard set is not linked to a flashcard lesson");
            }
            if (courseAccessService != null) {
                courseAccessService.requireReadableCourse(lesson.getCourse().getId());
            }
            return new SetContext(flashcardSet, lesson, null, lesson.getCourse());
        }
        UUID curriculumLessonId = flashcardSet.getCurriculumLessonId();
        CurriculumLesson curriculumLesson = curriculumLessonId == null || curriculumLessonRepository == null ? null
                : curriculumLessonRepository.findById(curriculumLessonId).orElse(null);
        Course course = flashcardSet.getCourse();
        if (curriculumLesson == null || curriculumLesson.getType() != LessonType.FLASHCARD
                || course == null || course.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Flashcard lesson was not found");
        }
        var version = curriculumLesson.getSection().getCurriculumVersion();
        if (version.getScope() == CurriculumScope.CLASS) {
            if (version.getClassId() == null) {
                throw new BusinessException(ErrorCode.CONFLICT, "Class curriculum is inconsistent");
            }
            trainerClassCurriculumService.requireOwnedClassLessonForRead(version.getClassId(), curriculumLessonId);
        } else {
            courseAccessService.requireReadableCourse(course.getId());
        }
        return new SetContext(flashcardSet, null, curriculumLessonId, course);
    }

    /** Kiểm tra staging card có đủ nội dung ở cả hai mặt. */
    private void validateCard(FlashcardStagingCard card) {
        if (!hasText(card.getFrontText()) && !hasText(card.getFrontImageUrl())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Flashcard staging front side requires text or image");
        }
        if (!hasText(card.getBackText()) && !hasText(card.getBackImageUrl())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Flashcard staging back side requires text or image");
        }
    }

    /** Tìm các source question đã nằm trong batch draft hoặc approved của set. */
    private Set<UUID> importedSourceQuestionIds(UUID setId, List<UUID> sourceQuestionIds) {
        if (sourceQuestionIds == null || sourceQuestionIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(stagingCardRepository.findImportedSourceQuestionIds(
                setId,
                sourceQuestionIds,
                IMPORTED_SOURCE_STATUSES
        ));
    }

    /** Chặn danh sách ID trùng trước khi có bất kỳ thao tác ghi nào. */
    private void assertNoDuplicates(List<UUID> ids, String message) {
        if (new HashSet<>(ids).size() != ids.size()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, message);
        }
    }

    /** Chuyển status query sang enum và báo lỗi khi giá trị không hợp lệ. */
    private QuestionStatus parseQuestionStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return QuestionStatus.valueOf(status.trim().replace('-', '_').toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Question status is invalid");
        }
    }

    /** Chuyển question và answers thành DTO dùng ở danh sách nguồn. */
    private SourceQuestionResponse toSourceQuestionResponse(
            Question question,
            String sourceName,
            List<QuestionAnswer> answers,
            boolean imported
    ) {
        List<SourceQuestionAnswerResponse> answerResponses = answers.stream()
                .sorted(Comparator.comparing(answer -> answer.getOrderIndex() == null ? 0 : answer.getOrderIndex()))
                .map(this::toSourceAnswerResponse)
                .toList();
        List<String> correctAnswers = answers.stream()
                .filter(answer -> Boolean.TRUE.equals(answer.getIsCorrect()))
                .map(QuestionAnswer::getAnswerText)
                .toList();
        return new SourceQuestionResponse(
                question.getId(),
                question.getId(),
                sourceName,
                question.getCourseId(),
                question.getModuleId(),
                question.getQuestionText(),
                toApiValue(question.getQuestionType()),
                question.getDifficulty(),
                toApiValue(question.getStatus()),
                imported,
                question.getExplanation(),
                answerResponses,
                correctAnswers
        );
    }

    /** Chuyển một answer thành DTO giữ nguyên các trường alias cũ. */
    private SourceQuestionAnswerResponse toSourceAnswerResponse(QuestionAnswer answer) {
        int orderIndex = answer.getOrderIndex() == null ? 0 : answer.getOrderIndex();
        return new SourceQuestionAnswerResponse(
                answer.getId(),
                answer.getId(),
                answer.getAnswerText(),
                Boolean.TRUE.equals(answer.getIsCorrect()),
                Boolean.TRUE.equals(answer.getIsCorrect()),
                orderIndex,
                orderIndex
        );
    }

    /** Chuyển batch staging đã lưu thành response giữ nguyên JSON contract. */
    private StagingBatchResponse toBatchResponse(FlashcardStagingBatch batch, List<FlashcardStagingCard> cards) {
        return new StagingBatchResponse(
                batch.getId(),
                batch.getFlashcardSet().getId(),
                batch.getLesson() == null ? batch.getCurriculumLessonId() : batch.getLesson().getId(),
                batch.getCurriculumLessonId(),
                batch.getSourceVideoAiContentId(),
                batch.getCourse().getId(),
                batch.getSourceType(),
                batch.getStatus(),
                batch.getSourceName(),
                cards.stream()
                        .sorted(Comparator.comparing(card -> card.getSortOrder() == null ? 0 : card.getSortOrder()))
                        .map(this::toCardResponse)
                        .toList(),
                batch.getCreatedAt(),
                batch.getUpdatedAt(),
                batch.getApprovedAt(),
                batch.getApprovedBy() == null ? null : batch.getApprovedBy().getId()
        );
    }

    /** Chuyển staging card thành response. */
    private StagingCardResponse toCardResponse(FlashcardStagingCard card) {
        return new StagingCardResponse(
                card.getId(),
                card.getBatch().getId(),
                card.getSourceQuestionId(),
                card.getFrontText(),
                card.getBackText(),
                card.getFrontImageUrl(),
                card.getBackImageUrl(),
                card.getHint(),
                card.getExplanation(),
                card.getSourceExcerpt(),
                card.getStatus(),
                card.getSortOrder(),
                card.getCreatedAt(),
                card.getUpdatedAt()
        );
    }

    /** Đổi enum sang giá trị API chữ thường. */
    private String toApiValue(Enum<?> value) {
        return value == null ? null : value.name().toLowerCase(Locale.ROOT);
    }

    /** Bắt buộc nội dung question còn giá trị sau khi bỏ HTML. */
    private String normalizeRequiredQuestionContent(String value, String message) {
        String normalized = normalizeQuestionContent(value);
        if (normalized == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, message);
        }
        return normalized;
    }

    /** Giải mã HTML và chuyển nội dung question thành plain text dễ đọc. */
    private String normalizeQuestionContent(String value) {
        if (value == null) {
            return null;
        }
        String decoded = HtmlUtils.htmlUnescape(value).replace('\u00A0', ' ');
        return normalizePlainText(looksLikeHtml(decoded) ? HtmlPlainTextExtractor.extract(decoded) : decoded);
    }

    /** Nhận diện nội dung có HTML tag thực sự. */
    private boolean looksLikeHtml(String value) {
        return value != null && HTML_TAG_PATTERN.matcher(value).find();
    }

    /** Chuẩn hóa plain text, xuống dòng và khoảng trắng. */
    private String normalizePlainText(String value) {
        if (value == null) {
            return null;
        }
        String[] lines = value.replace('\u00A0', ' ')
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .split("\n", -1);
        List<String> cleanedLines = new ArrayList<>();
        for (String line : lines) {
            String cleaned = line.trim().replaceAll("[ \\t\\x0B\\f]+", " ");
            if (!cleaned.isEmpty()) {
                cleanedLines.add(cleaned);
            }
        }
        return cleanedLines.isEmpty() ? null : String.join("\n", cleanedLines);
    }

    /** Kiểm tra chuỗi có nội dung khác khoảng trắng. */
    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /** Parser chuyển HTML sang text và giữ ranh giới dòng của các block. */
    private static final class HtmlPlainTextExtractor extends HTMLEditorKit.ParserCallback {
        private static final Set<HTML.Tag> BLOCK_TAGS = Set.of(
                HTML.Tag.ADDRESS, HTML.Tag.BLOCKQUOTE, HTML.Tag.DD, HTML.Tag.DIV,
                HTML.Tag.DL, HTML.Tag.DT, HTML.Tag.H1, HTML.Tag.H2, HTML.Tag.H3,
                HTML.Tag.H4, HTML.Tag.H5, HTML.Tag.H6, HTML.Tag.HR, HTML.Tag.LI,
                HTML.Tag.OL, HTML.Tag.P, HTML.Tag.PRE, HTML.Tag.TABLE, HTML.Tag.TD,
                HTML.Tag.TH, HTML.Tag.TR, HTML.Tag.UL
        );
        private final StringBuilder builder = new StringBuilder();
        private boolean pendingLineBreak;

        /** Phân tích HTML và trả plain text; nếu parser lỗi thì giữ nguyên input. */
        static String extract(String html) {
            HtmlPlainTextExtractor callback = new HtmlPlainTextExtractor();
            try {
                new ParserDelegator().parse(new StringReader(html), callback, true);
            } catch (IOException exception) {
                return html;
            }
            return callback.builder.toString();
        }

        /** Ghi text node vào kết quả sau khi áp dụng line break đang chờ. */
        @Override
        public void handleText(char[] data, int pos) {
            appendLineBreakIfNeeded();
            builder.append(data);
        }

        /** Đánh dấu xuống dòng khi bắt đầu một block HTML. */
        @Override
        public void handleStartTag(HTML.Tag tag, MutableAttributeSet attributes, int pos) {
            if (isBlock(tag)) {
                requestLineBreak();
            }
        }

        /** Đánh dấu xuống dòng khi kết thúc một block HTML. */
        @Override
        public void handleEndTag(HTML.Tag tag, int pos) {
            if (isBlock(tag)) {
                requestLineBreak();
            }
        }

        /** Xử lý thẻ đơn như br hoặc hr thành ranh giới dòng. */
        @Override
        public void handleSimpleTag(HTML.Tag tag, MutableAttributeSet attributes, int pos) {
            if (tag == HTML.Tag.BR || isBlock(tag)) {
                requestLineBreak();
            }
        }

        /** Kiểm tra tag có phải phần tử block cần tách dòng hay không. */
        private boolean isBlock(HTML.Tag tag) {
            return BLOCK_TAGS.contains(tag);
        }

        /** Ghi nhận yêu cầu xuống dòng nếu đã có nội dung trước đó. */
        private void requestLineBreak() {
            if (builder.length() > 0) {
                pendingLineBreak = true;
            }
        }

        /** Chèn đúng một line break trước text node tiếp theo. */
        private void appendLineBreakIfNeeded() {
            if (!pendingLineBreak) {
                return;
            }
            int length = builder.length();
            if (length > 0 && builder.charAt(length - 1) != '\n') {
                builder.append('\n');
            }
            pendingLineBreak = false;
        }
    }

    private record TemporaryCandidateSeed(
            UUID sourceQuestionId,
            String frontText,
            String backText,
            String explanation,
            String sourceExcerpt
    ) {
    }

    private record SetContext(
            FlashcardSet flashcardSet,
            Lesson lesson,
            UUID curriculumLessonId,
            Course course
    ) {
    }
}
