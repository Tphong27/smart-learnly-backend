package com.smartlearnly.backend.flashcard.staging.service;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.course.entity.Course;
import com.smartlearnly.backend.flashcard.entity.FlashcardCard;
import com.smartlearnly.backend.flashcard.entity.FlashcardSet;
import com.smartlearnly.backend.flashcard.repository.FlashcardCardRepository;
import com.smartlearnly.backend.flashcard.repository.FlashcardSetRepository;
import com.smartlearnly.backend.flashcard.staging.dto.AdminFlashcardStagingDtos.ApproveStagingCardsRequest;
import com.smartlearnly.backend.flashcard.staging.dto.AdminFlashcardStagingDtos.ApproveStagingCardsResponse;
import com.smartlearnly.backend.flashcard.staging.dto.AdminFlashcardStagingDtos.GenerateFromTranscriptRequest;
import com.smartlearnly.backend.flashcard.staging.dto.AdminFlashcardStagingDtos.GenerateFromTextRequest;
import com.smartlearnly.backend.flashcard.staging.dto.AdminFlashcardStagingDtos.ImportQuestionBankRequest;
import com.smartlearnly.backend.flashcard.staging.dto.AdminFlashcardStagingDtos.SourceQuestionAnswerResponse;
import com.smartlearnly.backend.flashcard.staging.dto.AdminFlashcardStagingDtos.SourceQuestionResponse;
import com.smartlearnly.backend.flashcard.staging.dto.AdminFlashcardStagingDtos.StagingBatchResponse;
import com.smartlearnly.backend.flashcard.staging.dto.AdminFlashcardStagingDtos.StagingCardResponse;
import com.smartlearnly.backend.flashcard.staging.dto.AdminFlashcardStagingDtos.UpdateStagingCardRequest;
import com.smartlearnly.backend.flashcard.staging.entity.FlashcardStagingBatch;
import com.smartlearnly.backend.flashcard.staging.entity.FlashcardStagingCard;
import com.smartlearnly.backend.flashcard.staging.repository.FlashcardStagingBatchRepository;
import com.smartlearnly.backend.flashcard.staging.repository.FlashcardStagingCardRepository;
import com.smartlearnly.backend.flashcard.staging.service.FlashcardDocumentTextExtractionService.DocumentTextExtractionResult;
import com.smartlearnly.backend.flashcard.staging.service.FlashcardTextGenerationService.GeneratedFlashcardCandidate;
import com.smartlearnly.backend.flashcard.staging.service.FlashcardTextGenerationService.GenerationRequest;
import com.smartlearnly.backend.flashcard.staging.service.FlashcardTextGenerationService.GenerationResult;
import com.smartlearnly.backend.flashcard.staging.service.FlashcardTranscriptTextExtractionService.TranscriptTextExtractionResult;
import com.smartlearnly.backend.learning.lesson.entity.Lesson;
import com.smartlearnly.backend.learning.lesson.entity.LessonType;
import com.smartlearnly.backend.question.entity.Question;
import com.smartlearnly.backend.question.entity.QuestionAnswer;
import com.smartlearnly.backend.question.entity.QuestionBank;
import com.smartlearnly.backend.question.entity.QuestionStatus;
import com.smartlearnly.backend.question.entity.QuestionType;
import com.smartlearnly.backend.question.repository.QuestionAnswerRepository;
import com.smartlearnly.backend.question.repository.QuestionBankRepository;
import com.smartlearnly.backend.question.repository.QuestionRepository;
import com.smartlearnly.backend.user.entity.UserAccount;
import java.time.Instant;
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
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class AdminFlashcardStagingService {
    private static final String SOURCE_TYPE_QUESTION_BANK = "QUESTION_BANK";
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
    private static final String STATUS_APPROVED = "approved";
    private static final String STATUS_REJECTED = "rejected";
    private static final List<String> VISIBLE_STAGING_STATUSES = List.of(STATUS_DRAFT, STATUS_APPROVED);
    private static final Set<String> IMPORTED_SOURCE_STATUSES = Set.of(STATUS_DRAFT, STATUS_APPROVED);
    private static final long MAX_GENERATION_FILE_SIZE_BYTES = 10L * 1024L * 1024L;
    private static final long MAX_TRANSCRIPT_FILE_SIZE_BYTES = 5L * 1024L * 1024L;
    private static final int DEFAULT_DESIRED_COUNT = 10;
    private static final int MIN_SOURCE_TEXT_LENGTH = 100;
    private static final int MAX_SOURCE_TEXT_LENGTH = 20000;
    private static final int MIN_DESIRED_COUNT = 1;
    private static final int MAX_DESIRED_COUNT = 30;
    private static final Set<String> ALLOWED_DIFFICULTIES = Set.of("easy", "medium", "hard");
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
    private final QuestionRepository questionRepository;
    private final QuestionAnswerRepository questionAnswerRepository;
    private final QuestionBankRepository questionBankRepository;
    private final CurrentUserService currentUserService;
    private final FlashcardTextGenerationService flashcardTextGenerationService;
    private final FlashcardDocumentTextExtractionService documentTextExtractionService;
    private final FlashcardTranscriptTextExtractionService transcriptTextExtractionService;

    @Transactional(readOnly = true)
    public List<SourceQuestionResponse> listSourceQuestions(
            UUID setId,
            UUID questionBankId,
            String keyword,
            Short difficulty,
            String status
    ) {
        SetContext context = resolveSetContext(setId);
        QuestionStatus parsedStatus = parseQuestionStatus(status);
        List<Question> questions = questionRepository.searchForAdmin(
                questionBankId,
                context.course().getId(),
                null,
                keyword,
                null,
                parsedStatus == null ? null : toApiValue(parsedStatus),
                difficulty,
                Pageable.unpaged()
        ).stream()
                .filter(question -> context.course().getId().equals(question.getCourseId()))
                .toList();
        Map<UUID, List<QuestionAnswer>> answersByQuestionId = answersByQuestionId(questions);
        Map<UUID, String> bankNames = bankNames(questions.stream().map(Question::getQuestionBankId).collect(Collectors.toSet()));
        Set<UUID> importedQuestionIds = importedSourceQuestionIds(
                setId,
                questions.stream().map(Question::getId).toList()
        );

        return questions.stream()
                .map(question -> toSourceQuestionResponse(
                        question,
                        bankNames.get(question.getQuestionBankId()),
                        answersByQuestionId.getOrDefault(question.getId(), List.of()),
                        importedQuestionIds.contains(question.getId())
                ))
                .toList();
    }

    @Transactional
    public StagingBatchResponse importQuestionBank(UUID setId, ImportQuestionBankRequest request) {
        SetContext context = resolveSetContext(setId);
        if (request == null || request.questionIds() == null || request.questionIds().isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "At least one question id is required");
        }
        assertNoDuplicates(request.questionIds(), "Question import contains duplicate ids");
        UserAccount actor = currentUserService.requireAuthenticatedUser();
        List<Question> questions = loadQuestionsInRequestOrder(request.questionIds());
        if (questions.size() != request.questionIds().size()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "One or more questions were not found");
        }
        Set<UUID> importedQuestionIds = importedSourceQuestionIds(setId, request.questionIds());
        if (!importedQuestionIds.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "One or more selected questions were already imported");
        }
        for (Question question : questions) {
            if (!context.course().getId().equals(question.getCourseId())) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST, "Question must belong to the same course as the flashcard set");
            }
        }

        Map<UUID, List<QuestionAnswer>> answersByQuestionId = answersByQuestionId(questions);
        Map<UUID, String> bankNames = bankNames(questions.stream().map(Question::getQuestionBankId).collect(Collectors.toSet()));
        FlashcardStagingBatch batch = new FlashcardStagingBatch();
        batch.setFlashcardSet(context.flashcardSet());
        batch.setLesson(context.lesson());
        batch.setCourse(context.course());
        batch.setCreatedBy(actor);
        batch.setSourceType(SOURCE_TYPE_QUESTION_BANK);
        batch.setStatus(STATUS_DRAFT);
        batch.setSourceName(sourceName(bankNames));
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
            card.setExplanation(normalizeNullable(question.getExplanation()));
            card.setSourceExcerpt(normalizeNullable(question.getQuestionText()));
            card.setStatus(STATUS_DRAFT);
            card.setSortOrder(index);
            validateCard(card);
            cards.add(card);
        }

        List<FlashcardStagingCard> savedCards = stagingCardRepository.saveAll(cards);
        return toBatchResponse(savedBatch, savedCards);
    }

    @Transactional
    public StagingBatchResponse generateFromText(UUID setId, GenerateFromTextRequest request) {
        SetContext context = resolveSetContext(setId);
        TextGenerationInput input = validateGenerateFromTextRequest(request);
        UserAccount actor = currentUserService.requireAuthenticatedUser();

        GenerationResult result = flashcardTextGenerationService.generate(new GenerationRequest(
                input.sourceText(),
                input.desiredCount(),
                input.language(),
                input.difficulty(),
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

    @Transactional
    public StagingBatchResponse generateFromFile(
            UUID setId,
            MultipartFile file,
            Integer desiredCount,
            String language,
            String difficulty,
            String generationMode
    ) {
        SetContext context = resolveSetContext(setId);
        DocumentFileInput fileInput = validateGenerateFromFileRequest(file);
        GenerationOptions options = validateGenerationOptions(
                desiredCount,
                language,
                difficulty,
                generationMode
        );
        UserAccount actor = currentUserService.requireAuthenticatedUser();
        DocumentTextExtractionResult extraction = documentTextExtractionService.extract(file);
        String extractedText = validateExtractedText(extraction == null ? null : extraction.text());

        GenerationResult result = flashcardTextGenerationService.generate(new GenerationRequest(
                extractedText,
                options.desiredCount(),
                options.language(),
                options.difficulty(),
                options.generationMode()
        ));
        String sourceType = resolveDocumentSourceType(extraction, fileInput.extension());
        return createGeneratedStagingBatch(
                context,
                actor,
                result,
                options.desiredCount(),
                sourceType,
                resolveDocumentSourceName(extraction, sourceType),
                "Uploaded file did not produce any flashcard candidates"
        );
    }

    @Transactional
    public StagingBatchResponse generateFromTranscript(UUID setId, GenerateFromTranscriptRequest request) {
        SetContext context = resolveSetContext(setId);
        if (request == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Request body is required");
        }
        GenerationOptions options = validateGenerationOptions(
                request.desiredCount(),
                request.language(),
                request.difficulty(),
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

        GenerationResult result = flashcardTextGenerationService.generate(new GenerationRequest(
                transcriptText,
                options.desiredCount(),
                options.language(),
                options.difficulty(),
                options.generationMode()
        ));
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

    @Transactional
    public StagingBatchResponse generateFromTranscriptFile(
            UUID setId,
            MultipartFile file,
            Integer desiredCount,
            String language,
            String difficulty,
            String generationMode
    ) {
        SetContext context = resolveSetContext(setId);
        validateGenerateFromTranscriptFileRequest(file);
        GenerationOptions options = validateGenerationOptions(
                desiredCount,
                language,
                difficulty,
                generationMode
        );
        UserAccount actor = currentUserService.requireAuthenticatedUser();
        TranscriptTextExtractionResult extraction = transcriptTextExtractionService.extractFile(file);
        String transcriptText = validateCleanedGenerationText(
                extraction == null ? null : extraction.text(),
                "Uploaded transcript file did not contain enough text to generate flashcards"
        );

        GenerationResult result = flashcardTextGenerationService.generate(new GenerationRequest(
                transcriptText,
                options.desiredCount(),
                options.language(),
                options.difficulty(),
                options.generationMode()
        ));
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

    @Transactional(readOnly = true)
    public List<StagingBatchResponse> listStaging(UUID setId) {
        resolveSetContext(setId);
        List<FlashcardStagingBatch> batches = stagingBatchRepository
                .findByFlashcardSetIdAndStatusInOrderByCreatedAtDesc(setId, VISIBLE_STAGING_STATUSES);
        if (batches.isEmpty()) {
            return List.of();
        }
        List<UUID> batchIds = batches.stream().map(FlashcardStagingBatch::getId).toList();
        Map<UUID, List<FlashcardStagingCard>> cardsByBatchId = stagingCardRepository
                .findByBatchIdInOrderBySortOrderAscCreatedAtAsc(batchIds)
                .stream()
                .collect(Collectors.groupingBy(card -> card.getBatch().getId(), LinkedHashMap::new, Collectors.toList()));

        return batches.stream()
                .map(batch -> toBatchResponse(batch, cardsByBatchId.getOrDefault(batch.getId(), List.of())))
                .toList();
    }

    @Transactional
    public StagingCardResponse updateCard(UUID stagingCardId, UpdateStagingCardRequest request) {
        FlashcardStagingCard card = findStagingCard(stagingCardId);
        requireDraftCard(card, "Only draft staging cards can be edited");
        applyUpdate(card, request);
        validateCard(card);
        return toCardResponse(stagingCardRepository.save(card));
    }

    @Transactional
    public void rejectCard(UUID stagingCardId) {
        FlashcardStagingCard card = findStagingCard(stagingCardId);
        requireDraftCard(card, "Only draft staging cards can be rejected");
        card.setStatus(STATUS_REJECTED);
        stagingCardRepository.save(card);
    }

    @Transactional
    public ApproveStagingCardsResponse approve(UUID setId, ApproveStagingCardsRequest request) {
        SetContext context = resolveSetContext(setId);
        if (request == null || request.stagingCardIds() == null || request.stagingCardIds().isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "At least one staging card id is required");
        }
        UserAccount actor = currentUserService.requireAuthenticatedUser();
        List<FlashcardStagingCard> cards = resolveCardsForApproval(setId, request.stagingCardIds());
        assertNoDuplicateApproval(setId, cards);

        int nextOrderIndex = flashcardCardRepository.findMaxOrderIndexBySetId(setId) + 1;
        List<FlashcardCard> flashcards = new ArrayList<>();
        for (FlashcardStagingCard stagingCard : cards) {
            FlashcardCard flashcard = new FlashcardCard();
            flashcard.setFlashcardSet(context.flashcardSet());
            flashcard.setFrontText(stagingCard.getFrontText());
            flashcard.setFrontImageUrl(stagingCard.getFrontImageUrl());
            flashcard.setBackText(stagingCard.getBackText());
            flashcard.setBackImageUrl(stagingCard.getBackImageUrl());
            flashcard.setHint(stagingCard.getHint());
            flashcard.setExplanation(stagingCard.getExplanation());
            flashcard.setOrderIndex(nextOrderIndex);
            nextOrderIndex += 1;
            flashcards.add(flashcard);
            stagingCard.setStatus(STATUS_APPROVED);
        }

        List<FlashcardCard> savedFlashcards = flashcardCardRepository.saveAll(flashcards);
        stagingCardRepository.saveAll(cards);
        markFullyApprovedBatches(cards, actor);

        return new ApproveStagingCardsResponse(
                savedFlashcards.size(),
                savedFlashcards.stream().map(FlashcardCard::getId).toList()
        );
    }

    private List<FlashcardStagingCard> resolveCardsForApproval(UUID setId, List<UUID> requestedIds) {
        assertNoDuplicates(requestedIds, "Approval request contains duplicate staging card ids");
        Map<UUID, FlashcardStagingCard> cardsById = stagingCardRepository.findByIdIn(requestedIds).stream()
                .collect(Collectors.toMap(FlashcardStagingCard::getId, Function.identity()));
        if (cardsById.size() != requestedIds.size()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "One or more staging cards were not found");
        }

        List<FlashcardStagingCard> cards = new ArrayList<>();
        for (UUID cardId : requestedIds) {
            FlashcardStagingCard card = cardsById.get(cardId);
            if (!setId.equals(card.getBatch().getFlashcardSet().getId())) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST, "Staging card must belong to the selected flashcard set");
            }
            requireDraftCard(card, "Only draft staging cards can be approved");
            cards.add(card);
        }
        return cards;
    }

    private void assertNoDuplicateApproval(UUID setId, List<FlashcardStagingCard> cards) {
        List<FlashcardCard> existingCards = flashcardCardRepository.findActiveBySetIdOrderByOrderIndex(setId);
        Set<String> existingKeys = (existingCards == null ? List.<FlashcardCard>of() : existingCards)
                .stream()
                .map(card -> duplicateKey(card.getFrontText(), card.getBackText()))
                .filter(this::hasDuplicateKey)
                .collect(Collectors.toSet());
        Set<String> approvalKeys = new HashSet<>();
        for (FlashcardStagingCard card : cards) {
            String duplicateKey = duplicateKey(card.getFrontText(), card.getBackText());
            if (!hasDuplicateKey(duplicateKey)) {
                continue;
            }
            if (existingKeys.contains(duplicateKey)) {
                throw new BusinessException(
                        ErrorCode.INVALID_REQUEST,
                        "Duplicate staging card matches an existing Current Flashcard"
                );
            }
            if (!approvalKeys.add(duplicateKey)) {
                throw new BusinessException(
                        ErrorCode.INVALID_REQUEST,
                        "Duplicate staging cards cannot be approved together"
                );
            }
        }
    }

    private void markFullyApprovedBatches(List<FlashcardStagingCard> cards, UserAccount actor) {
        Instant now = Instant.now();
        Map<UUID, FlashcardStagingBatch> batchesById = new LinkedHashMap<>();
        for (FlashcardStagingCard card : cards) {
            batchesById.put(card.getBatch().getId(), card.getBatch());
        }
        List<FlashcardStagingBatch> approvedBatches = new ArrayList<>();
        for (FlashcardStagingBatch batch : batchesById.values()) {
            if (stagingCardRepository.countByBatchIdAndStatus(batch.getId(), STATUS_DRAFT) == 0) {
                batch.setStatus(STATUS_APPROVED);
                batch.setApprovedAt(now);
                batch.setApprovedBy(actor);
                approvedBatches.add(batch);
            }
        }
        if (!approvedBatches.isEmpty()) {
            stagingBatchRepository.saveAll(approvedBatches);
        }
    }

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
                request.difficulty(),
                request.generationMode()
        );
        return new TextGenerationInput(
                sourceText,
                options.desiredCount(),
                options.language(),
                options.difficulty(),
                options.generationMode()
        );
    }

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
        return new DocumentFileInput(originalFileName, extension);
    }

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

    private GenerationOptions validateGenerationOptions(
            Integer desiredCountValue,
            String languageValue,
            String difficultyValue,
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
            language = "en";
        }

        String difficulty = normalizeNullable(difficultyValue);
        if (difficulty != null) {
            difficulty = difficulty.toLowerCase(Locale.ROOT);
            if (!ALLOWED_DIFFICULTIES.contains(difficulty)) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST, "difficulty must be easy, medium, or hard");
            }
        }

        String generationMode = normalizeNullable(generationModeValue);
        if (generationMode == null) {
            generationMode = SOURCE_TYPE_AI;
        }
        generationMode = generationMode.replace('-', '_').toUpperCase(Locale.ROOT);
        if (!ALLOWED_GENERATION_MODES.contains(generationMode)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "generationMode must be AI or RULE_BASED");
        }

        return new GenerationOptions(desiredCount, language, difficulty, generationMode);
    }

    private String validateExtractedText(String value) {
        return validateCleanedGenerationText(
                value,
                "Uploaded file did not contain enough text to generate flashcards"
        );
    }

    private String validateCleanedGenerationText(String value, String message) {
        String cleanedText = normalizeExtractedText(value);
        if (cleanedText.length() < MIN_SOURCE_TEXT_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, message);
        }
        return trimToMaxSourceText(cleanedText);
    }

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
            String duplicateKey = duplicateKey(frontText, backText);
            if (!seen.add(duplicateKey)) {
                continue;
            }
            valid.add(new GeneratedFlashcardCandidate(
                    frontText,
                    backText,
                    normalizeNullable(candidate.explanation()),
                    normalizeNullable(candidate.sourceExcerpt())
            ));
            if (valid.size() >= desiredCount) {
                break;
            }
        }
        return valid;
    }

    private String resolveGeneratedSourceType(GenerationResult result) {
        String sourceType = normalizeNullable(result == null ? null : result.sourceType());
        if (sourceType != null && SOURCE_TYPE_AI.equals(sourceType.toUpperCase(Locale.ROOT))) {
            return SOURCE_TYPE_AI;
        }
        return SOURCE_TYPE_TEXT;
    }

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

    private String resolveTranscriptSourceName(TranscriptTextExtractionResult extraction) {
        String sourceName = normalizeNullable(extraction == null ? null : extraction.sourceName());
        return sourceName == null ? SOURCE_NAME_VIDEO_TRANSCRIPT_GENERATION : sourceName;
    }

    private String duplicateKey(String frontText, String backText) {
        return normalizeForDuplicate(frontText) + "\n" + normalizeForDuplicate(backText);
    }

    private boolean hasDuplicateKey(String duplicateKey) {
        return duplicateKey != null && !duplicateKey.trim().isEmpty();
    }

    private String normalizeForDuplicate(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

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

    private String extractFileExtension(String fileName) {
        int extensionIndex = fileName.lastIndexOf('.');
        if (extensionIndex < 0 || extensionIndex == fileName.length() - 1) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Uploaded file must be a DOCX or PDF file");
        }
        return fileName.substring(extensionIndex + 1).toLowerCase(Locale.ROOT);
    }

    private String extractTranscriptFileExtension(String fileName) {
        int extensionIndex = fileName.lastIndexOf('.');
        if (extensionIndex < 0 || extensionIndex == fileName.length() - 1) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Uploaded transcript file must be an SRT or VTT file");
        }
        return fileName.substring(extensionIndex + 1).toLowerCase(Locale.ROOT);
    }

    private String normalizeExtractedText(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace('\u00a0', ' ');
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

    private SetContext resolveSetContext(UUID setId) {
        FlashcardSet flashcardSet = flashcardSetRepository.findByIdAndDeletedAtIsNull(setId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Flashcard set was not found"));
        Lesson lesson = flashcardSet.getLesson();
        if (lesson == null || lesson.getCourse() == null || lesson.getCourse().getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Flashcard lesson was not found");
        }
        if (lesson.getType() != LessonType.FLASHCARD) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Flashcard set is not linked to a flashcard lesson");
        }
        return new SetContext(flashcardSet, lesson, lesson.getCourse());
    }

    private FlashcardStagingCard findStagingCard(UUID stagingCardId) {
        return stagingCardRepository.findById(stagingCardId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Flashcard staging card was not found"));
    }

    private List<Question> loadQuestionsInRequestOrder(List<UUID> questionIds) {
        Map<UUID, Question> questionsById = questionRepository.findAllById(questionIds).stream()
                .collect(Collectors.toMap(Question::getId, Function.identity()));
        List<Question> questions = new ArrayList<>();
        for (UUID questionId : questionIds) {
            Question question = questionsById.get(questionId);
            if (question != null) {
                questions.add(question);
            }
        }
        return questions;
    }

    private Map<UUID, List<QuestionAnswer>> answersByQuestionId(List<Question> questions) {
        List<UUID> questionIds = questions.stream().map(Question::getId).toList();
        if (questionIds.isEmpty()) {
            return Map.of();
        }
        return questionAnswerRepository.findByQuestionIdInOrderByQuestionIdAscOrderIndexAsc(questionIds).stream()
                .collect(Collectors.groupingBy(QuestionAnswer::getQuestionId, LinkedHashMap::new, Collectors.toList()));
    }

    private Map<UUID, String> bankNames(Set<UUID> bankIds) {
        if (bankIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> names = new HashMap<>();
        questionBankRepository.findAllById(bankIds).forEach(bank -> names.put(bank.getId(), bank.getName()));
        return names;
    }

    private String sourceName(Map<UUID, String> bankNames) {
        List<String> names = bankNames.values().stream()
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .sorted()
                .toList();
        if (names.size() == 1) {
            return names.get(0);
        }
        if (names.size() > 1) {
            return "Question Bank Import (" + names.size() + " banks)";
        }
        return "Question Bank Import";
    }

    private String buildFrontText(Question question, List<QuestionAnswer> answers) {
        String questionText = normalizeRequired(question.getQuestionText(), "Question text is required");
        if (!hasOptions(question, answers)) {
            return questionText;
        }
        List<String> options = orderedAnswers(answers).stream()
                .map(QuestionAnswer::getAnswerText)
                .map(this::normalizeNullable)
                .filter(value -> value != null)
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

    private boolean hasOptions(Question question, List<QuestionAnswer> answers) {
        if (answers == null || answers.isEmpty()) {
            return false;
        }
        QuestionType type = question.getQuestionType();
        return type == QuestionType.SINGLE_CHOICE
                || type == QuestionType.MULTIPLE_CHOICE
                || type == QuestionType.TRUE_FALSE;
    }

    private List<QuestionAnswer> orderedAnswers(List<QuestionAnswer> answers) {
        if (answers == null || answers.isEmpty()) {
            return List.of();
        }
        return answers.stream()
                .sorted(Comparator.comparing(answer -> answer.getOrderIndex() == null ? 0 : answer.getOrderIndex()))
                .toList();
    }

    private String buildBackText(List<QuestionAnswer> answers) {
        List<String> correctAnswers = orderedAnswers(answers).stream()
                .filter(answer -> Boolean.TRUE.equals(answer.getIsCorrect()))
                .map(QuestionAnswer::getAnswerText)
                .map(this::normalizeNullable)
                .filter(value -> value != null)
                .toList();
        if (correctAnswers.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Question must have at least one correct answer");
        }
        return String.join("\n", correctAnswers);
    }

    private void applyUpdate(FlashcardStagingCard card, UpdateStagingCardRequest request) {
        if (request.frontText() != null) {
            card.setFrontText(normalizeNullable(request.frontText()));
        }
        if (request.backText() != null) {
            card.setBackText(normalizeNullable(request.backText()));
        }
        if (request.frontImageUrl() != null) {
            card.setFrontImageUrl(normalizeNullable(request.frontImageUrl()));
        }
        if (request.backImageUrl() != null) {
            card.setBackImageUrl(normalizeNullable(request.backImageUrl()));
        }
        if (request.hint() != null) {
            card.setHint(normalizeNullable(request.hint()));
        }
        if (request.explanation() != null) {
            card.setExplanation(normalizeNullable(request.explanation()));
        }
        if (request.sortOrder() != null) {
            card.setSortOrder(request.sortOrder());
        }
    }

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

    private void requireDraftCard(FlashcardStagingCard card, String message) {
        if (!STATUS_DRAFT.equals(card.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, message);
        }
    }

    private void assertNoDuplicates(List<UUID> ids, String message) {
        Set<UUID> uniqueIds = new HashSet<>(ids);
        if (uniqueIds.size() != ids.size()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, message);
        }
    }

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

    private QuestionStatus parseQuestionStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String normalized = status.trim().replace('-', '_').toUpperCase(Locale.ROOT);
        try {
            return QuestionStatus.valueOf(normalized);
        }
        catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Question status is invalid");
        }
    }

    private SourceQuestionResponse toSourceQuestionResponse(
            Question question,
            String bankName,
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
                question.getQuestionBankId(),
                bankName,
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

    private StagingBatchResponse toBatchResponse(FlashcardStagingBatch batch, List<FlashcardStagingCard> cards) {
        return new StagingBatchResponse(
                batch.getId(),
                batch.getFlashcardSet().getId(),
                batch.getLesson().getId(),
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

    private String toApiValue(Enum<?> value) {
        return value == null ? null : value.name().toLowerCase(Locale.ROOT);
    }

    private String normalizeRequired(String value, String message) {
        String normalized = normalizeNullable(value);
        if (normalized == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, message);
        }
        return normalized;
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record SetContext(FlashcardSet flashcardSet, Lesson lesson, Course course) {
    }

    private record TextGenerationInput(
            String sourceText,
            int desiredCount,
            String language,
            String difficulty,
            String generationMode
    ) {
    }

    private record GenerationOptions(
            int desiredCount,
            String language,
            String difficulty,
            String generationMode
    ) {
    }

    private record DocumentFileInput(String originalFileName, String extension) {
    }
}
