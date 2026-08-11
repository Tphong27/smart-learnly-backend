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
import com.smartlearnly.backend.flashcard.staging.dto.AdminFlashcardStagingDtos.GenerateFromTranscriptRequest;
import com.smartlearnly.backend.flashcard.staging.dto.AdminFlashcardStagingDtos.GenerateFromTextRequest;
import com.smartlearnly.backend.flashcard.staging.dto.AdminFlashcardStagingDtos.StagingBatchResponse;
import com.smartlearnly.backend.flashcard.staging.dto.AdminFlashcardStagingDtos.StagingCardResponse;
import com.smartlearnly.backend.flashcard.staging.dto.AdminFlashcardStagingDtos.TemporaryFlashcardCandidateBatchResponse;
import com.smartlearnly.backend.flashcard.staging.dto.AdminFlashcardStagingDtos.TemporaryFlashcardCandidateResponse;
import com.smartlearnly.backend.flashcard.staging.entity.FlashcardStagingBatch;
import com.smartlearnly.backend.flashcard.staging.entity.FlashcardStagingCard;
import com.smartlearnly.backend.flashcard.staging.repository.FlashcardStagingBatchRepository;
import com.smartlearnly.backend.flashcard.staging.repository.FlashcardStagingCardRepository;
import com.smartlearnly.backend.flashcard.staging.service.FlashcardDocumentGenerationService.DocumentGenerationRequest;
import com.smartlearnly.backend.flashcard.staging.service.FlashcardDocumentTextExtractionService.DocumentTextExtractionResult;
import com.smartlearnly.backend.flashcard.staging.service.FlashcardTextGenerationService.GeneratedFlashcardCandidate;
import com.smartlearnly.backend.flashcard.staging.service.FlashcardTextGenerationService.GenerationRequest;
import com.smartlearnly.backend.flashcard.staging.service.FlashcardTextGenerationService.GenerationResult;
import com.smartlearnly.backend.flashcard.staging.service.FlashcardTranscriptTextExtractionService.TranscriptTextExtractionResult;
import com.smartlearnly.backend.learning.lesson.entity.Lesson;
import com.smartlearnly.backend.learning.lesson.entity.LessonType;
import com.smartlearnly.backend.user.entity.UserAccount;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.HtmlUtils;

/**
 * Tạo ứng viên flashcard từ văn bản, tài liệu hoặc transcript và lưu chúng vào vùng chờ duyệt.
 */
@Service
@RequiredArgsConstructor
public class FlashcardStagingGenerationService {
    private static final String SOURCE_TYPE_TEXT = "TEXT";
    private static final String SOURCE_TYPE_AI = "AI";
    private static final String SOURCE_TYPE_DOCX = "DOCX";
    private static final String SOURCE_TYPE_PDF = "PDF";
    private static final String SOURCE_TYPE_VIDEO_TRANSCRIPT = "VIDEO_TRANSCRIPT";
    private static final String SOURCE_NAME_PASTED_TEXT_GENERATION = "Pasted Text Generation";
    private static final String SOURCE_NAME_UPLOADED_DOCX_GENERATION = "Uploaded DOCX Generation";
    private static final String SOURCE_NAME_UPLOADED_PDF_GENERATION = "Uploaded PDF Generation";
    private static final String SOURCE_NAME_VIDEO_TRANSCRIPT_GENERATION = "Video Transcript Generation";
    private static final String STATUS_DRAFT = "draft";
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[a-zA-Z][^>]*>");
    private static final long MAX_GENERATION_FILE_SIZE_BYTES = 10L * 1024L * 1024L;
    private static final long MAX_TRANSCRIPT_FILE_SIZE_BYTES = 5L * 1024L * 1024L;
    private static final int DEFAULT_DESIRED_COUNT = 10;
    private static final int MIN_SOURCE_TEXT_LENGTH = 100;
    private static final int MAX_SOURCE_TEXT_LENGTH = 20000;
    private static final int MAX_GENERATED_FRONT_TEXT_LENGTH = 2000;
    private static final int MAX_GENERATED_BACK_TEXT_LENGTH = 4000;
    private static final int MAX_GENERATED_HINT_LENGTH = 1000;
    private static final int MAX_GENERATED_EXPLANATION_LENGTH = 6000;
    private static final int MAX_GENERATED_SOURCE_EXCERPT_LENGTH = 1000;
    private static final int MIN_DESIRED_COUNT = 1;
    private static final int MAX_DESIRED_COUNT = 30;
    private static final Set<String> ALLOWED_GENERATION_MODES = Set.of("AI", "RULE_BASED");
    private static final Set<String> PDF_CONTENT_TYPES = Set.of(
            "application/pdf",
            "application/x-pdf",
            "application/octet-stream"
    );
    private static final Set<String> DOCX_CONTENT_TYPES = Set.of(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/zip",
            "application/octet-stream"
    );

    private final FlashcardSetRepository flashcardSetRepository;
    private final FlashcardCardRepository flashcardCardRepository;
    private final FlashcardStagingBatchRepository stagingBatchRepository;
    private final FlashcardStagingCardRepository stagingCardRepository;
    private final CurrentUserService currentUserService;
    private final FlashcardTextGenerationService flashcardTextGenerationService;
    private final FlashcardDocumentGenerationService flashcardDocumentGenerationService;
    private final FlashcardDocumentTextExtractionService documentTextExtractionService;
    private final FlashcardTranscriptTextExtractionService transcriptTextExtractionService;
    @Autowired
    private CurriculumLessonRepository curriculumLessonRepository;
    @Autowired
    private CourseAccessService courseAccessService;
    @Autowired
    private TrainerClassCurriculumService trainerClassCurriculumService;

    /** Tạo và lưu một batch staging từ văn bản người dùng dán vào. */
    @Transactional
    public StagingBatchResponse generateFromText(UUID setId, GenerateFromTextRequest request) {
        SetContext context = resolveSetContext(setId);
        TextGenerationInput input = validateGenerateFromTextRequest(request);
        UserAccount actor = currentUserService.requireAuthenticatedUser();
        GenerationResult result = flashcardTextGenerationService.generate(new GenerationRequest(
                input.sourceText(),
                input.desiredCount(),
                input.language(),
                input.generationMode()
        ));
        return createGeneratedStagingBatch(
                context,
                actor,
                result,
                input.desiredCount(),
                resolveGeneratedSourceType(result),
                SOURCE_NAME_PASTED_TEXT_GENERATION,
                "Pasted text did not produce any flashcard candidates"
        );
    }

    /** Đọc DOCX/PDF, sinh flashcard và lưu các ứng viên vào staging để người dùng duyệt. */
    @Transactional
    public StagingBatchResponse generateFromFile(
            UUID setId,
            MultipartFile file,
            Integer desiredCount,
            String language,
            String generationMode
    ) {
        SetContext context = resolveSetContext(setId);
        DocumentFileInput fileInput = validateGenerateFromFileRequest(file);
        GenerationOptions options = validateGenerationOptions(desiredCount, language, generationMode);
        UserAccount actor = currentUserService.requireAuthenticatedUser();
        DocumentTextExtractionResult extraction = documentTextExtractionService.extract(file);
        String sourceType = resolveDocumentSourceType(extraction, fileInput.extension());
        String sourceName = resolveDocumentSourceName(extraction, sourceType);
        GenerationResult result = generateFromDocument(extraction, options, sourceType, sourceName);
        return createGeneratedStagingBatch(
                context,
                actor,
                result,
                options.desiredCount(),
                sourceType,
                sourceName,
                "Uploaded file did not produce any flashcard candidates"
        );
    }

    /** Tạo bản xem trước từ DOCX/PDF nhưng chưa ghi batch hoặc card staging xuống cơ sở dữ liệu. */
    @Transactional(readOnly = true)
    public TemporaryFlashcardCandidateBatchResponse generateTemporaryFromFile(
            UUID setId,
            MultipartFile file,
            Integer desiredCount,
            String language,
            String generationMode
    ) {
        SetContext context = resolveSetContext(setId);
        DocumentFileInput fileInput = validateGenerateFromFileRequest(file);
        GenerationOptions options = validateGenerationOptions(desiredCount, language, generationMode);
        DocumentTextExtractionResult extraction = documentTextExtractionService.extract(file);
        String sourceType = resolveDocumentSourceType(extraction, fileInput.extension());
        String sourceName = resolveDocumentSourceName(extraction, sourceType);
        GenerationResult result = generateFromDocument(extraction, options, sourceType, sourceName);
        List<GeneratedFlashcardCandidate> candidates = validGeneratedCandidates(
                result == null ? List.of() : result.candidates(),
                options.desiredCount()
        );
        if (candidates.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Uploaded file did not produce any flashcard candidates");
        }
        return toTemporaryBatchResponse(
                context,
                candidates.stream()
                        .map(candidate -> new TemporaryCandidateSeed(
                                candidate.frontText(),
                                candidate.backText(),
                                candidate.hint(),
                                candidate.explanation(),
                                candidate.sourceExcerpt()
                        ))
                        .toList(),
                options.desiredCount(),
                sourceType,
                sourceName
        );
    }

    /** Làm sạch transcript được dán vào, sinh flashcard và lưu staging chờ duyệt. */
    @Transactional
    public StagingBatchResponse generateFromTranscript(UUID setId, GenerateFromTranscriptRequest request) {
        SetContext context = resolveSetContext(setId);
        if (request == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Request body is required");
        }
        GenerationOptions options = validateGenerationOptions(
                request.desiredCount(),
                request.language(),
                request.generationMode()
        );
        UserAccount actor = currentUserService.requireAuthenticatedUser();
        TranscriptTextExtractionResult extraction = transcriptTextExtractionService.extractRaw(
                request.transcriptText(),
                request.sourceName()
        );
        String transcriptText = validateCleanedGenerationText(
                extraction == null ? null : extraction.text(),
                "Transcript text must be at least 100 characters after cleaning"
        );
        GenerationResult result = generateFromTranscriptText(transcriptText, options);
        return createGeneratedStagingBatch(
                context,
                actor,
                result,
                options.desiredCount(),
                SOURCE_TYPE_VIDEO_TRANSCRIPT,
                resolveTranscriptSourceName(extraction),
                "Transcript text did not produce any flashcard candidates"
        );
    }

    /** Đọc SRT/VTT, sinh flashcard và lưu staging chờ duyệt. */
    @Transactional
    public StagingBatchResponse generateFromTranscriptFile(
            UUID setId,
            MultipartFile file,
            Integer desiredCount,
            String language,
            String generationMode
    ) {
        SetContext context = resolveSetContext(setId);
        validateGenerateFromTranscriptFileRequest(file);
        GenerationOptions options = validateGenerationOptions(desiredCount, language, generationMode);
        UserAccount actor = currentUserService.requireAuthenticatedUser();
        TranscriptTextExtractionResult extraction = transcriptTextExtractionService.extractFile(file);
        String transcriptText = validateCleanedGenerationText(
                extraction == null ? null : extraction.text(),
                "Uploaded transcript file did not contain enough text to generate flashcards"
        );
        GenerationResult result = generateFromTranscriptText(transcriptText, options);
        return createGeneratedStagingBatch(
                context,
                actor,
                result,
                options.desiredCount(),
                SOURCE_TYPE_VIDEO_TRANSCRIPT,
                resolveTranscriptSourceName(extraction),
                "Uploaded transcript file did not produce any flashcard candidates"
        );
    }

    /** Gọi generator tài liệu với đúng nội dung, ảnh và tùy chọn đã trích xuất. */
    private GenerationResult generateFromDocument(
            DocumentTextExtractionResult extraction,
            GenerationOptions options,
            String sourceType,
            String sourceName
    ) {
        return flashcardDocumentGenerationService.generate(new DocumentGenerationRequest(
                extraction == null ? null : extraction.text(),
                extraction == null ? List.of() : extraction.images(),
                extraction == null ? List.of() : extraction.renderedPageImages(),
                options.desiredCount(),
                options.language(),
                sourceType,
                sourceName
        ));
    }

    /** Gọi generator văn bản cho transcript đã được làm sạch. */
    private GenerationResult generateFromTranscriptText(String transcriptText, GenerationOptions options) {
        return flashcardTextGenerationService.generate(new GenerationRequest(
                transcriptText,
                options.desiredCount(),
                options.language(),
                options.generationMode()
        ));
    }

    /** Kiểm tra yêu cầu sinh từ văn bản và chuẩn hóa các tùy chọn mặc định. */
    private TextGenerationInput validateGenerateFromTextRequest(GenerateFromTextRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Request body is required");
        }
        String sourceText = normalizeNullable(request.sourceText());
        if (sourceText == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "sourceText is required");
        }
        if (sourceText.length() < MIN_SOURCE_TEXT_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "sourceText must be at least 100 characters");
        }
        if (sourceText.length() > MAX_SOURCE_TEXT_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "sourceText must not exceed 20000 characters");
        }
        GenerationOptions options = validateGenerationOptions(
                request.desiredCount(),
                request.language(),
                request.generationMode()
        );
        return new TextGenerationInput(
                sourceText,
                options.desiredCount(),
                options.language(),
                options.generationMode()
        );
    }

    /** Kiểm tra kích thước, phần mở rộng và MIME type của DOCX/PDF tải lên. */
    private DocumentFileInput validateGenerateFromFileRequest(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Uploaded DOCX or PDF file is required");
        }
        if (file.getSize() > MAX_GENERATION_FILE_SIZE_BYTES) {
            throw new BusinessException(ErrorCode.PAYLOAD_TOO_LARGE, "Uploaded file must not exceed 10 MB");
        }
        String originalFileName = sanitizeOriginalFileName(file.getOriginalFilename());
        String extension = extractFileExtension(originalFileName);
        if (!SOURCE_TYPE_DOCX.toLowerCase(Locale.ROOT).equals(extension)
                && !SOURCE_TYPE_PDF.toLowerCase(Locale.ROOT).equals(extension)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Uploaded file must be a DOCX or PDF file");
        }
        String contentType = normalizeNullable(file.getContentType());
        if (contentType != null) {
            contentType = contentType.toLowerCase(Locale.ROOT);
            boolean supportedContentType = "pdf".equals(extension)
                    ? PDF_CONTENT_TYPES.contains(contentType)
                    : DOCX_CONTENT_TYPES.contains(contentType);
            if (!supportedContentType) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST, "Uploaded file content type must match the file extension");
            }
        }
        return new DocumentFileInput(extension);
    }

    /** Kiểm tra kích thước và phần mở rộng của transcript SRT/VTT tải lên. */
    private void validateGenerateFromTranscriptFileRequest(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Uploaded transcript file is required");
        }
        if (file.getSize() > MAX_TRANSCRIPT_FILE_SIZE_BYTES) {
            throw new BusinessException(ErrorCode.PAYLOAD_TOO_LARGE, "Uploaded transcript file must not exceed 5 MB");
        }
        String originalFileName = sanitizeOriginalFileName(file.getOriginalFilename());
        String extension = extractTranscriptFileExtension(originalFileName);
        if (!"srt".equals(extension) && !"vtt".equals(extension)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Uploaded transcript file must be an SRT or VTT file");
        }
    }

    /** Chuẩn hóa số lượng, ngôn ngữ và chế độ generation theo contract hiện hành. */
    private GenerationOptions validateGenerationOptions(
            Integer desiredCountValue,
            String languageValue,
            String generationModeValue
    ) {
        int desiredCount = desiredCountValue == null ? DEFAULT_DESIRED_COUNT : desiredCountValue;
        if (desiredCount < MIN_DESIRED_COUNT) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "desiredCount must be at least 1");
        }
        if (desiredCount > MAX_DESIRED_COUNT) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "desiredCount must not exceed 30");
        }
        String language = normalizeNullable(languageValue);
        if (language == null) {
            language = "auto";
        }
        String generationMode = normalizeNullable(generationModeValue);
        if (generationMode == null) {
            generationMode = SOURCE_TYPE_AI;
        }
        generationMode = generationMode.replace('-', '_').toUpperCase(Locale.ROOT);
        if (!ALLOWED_GENERATION_MODES.contains(generationMode)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "generationMode must be AI or RULE_BASED");
        }
        return new GenerationOptions(desiredCount, language, generationMode);
    }

    /** Làm sạch nguồn text và giới hạn độ dài trước khi gửi sang provider. */
    private String validateCleanedGenerationText(String value, String message) {
        String cleanedText = normalizeExtractedText(value);
        if (cleanedText.length() < MIN_SOURCE_TEXT_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, message);
        }
        return trimToMaxSourceText(cleanedText);
    }

    /** Lưu batch và các ứng viên hợp lệ để giữ bước human review trước khi publication. */
    private StagingBatchResponse createGeneratedStagingBatch(
            SetContext context,
            UserAccount actor,
            GenerationResult result,
            int desiredCount,
            String sourceType,
            String sourceName,
            String emptyResultMessage
    ) {
        List<GeneratedFlashcardCandidate> candidates = validGeneratedCandidates(
                result == null ? List.of() : result.candidates(),
                desiredCount
        );
        if (candidates.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, emptyResultMessage);
        }
        FlashcardStagingBatch batch = new FlashcardStagingBatch();
        batch.setFlashcardSet(context.flashcardSet());
        batch.setLesson(context.lesson());
        batch.setCurriculumLessonId(context.curriculumLessonId());
        batch.setCourse(context.course());
        batch.setCreatedBy(actor);
        batch.setSourceType(sourceType);
        batch.setStatus(STATUS_DRAFT);
        batch.setSourceName(sourceName);
        FlashcardStagingBatch savedBatch = stagingBatchRepository.save(batch);
        List<FlashcardStagingCard> cards = new ArrayList<>();
        for (int index = 0; index < candidates.size(); index += 1) {
            GeneratedFlashcardCandidate candidate = candidates.get(index);
            FlashcardStagingCard card = new FlashcardStagingCard();
            card.setBatch(savedBatch);
            card.setFrontText(normalizeRequired(candidate.frontText(), "Generated flashcard front text is required"));
            card.setBackText(normalizeRequired(candidate.backText(), "Generated flashcard back text is required"));
            card.setHint(normalizeNullable(candidate.hint()));
            card.setExplanation(normalizeNullable(candidate.explanation()));
            card.setSourceExcerpt(normalizeNullable(candidate.sourceExcerpt()));
            card.setStatus(STATUS_DRAFT);
            card.setSortOrder(index);
            validateCard(card);
            cards.add(card);
        }
        List<FlashcardStagingCard> savedCards = stagingCardRepository.saveAll(cards);
        return toBatchResponse(savedBatch, savedCards);
    }

    /** Loại ứng viên rỗng, quá dài hoặc trùng nhau trước khi lưu staging. */
    private List<GeneratedFlashcardCandidate> validGeneratedCandidates(
            List<GeneratedFlashcardCandidate> candidates,
            int desiredCount
    ) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        Set<String> seen = new HashSet<>();
        List<GeneratedFlashcardCandidate> valid = new ArrayList<>();
        for (GeneratedFlashcardCandidate candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            String frontText = normalizeNullable(candidate.frontText());
            String backText = normalizeNullable(candidate.backText());
            if (frontText == null || backText == null) {
                continue;
            }
            if (frontText.length() > MAX_GENERATED_FRONT_TEXT_LENGTH || backText.length() > MAX_GENERATED_BACK_TEXT_LENGTH) {
                continue;
            }
            String key = duplicateKey(frontText, backText);
            if (!seen.add(key)) {
                continue;
            }
            valid.add(new GeneratedFlashcardCandidate(
                    frontText,
                    backText,
                    normalizeOptionalMax(candidate.hint(), MAX_GENERATED_HINT_LENGTH),
                    normalizeOptionalMax(candidate.explanation(), MAX_GENERATED_EXPLANATION_LENGTH),
                    normalizeOptionalMax(candidate.sourceExcerpt(), MAX_GENERATED_SOURCE_EXCERPT_LENGTH)
            ));
            if (valid.size() >= desiredCount) {
                break;
            }
        }
        return valid;
    }

    /** Đánh dấu lỗi và trùng lặp cho bản xem trước mà không ghi dữ liệu staging. */
    private TemporaryFlashcardCandidateBatchResponse toTemporaryBatchResponse(
            SetContext context,
            List<TemporaryCandidateSeed> candidates,
            int requestedCount,
            String sourceType,
            String sourceName
    ) {
        List<TemporaryCandidateSeed> normalizedCandidates = candidates.stream()
                .map(this::normalizeTemporaryCandidate)
                .toList();
        List<FlashcardCard> existingCards = flashcardCardRepository
                .findActiveBySetIdOrderByOrderIndex(context.flashcardSet().getId());
        Set<String> currentKeys = (existingCards == null ? List.<FlashcardCard>of() : existingCards)
                .stream()
                .map(card -> duplicateKey(card.getFrontText(), card.getBackText()))
                .filter(this::hasDuplicateKey)
                .collect(Collectors.toSet());
        Map<String, Long> candidateKeyCounts = normalizedCandidates.stream()
                .map(candidate -> duplicateKey(candidate.frontText(), candidate.backText()))
                .filter(this::hasDuplicateKey)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        List<TemporaryFlashcardCandidateResponse> cards = new ArrayList<>();
        for (int index = 0; index < normalizedCandidates.size(); index += 1) {
            TemporaryCandidateSeed candidate = normalizedCandidates.get(index);
            String key = duplicateKey(candidate.frontText(), candidate.backText());
            List<String> issues = new ArrayList<>();
            boolean invalid = !hasText(candidate.frontText()) && !hasText(candidate.backText());
            if (invalid) {
                issues.add("At least one side needs text or an image");
            }
            boolean duplicate = hasDuplicateKey(key)
                    && (currentKeys.contains(key) || candidateKeyCounts.getOrDefault(key, 0L) > 1L);
            if (hasDuplicateKey(key) && currentKeys.contains(key)) {
                issues.add("Matches Current Flashcards");
            }
            if (hasDuplicateKey(key) && candidateKeyCounts.getOrDefault(key, 0L) > 1L) {
                issues.add("Duplicate in candidates");
            }
            cards.add(new TemporaryFlashcardCandidateResponse(
                    UUID.randomUUID(),
                    null,
                    candidate.frontText(),
                    candidate.backText(),
                    null,
                    null,
                    candidate.hint(),
                    candidate.explanation(),
                    candidate.sourceExcerpt(),
                    !invalid && !duplicate,
                    duplicate,
                    invalid,
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
                sourceType,
                sourceName,
                requestedCount,
                cards.size(),
                cards
        );
    }

    /** Chuẩn hóa trường tùy chọn của một ứng viên tạm thời. */
    private TemporaryCandidateSeed normalizeTemporaryCandidate(TemporaryCandidateSeed candidate) {
        return new TemporaryCandidateSeed(
                normalizeNullable(candidate.frontText()),
                normalizeNullable(candidate.backText()),
                normalizeOptionalMax(candidate.hint(), MAX_GENERATED_HINT_LENGTH),
                normalizeOptionalMax(candidate.explanation(), MAX_GENERATED_EXPLANATION_LENGTH),
                normalizeOptionalMax(candidate.sourceExcerpt(), MAX_GENERATED_SOURCE_EXCERPT_LENGTH)
        );
    }

    /** Xác định nhãn nguồn theo kết quả thực tế của generator văn bản. */
    private String resolveGeneratedSourceType(GenerationResult result) {
        String sourceType = normalizeNullable(result == null ? null : result.sourceType());
        return sourceType != null && SOURCE_TYPE_AI.equals(sourceType.toUpperCase(Locale.ROOT))
                ? SOURCE_TYPE_AI
                : SOURCE_TYPE_TEXT;
    }

    /** Xác định loại nguồn DOCX/PDF mà không thay đổi cách ưu tiên phần mở rộng hiện tại. */
    private String resolveDocumentSourceType(DocumentTextExtractionResult extraction, String extension) {
        if ("pdf".equals(extension)) {
            return SOURCE_TYPE_PDF;
        }
        if ("docx".equals(extension)) {
            return SOURCE_TYPE_DOCX;
        }
        String extractedSourceType = normalizeNullable(extraction == null ? null : extraction.sourceType());
        if (extractedSourceType != null) {
            extractedSourceType = extractedSourceType.toUpperCase(Locale.ROOT);
            if (SOURCE_TYPE_DOCX.equals(extractedSourceType) || SOURCE_TYPE_PDF.equals(extractedSourceType)) {
                return extractedSourceType;
            }
        }
        throw new BusinessException(ErrorCode.INVALID_REQUEST, "Unsupported flashcard source file type");
    }

    /** Giữ tên nguồn do extractor cung cấp hoặc dùng tên mặc định theo loại tài liệu. */
    private String resolveDocumentSourceName(DocumentTextExtractionResult extraction, String sourceType) {
        String sourceName = normalizeNullable(extraction == null ? null : extraction.sourceName());
        if (sourceName != null) {
            return sourceName;
        }
        if (SOURCE_TYPE_DOCX.equals(sourceType)) {
            return SOURCE_NAME_UPLOADED_DOCX_GENERATION;
        }
        if (SOURCE_TYPE_PDF.equals(sourceType)) {
            return SOURCE_NAME_UPLOADED_PDF_GENERATION;
        }
        return "Uploaded File Generation";
    }

    /** Giữ tên transcript đã cung cấp hoặc dùng tên mặc định. */
    private String resolveTranscriptSourceName(TranscriptTextExtractionResult extraction) {
        String sourceName = normalizeNullable(extraction == null ? null : extraction.sourceName());
        return sourceName == null ? SOURCE_NAME_VIDEO_TRANSCRIPT_GENERATION : sourceName;
    }

    /** Nạp flashcard set và kiểm tra quyền đọc tương ứng với course hoặc class curriculum. */
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

    /** Kiểm tra mỗi card sinh ra có tối thiểu một nội dung ở cả hai mặt. */
    private void validateCard(FlashcardStagingCard card) {
        boolean hasFront = hasText(card.getFrontText()) || hasText(card.getFrontImageUrl());
        boolean hasBack = hasText(card.getBackText()) || hasText(card.getBackImageUrl());
        if (!hasFront) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Flashcard staging front side requires text or image");
        }
        if (!hasBack) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Flashcard staging back side requires text or image");
        }
    }

    /** Chuyển batch và card đã lưu thành response giữ nguyên JSON contract. */
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

    /** Chuyển một staging card thành DTO phản hồi. */
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

    /** Tạo khóa so sánh nội dung hai mặt để phát hiện thẻ trùng nhau. */
    private String duplicateKey(String frontText, String backText) {
        return normalizeForDuplicate(frontText) + "\n" + normalizeForDuplicate(backText);
    }

    /** Kiểm tra khóa trùng lặp có chứa nội dung thực tế hay không. */
    private boolean hasDuplicateKey(String key) {
        return key != null && !key.trim().isEmpty();
    }

    /** Chuẩn hóa HTML, khoảng trắng và chữ hoa/thường trước khi so sánh trùng lặp. */
    private String normalizeForDuplicate(String value) {
        if (value == null) {
            return "";
        }
        String decoded = HtmlUtils.htmlUnescape(value).replace('\u00A0', ' ');
        String plainText = looksLikeHtml(decoded) ? HtmlPlainTextExtractor.extract(decoded) : decoded;
        return plainText
                .replaceAll("<[^>]+>", " ")
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    /** Nhận diện nội dung chứa HTML tag trước khi chuyển thành plain text. */
    private boolean looksLikeHtml(String value) {
        return value != null && HTML_TAG_PATTERN.matcher(value).find();
    }

    /** Loại đường dẫn khỏi tên file và chặn tên file không hợp lệ. */
    private String sanitizeOriginalFileName(String originalFileName) {
        if (originalFileName == null || originalFileName.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Uploaded file name is required");
        }
        String normalized = originalFileName.trim().replace('\\', '/');
        String fileName = normalized.substring(normalized.lastIndexOf('/') + 1).trim();
        if (fileName.isBlank() || fileName.contains("..")) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Uploaded file name is invalid");
        }
        return fileName;
    }

    /** Lấy phần mở rộng DOCX/PDF từ tên file đã làm sạch. */
    private String extractFileExtension(String fileName) {
        int extensionIndex = fileName.lastIndexOf('.');
        if (extensionIndex < 0 || extensionIndex == fileName.length() - 1) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Uploaded file must be a DOCX or PDF file");
        }
        return fileName.substring(extensionIndex + 1).toLowerCase(Locale.ROOT);
    }

    /** Lấy phần mở rộng SRT/VTT từ tên transcript đã làm sạch. */
    private String extractTranscriptFileExtension(String fileName) {
        int extensionIndex = fileName.lastIndexOf('.');
        if (extensionIndex < 0 || extensionIndex == fileName.length() - 1) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Uploaded transcript file must be an SRT or VTT file");
        }
        return fileName.substring(extensionIndex + 1).toLowerCase(Locale.ROOT);
    }

    /** Chuẩn hóa xuống dòng và khoảng trắng của nội dung đã trích xuất. */
    private String normalizeExtractedText(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.replace("\r\n", "\n").replace('\r', '\n').replace('\u00a0', ' ');
        String[] paragraphs = normalized.split("\\n\\s*\\n");
        List<String> blocks = new ArrayList<>();
        for (String paragraph : paragraphs) {
            String block = paragraph.replaceAll("[\\t\\x0B\\f ]+", " ")
                    .replaceAll(" *\\n *", "\n")
                    .trim();
            if (!block.isBlank()) {
                blocks.add(block);
            }
        }
        return String.join("\n\n", blocks);
    }

    /** Cắt nguồn text tại ranh giới từ gần nhất mà không vượt contract 20.000 ký tự. */
    private String trimToMaxSourceText(String value) {
        if (value.length() <= MAX_SOURCE_TEXT_LENGTH) {
            return value;
        }
        int end = value.lastIndexOf(' ', MAX_SOURCE_TEXT_LENGTH);
        if (end < MIN_SOURCE_TEXT_LENGTH) {
            end = MAX_SOURCE_TEXT_LENGTH;
        }
        return value.substring(0, end).trim();
    }

    /** Bắt buộc trường văn bản có giá trị sau khi trim. */
    private String normalizeRequired(String value, String message) {
        String normalized = normalizeNullable(value);
        if (normalized == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, message);
        }
        return normalized;
    }

    /** Đổi chuỗi trắng thành null và giữ nguyên nội dung có nghĩa. */
    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    /** Cắt trường tùy chọn theo giới hạn mà ưu tiên kết thúc ở ranh giới từ. */
    private String normalizeOptionalMax(String value, int maxLength) {
        String normalized = normalizeNullable(value);
        if (normalized == null || normalized.length() <= maxLength) {
            return normalized;
        }
        int end = normalized.lastIndexOf(' ', maxLength);
        if (end < 1) {
            end = maxLength;
        }
        return normalized.substring(0, end).trim();
    }

    /** Kiểm tra một trường có nội dung khác khoảng trắng. */
    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /** Parser HTML giữ ranh giới dòng để khóa so sánh trùng lặp không làm mất cấu trúc nội dung. */
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

        /** Chuyển HTML thành text; giữ nguyên input nếu parser không đọc được. */
        static String extract(String html) {
            HtmlPlainTextExtractor callback = new HtmlPlainTextExtractor();
            try {
                new ParserDelegator().parse(new StringReader(html), callback, true);
            } catch (IOException exception) {
                return html;
            }
            return callback.builder.toString();
        }

        /** Ghi text node sau khi áp dụng line break đang chờ. */
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

        /** Chuyển br và các thẻ block đơn thành ranh giới dòng. */
        @Override
        public void handleSimpleTag(HTML.Tag tag, MutableAttributeSet attributes, int pos) {
            if (tag == HTML.Tag.BR || isBlock(tag)) {
                requestLineBreak();
            }
        }

        /** Kiểm tra tag có cần tạo ranh giới dòng hay không. */
        private boolean isBlock(HTML.Tag tag) {
            return BLOCK_TAGS.contains(tag);
        }

        /** Ghi nhận yêu cầu xuống dòng khi đã có nội dung trước đó. */
        private void requestLineBreak() {
            if (builder.length() > 0) {
                pendingLineBreak = true;
            }
        }

        /** Chèn đúng một line break trước text tiếp theo. */
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
            String frontText,
            String backText,
            String hint,
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

    private record TextGenerationInput(
            String sourceText,
            int desiredCount,
            String language,
            String generationMode
    ) {
    }

    private record GenerationOptions(
            int desiredCount,
            String language,
            String generationMode
    ) {
    }

    private record DocumentFileInput(String extension) {
    }
}
