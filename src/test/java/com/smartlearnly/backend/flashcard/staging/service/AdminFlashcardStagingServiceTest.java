package com.smartlearnly.backend.flashcard.staging.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.smartlearnly.backend.flashcard.staging.dto.AdminFlashcardStagingDtos.SourceQuestionResponse;
import com.smartlearnly.backend.flashcard.staging.dto.AdminFlashcardStagingDtos.StagingBatchResponse;
import com.smartlearnly.backend.flashcard.staging.dto.AdminFlashcardStagingDtos.UpdateStagingCardRequest;
import com.smartlearnly.backend.flashcard.staging.entity.FlashcardStagingBatch;
import com.smartlearnly.backend.flashcard.staging.entity.FlashcardStagingCard;
import com.smartlearnly.backend.flashcard.staging.repository.FlashcardStagingBatchRepository;
import com.smartlearnly.backend.flashcard.staging.repository.FlashcardStagingCardRepository;
import com.smartlearnly.backend.flashcard.staging.service.FlashcardDocumentTextExtractionService.DocumentTextExtractionResult;
import com.smartlearnly.backend.flashcard.staging.service.FlashcardTextGenerationService.GeneratedFlashcardCandidate;
import com.smartlearnly.backend.flashcard.staging.service.FlashcardTextGenerationService.GenerationResult;
import com.smartlearnly.backend.flashcard.staging.service.FlashcardTranscriptTextExtractionService.TranscriptTextExtractionResult;
import com.smartlearnly.backend.learning.lesson.entity.Lesson;
import com.smartlearnly.backend.learning.lesson.entity.LessonStatus;
import com.smartlearnly.backend.learning.lesson.entity.LessonType;
import com.smartlearnly.backend.learning.module.entity.CourseSection;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class AdminFlashcardStagingServiceTest {
    @Mock
    private FlashcardSetRepository flashcardSetRepository;
    @Mock
    private FlashcardCardRepository flashcardCardRepository;
    @Mock
    private FlashcardStagingBatchRepository stagingBatchRepository;
    @Mock
    private FlashcardStagingCardRepository stagingCardRepository;
    @Mock
    private QuestionRepository questionRepository;
    @Mock
    private QuestionAnswerRepository questionAnswerRepository;
    @Mock
    private QuestionBankRepository questionBankRepository;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private FlashcardTextGenerationService flashcardTextGenerationService;
    @Mock
    private FlashcardDocumentTextExtractionService documentTextExtractionService;
    @Mock
    private FlashcardTranscriptTextExtractionService transcriptTextExtractionService;

    private AdminFlashcardStagingService service;

    @BeforeEach
    void setUp() {
        service = new AdminFlashcardStagingService(
                flashcardSetRepository,
                flashcardCardRepository,
                stagingBatchRepository,
                stagingCardRepository,
                questionRepository,
                questionAnswerRepository,
                questionBankRepository,
                currentUserService,
                flashcardTextGenerationService,
                documentTextExtractionService,
                transcriptTextExtractionService
        );
    }

    @Test
    void importSelectedQuestionBankQuestionsCreatesOneBatchAndStagingCards() {
        FlashcardSet flashcardSet = flashcardSet();
        UserAccount actor = actor();
        QuestionBank bank = bank(flashcardSet.getLesson().getCourse().getId(), "Bank A");
        Question firstQuestion = question(flashcardSet.getLesson().getCourse().getId(), bank.getId(), "Question 1");
        Question secondQuestion = question(flashcardSet.getLesson().getCourse().getId(), bank.getId(), "Question 2");
        QuestionAnswer firstAnswer = answer(firstQuestion.getId(), "Correct 1", true, 0);
        QuestionAnswer secondAnswer = answer(secondQuestion.getId(), "Correct 2", true, 0);
        when(flashcardSetRepository.findByIdAndDeletedAtIsNull(flashcardSet.getId())).thenReturn(Optional.of(flashcardSet));
        when(currentUserService.requireAuthenticatedUser()).thenReturn(actor);
        when(questionRepository.findAllById(List.of(firstQuestion.getId(), secondQuestion.getId())))
                .thenReturn(List.of(firstQuestion, secondQuestion));
        when(questionAnswerRepository.findByQuestionIdInOrderByQuestionIdAscOrderIndexAsc(List.of(firstQuestion.getId(), secondQuestion.getId())))
                .thenReturn(List.of(firstAnswer, secondAnswer));
        when(questionBankRepository.findAllById(any())).thenReturn(List.of(bank));
        when(stagingBatchRepository.save(any(FlashcardStagingBatch.class))).thenAnswer(invocation -> {
            FlashcardStagingBatch batch = invocation.getArgument(0);
            batch.setId(UUID.randomUUID());
            return batch;
        });
        when(stagingCardRepository.saveAll(anyList())).thenAnswer(invocation -> {
            List<FlashcardStagingCard> cards = invocation.getArgument(0);
            cards.forEach(card -> card.setId(UUID.randomUUID()));
            return cards;
        });

        StagingBatchResponse response = service.importQuestionBank(
                flashcardSet.getId(),
                new ImportQuestionBankRequest(List.of(firstQuestion.getId(), secondQuestion.getId()))
        );

        assertThat(response.sourceType()).isEqualTo("QUESTION_BANK");
        assertThat(response.status()).isEqualTo("draft");
        assertThat(response.sourceName()).isEqualTo("Bank A");
        assertThat(response.cards()).hasSize(2);
        assertThat(response.cards()).extracting(card -> card.frontText()).containsExactly("Question 1", "Question 2");
        assertThat(response.cards()).extracting(card -> card.backText())
                .containsExactly("Correct answer(s):\nCorrect 1", "Correct answer(s):\nCorrect 2");
        ArgumentCaptor<FlashcardStagingBatch> batchCaptor = ArgumentCaptor.forClass(FlashcardStagingBatch.class);
        verify(stagingBatchRepository).save(batchCaptor.capture());
        assertThat(batchCaptor.getValue().getCreatedBy()).isSameAs(actor);
        assertThat(batchCaptor.getValue().getFlashcardSet()).isSameAs(flashcardSet);
    }

    @Test
    void generateFromTextCreatesOneDraftBatchAndStagingCards() {
        FlashcardSet flashcardSet = flashcardSet();
        UserAccount actor = actor();
        when(flashcardSetRepository.findByIdAndDeletedAtIsNull(flashcardSet.getId())).thenReturn(Optional.of(flashcardSet));
        when(currentUserService.requireAuthenticatedUser()).thenReturn(actor);
        when(flashcardTextGenerationService.generate(any())).thenReturn(new GenerationResult(
                "TEXT",
                List.of(
                        candidate("Front 1", "Back 1", "Excerpt 1"),
                        candidate("Front 2", "Back 2", "Excerpt 2")
                )
        ));
        when(stagingBatchRepository.save(any(FlashcardStagingBatch.class))).thenAnswer(invocation -> {
            FlashcardStagingBatch batch = invocation.getArgument(0);
            batch.setId(UUID.randomUUID());
            return batch;
        });
        when(stagingCardRepository.saveAll(anyList())).thenAnswer(invocation -> {
            List<FlashcardStagingCard> cards = invocation.getArgument(0);
            cards.forEach(card -> card.setId(UUID.randomUUID()));
            return cards;
        });

        StagingBatchResponse response = service.generateFromText(
                flashcardSet.getId(),
                new GenerateFromTextRequest(longSourceText(), 2, null, "medium", "AI")
        );

        assertThat(response.sourceType()).isEqualTo("TEXT");
        assertThat(response.status()).isEqualTo("draft");
        assertThat(response.sourceName()).isEqualTo("Pasted Text Generation");
        assertThat(response.cards()).hasSize(2);
        assertThat(response.cards()).extracting(card -> card.frontText()).containsExactly("Front 1", "Front 2");
        assertThat(response.cards()).extracting(card -> card.backText()).containsExactly("Back 1", "Back 2");
        ArgumentCaptor<FlashcardStagingBatch> batchCaptor = ArgumentCaptor.forClass(FlashcardStagingBatch.class);
        verify(stagingBatchRepository).save(batchCaptor.capture());
        assertThat(batchCaptor.getValue().getCreatedBy()).isSameAs(actor);
        assertThat(batchCaptor.getValue().getFlashcardSet()).isSameAs(flashcardSet);
        assertThat(batchCaptor.getValue().getSourceType()).isEqualTo("TEXT");
        assertThat(batchCaptor.getValue().getSourceName()).isEqualTo("Pasted Text Generation");
        ArgumentCaptor<List<FlashcardStagingCard>> cardsCaptor = ArgumentCaptor.forClass(List.class);
        verify(stagingCardRepository).saveAll(cardsCaptor.capture());
        assertThat(cardsCaptor.getValue()).extracting(FlashcardStagingCard::getSourceQuestionId)
                .containsExactly(null, null);
        assertThat(cardsCaptor.getValue()).extracting(FlashcardStagingCard::getStatus)
                .containsExactly("draft", "draft");
        assertThat(cardsCaptor.getValue()).extracting(FlashcardStagingCard::getSortOrder)
                .containsExactly(0, 1);
        verify(flashcardCardRepository, never()).saveAll(anyList());
    }

    @Test
    void generateFromTextRejectsBlankSourceText() {
        FlashcardSet flashcardSet = flashcardSet();
        when(flashcardSetRepository.findByIdAndDeletedAtIsNull(flashcardSet.getId())).thenReturn(Optional.of(flashcardSet));

        assertThatThrownBy(() -> service.generateFromText(
                flashcardSet.getId(),
                new GenerateFromTextRequest("   ", null, null, null, null)
        ))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));

        verify(flashcardTextGenerationService, never()).generate(any());
        verify(stagingBatchRepository, never()).save(any());
        verify(stagingCardRepository, never()).saveAll(anyList());
    }

    @Test
    void generateFromTextRejectsSourceTextShorterThanMinimum() {
        FlashcardSet flashcardSet = flashcardSet();
        when(flashcardSetRepository.findByIdAndDeletedAtIsNull(flashcardSet.getId())).thenReturn(Optional.of(flashcardSet));

        assertThatThrownBy(() -> service.generateFromText(
                flashcardSet.getId(),
                new GenerateFromTextRequest("This source text is intentionally too short.", null, null, null, null)
        ))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));

        verify(flashcardTextGenerationService, never()).generate(any());
        verify(stagingBatchRepository, never()).save(any());
        verify(stagingCardRepository, never()).saveAll(anyList());
    }

    @Test
    void generateFromTextEnforcesDesiredCountMax() {
        FlashcardSet flashcardSet = flashcardSet();
        when(flashcardSetRepository.findByIdAndDeletedAtIsNull(flashcardSet.getId())).thenReturn(Optional.of(flashcardSet));

        assertThatThrownBy(() -> service.generateFromText(
                flashcardSet.getId(),
                new GenerateFromTextRequest(longSourceText(), 31, null, null, null)
        ))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));

        verify(flashcardTextGenerationService, never()).generate(any());
        verify(stagingBatchRepository, never()).save(any());
        verify(stagingCardRepository, never()).saveAll(anyList());
    }

    @Test
    void generateFromTextRejectsEmptyGeneratedResult() {
        FlashcardSet flashcardSet = flashcardSet();
        when(flashcardSetRepository.findByIdAndDeletedAtIsNull(flashcardSet.getId())).thenReturn(Optional.of(flashcardSet));
        when(currentUserService.requireAuthenticatedUser()).thenReturn(actor());
        when(flashcardTextGenerationService.generate(any())).thenReturn(new GenerationResult("TEXT", List.of()));

        assertThatThrownBy(() -> service.generateFromText(
                flashcardSet.getId(),
                new GenerateFromTextRequest(longSourceText(), 10, null, null, null)
        ))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));

        verify(stagingBatchRepository, never()).save(any());
        verify(stagingCardRepository, never()).saveAll(anyList());
    }

    @Test
    void generateFromTextDeduplicatesGeneratedCandidates() {
        FlashcardSet flashcardSet = flashcardSet();
        when(flashcardSetRepository.findByIdAndDeletedAtIsNull(flashcardSet.getId())).thenReturn(Optional.of(flashcardSet));
        when(currentUserService.requireAuthenticatedUser()).thenReturn(actor());
        when(flashcardTextGenerationService.generate(any())).thenReturn(new GenerationResult(
                "TEXT",
                List.of(
                        candidate("  Duplicate front  ", "Duplicate back", "Excerpt 1"),
                        candidate("duplicate FRONT", " duplicate   back ", "Excerpt 2"),
                        candidate("Unique front", "Unique back", "Excerpt 3")
                )
        ));
        when(stagingBatchRepository.save(any(FlashcardStagingBatch.class))).thenAnswer(invocation -> {
            FlashcardStagingBatch batch = invocation.getArgument(0);
            batch.setId(UUID.randomUUID());
            return batch;
        });
        when(stagingCardRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        StagingBatchResponse response = service.generateFromText(
                flashcardSet.getId(),
                new GenerateFromTextRequest(longSourceText(), 10, null, null, null)
        );

        assertThat(response.cards()).hasSize(2);
        assertThat(response.cards()).extracting(card -> card.frontText())
                .containsExactly("Duplicate front", "Unique front");
        ArgumentCaptor<List<FlashcardStagingCard>> cardsCaptor = ArgumentCaptor.forClass(List.class);
        verify(stagingCardRepository).saveAll(cardsCaptor.capture());
        assertThat(cardsCaptor.getValue()).extracting(FlashcardStagingCard::getSortOrder)
                .containsExactly(0, 1);
    }

    @Test
    void generateFromFileWithPdfExtractedTextCreatesDraftBatchAndCards() {
        FlashcardSet flashcardSet = flashcardSet();
        UserAccount actor = actor();
        MockMultipartFile file = pdfFile();
        when(flashcardSetRepository.findByIdAndDeletedAtIsNull(flashcardSet.getId())).thenReturn(Optional.of(flashcardSet));
        when(currentUserService.requireAuthenticatedUser()).thenReturn(actor);
        when(documentTextExtractionService.extract(file)).thenReturn(new DocumentTextExtractionResult(
                "PDF",
                "lesson.pdf",
                longSourceText()
        ));
        when(flashcardTextGenerationService.generate(any())).thenReturn(new GenerationResult(
                "TEXT",
                List.of(candidate("PDF front", "PDF back", "PDF excerpt"))
        ));
        when(stagingBatchRepository.save(any(FlashcardStagingBatch.class))).thenAnswer(invocation -> {
            FlashcardStagingBatch batch = invocation.getArgument(0);
            batch.setId(UUID.randomUUID());
            return batch;
        });
        when(stagingCardRepository.saveAll(anyList())).thenAnswer(invocation -> {
            List<FlashcardStagingCard> cards = invocation.getArgument(0);
            cards.forEach(card -> card.setId(UUID.randomUUID()));
            return cards;
        });

        StagingBatchResponse response = service.generateFromFile(
                flashcardSet.getId(),
                file,
                1,
                null,
                "hard",
                "RULE_BASED"
        );

        assertThat(response.sourceType()).isEqualTo("PDF");
        assertThat(response.sourceName()).isEqualTo("lesson.pdf");
        assertThat(response.cards()).hasSize(1);
        assertThat(response.cards().get(0).sourceQuestionId()).isNull();
        assertThat(response.cards().get(0).frontText()).isEqualTo("PDF front");
        ArgumentCaptor<FlashcardStagingBatch> batchCaptor = ArgumentCaptor.forClass(FlashcardStagingBatch.class);
        verify(stagingBatchRepository).save(batchCaptor.capture());
        assertThat(batchCaptor.getValue().getSourceType()).isEqualTo("PDF");
        assertThat(batchCaptor.getValue().getCreatedBy()).isSameAs(actor);
        verify(flashcardCardRepository, never()).saveAll(anyList());
    }

    @Test
    void generateFromFileWithDocxExtractedTextUsesDocxSourceType() {
        FlashcardSet flashcardSet = flashcardSet();
        MockMultipartFile file = docxFile();
        when(flashcardSetRepository.findByIdAndDeletedAtIsNull(flashcardSet.getId())).thenReturn(Optional.of(flashcardSet));
        when(currentUserService.requireAuthenticatedUser()).thenReturn(actor());
        when(documentTextExtractionService.extract(file)).thenReturn(new DocumentTextExtractionResult(
                "DOCX",
                "lesson.docx",
                longSourceText()
        ));
        when(flashcardTextGenerationService.generate(any())).thenReturn(new GenerationResult(
                "TEXT",
                List.of(candidate("DOCX front", "DOCX back", "DOCX excerpt"))
        ));
        when(stagingBatchRepository.save(any(FlashcardStagingBatch.class))).thenAnswer(invocation -> {
            FlashcardStagingBatch batch = invocation.getArgument(0);
            batch.setId(UUID.randomUUID());
            return batch;
        });
        when(stagingCardRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        StagingBatchResponse response = service.generateFromFile(
                flashcardSet.getId(),
                file,
                null,
                null,
                null,
                null
        );

        assertThat(response.sourceType()).isEqualTo("DOCX");
        assertThat(response.sourceName()).isEqualTo("lesson.docx");
        assertThat(response.cards()).hasSize(1);
        verify(flashcardCardRepository, never()).saveAll(anyList());
    }

    @Test
    void generateFromFileRejectsUnsupportedExtensionAndContentType() {
        FlashcardSet flashcardSet = flashcardSet();
        when(flashcardSetRepository.findByIdAndDeletedAtIsNull(flashcardSet.getId())).thenReturn(Optional.of(flashcardSet));

        assertThatThrownBy(() -> service.generateFromFile(
                flashcardSet.getId(),
                new MockMultipartFile("file", "lesson.txt", "text/plain", "content".getBytes()),
                null,
                null,
                null,
                null
        ))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));

        assertThatThrownBy(() -> service.generateFromFile(
                flashcardSet.getId(),
                new MockMultipartFile("file", "lesson.pdf", "text/plain", "content".getBytes()),
                null,
                null,
                null,
                null
        ))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));

        verify(documentTextExtractionService, never()).extract(any());
        verify(stagingBatchRepository, never()).save(any());
    }

    @Test
    void generateFromFileRejectsEmptyFile() {
        FlashcardSet flashcardSet = flashcardSet();
        when(flashcardSetRepository.findByIdAndDeletedAtIsNull(flashcardSet.getId())).thenReturn(Optional.of(flashcardSet));

        assertThatThrownBy(() -> service.generateFromFile(
                flashcardSet.getId(),
                new MockMultipartFile("file", "lesson.pdf", "application/pdf", new byte[0]),
                null,
                null,
                null,
                null
        ))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));

        verify(documentTextExtractionService, never()).extract(any());
        verify(stagingBatchRepository, never()).save(any());
    }

    @Test
    void generateFromFileRejectsExtractedTextShorterThanMinimum() {
        FlashcardSet flashcardSet = flashcardSet();
        MockMultipartFile file = pdfFile();
        when(flashcardSetRepository.findByIdAndDeletedAtIsNull(flashcardSet.getId())).thenReturn(Optional.of(flashcardSet));
        when(currentUserService.requireAuthenticatedUser()).thenReturn(actor());
        when(documentTextExtractionService.extract(file)).thenReturn(new DocumentTextExtractionResult(
                "PDF",
                "lesson.pdf",
                "Too short."
        ));

        assertThatThrownBy(() -> service.generateFromFile(
                flashcardSet.getId(),
                file,
                null,
                null,
                null,
                null
        ))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));

        verify(flashcardTextGenerationService, never()).generate(any());
        verify(stagingBatchRepository, never()).save(any());
    }

    @Test
    void generateFromFileEnforcesDesiredCountMax() {
        FlashcardSet flashcardSet = flashcardSet();
        when(flashcardSetRepository.findByIdAndDeletedAtIsNull(flashcardSet.getId())).thenReturn(Optional.of(flashcardSet));

        assertThatThrownBy(() -> service.generateFromFile(
                flashcardSet.getId(),
                pdfFile(),
                31,
                null,
                null,
                null
        ))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));

        verify(documentTextExtractionService, never()).extract(any());
        verify(stagingBatchRepository, never()).save(any());
    }

    @Test
    void generateFromTranscriptCreatesDraftBatchAndCards() {
        FlashcardSet flashcardSet = flashcardSet();
        UserAccount actor = actor();
        when(flashcardSetRepository.findByIdAndDeletedAtIsNull(flashcardSet.getId())).thenReturn(Optional.of(flashcardSet));
        when(currentUserService.requireAuthenticatedUser()).thenReturn(actor);
        when(transcriptTextExtractionService.extractRaw(any(), any())).thenReturn(new TranscriptTextExtractionResult(
                "Lesson video transcript",
                longSourceText()
        ));
        when(flashcardTextGenerationService.generate(any())).thenReturn(new GenerationResult(
                "TEXT",
                List.of(candidate("Transcript front", "Transcript back", "Transcript excerpt"))
        ));
        when(stagingBatchRepository.save(any(FlashcardStagingBatch.class))).thenAnswer(invocation -> {
            FlashcardStagingBatch batch = invocation.getArgument(0);
            batch.setId(UUID.randomUUID());
            return batch;
        });
        when(stagingCardRepository.saveAll(anyList())).thenAnswer(invocation -> {
            List<FlashcardStagingCard> cards = invocation.getArgument(0);
            cards.forEach(card -> card.setId(UUID.randomUUID()));
            return cards;
        });

        StagingBatchResponse response = service.generateFromTranscript(
                flashcardSet.getId(),
                new GenerateFromTranscriptRequest(
                        transcriptSourceText(),
                        "Lesson video transcript",
                        1,
                        null,
                        "medium",
                        "AI"
                )
        );

        assertThat(response.sourceType()).isEqualTo("VIDEO_TRANSCRIPT");
        assertThat(response.sourceName()).isEqualTo("Lesson video transcript");
        assertThat(response.cards()).hasSize(1);
        assertThat(response.cards().get(0).sourceQuestionId()).isNull();
        assertThat(response.cards().get(0).frontText()).isEqualTo("Transcript front");
        ArgumentCaptor<FlashcardStagingBatch> batchCaptor = ArgumentCaptor.forClass(FlashcardStagingBatch.class);
        verify(stagingBatchRepository).save(batchCaptor.capture());
        assertThat(batchCaptor.getValue().getSourceType()).isEqualTo("VIDEO_TRANSCRIPT");
        assertThat(batchCaptor.getValue().getCreatedBy()).isSameAs(actor);
        verify(flashcardCardRepository, never()).saveAll(anyList());
    }

    @Test
    void generateFromTranscriptRejectsBlankTranscriptText() {
        FlashcardSet flashcardSet = flashcardSet();
        when(flashcardSetRepository.findByIdAndDeletedAtIsNull(flashcardSet.getId())).thenReturn(Optional.of(flashcardSet));

        assertThatThrownBy(() -> service.generateFromTranscript(
                flashcardSet.getId(),
                new GenerateFromTranscriptRequest("   ", null, null, null, null, null)
        ))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));

        verify(flashcardTextGenerationService, never()).generate(any());
        verify(stagingBatchRepository, never()).save(any());
        verify(stagingCardRepository, never()).saveAll(anyList());
    }

    @Test
    void generateFromTranscriptRejectsCleanedTranscriptShorterThanMinimum() {
        FlashcardSet flashcardSet = flashcardSet();
        when(flashcardSetRepository.findByIdAndDeletedAtIsNull(flashcardSet.getId())).thenReturn(Optional.of(flashcardSet));
        when(currentUserService.requireAuthenticatedUser()).thenReturn(actor());
        when(transcriptTextExtractionService.extractRaw(any(), any())).thenReturn(new TranscriptTextExtractionResult(
                null,
                "Too short."
        ));

        assertThatThrownBy(() -> service.generateFromTranscript(
                flashcardSet.getId(),
                new GenerateFromTranscriptRequest(transcriptSourceText(), null, null, null, null, null)
        ))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));

        verify(flashcardTextGenerationService, never()).generate(any());
        verify(stagingBatchRepository, never()).save(any());
    }

    @Test
    void generateFromTranscriptEnforcesDesiredCountMax() {
        FlashcardSet flashcardSet = flashcardSet();
        when(flashcardSetRepository.findByIdAndDeletedAtIsNull(flashcardSet.getId())).thenReturn(Optional.of(flashcardSet));

        assertThatThrownBy(() -> service.generateFromTranscript(
                flashcardSet.getId(),
                new GenerateFromTranscriptRequest(transcriptSourceText(), null, 31, null, null, null)
        ))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));

        verify(transcriptTextExtractionService, never()).extractRaw(any(), any());
        verify(stagingBatchRepository, never()).save(any());
    }

    @Test
    void generateFromTranscriptFileAcceptsSrtAndCreatesVideoTranscriptStaging() {
        FlashcardSet flashcardSet = flashcardSet();
        MockMultipartFile file = srtFile();
        when(flashcardSetRepository.findByIdAndDeletedAtIsNull(flashcardSet.getId())).thenReturn(Optional.of(flashcardSet));
        when(currentUserService.requireAuthenticatedUser()).thenReturn(actor());
        when(transcriptTextExtractionService.extractFile(file)).thenReturn(new TranscriptTextExtractionResult(
                "lesson.srt",
                longSourceText()
        ));
        when(flashcardTextGenerationService.generate(any())).thenReturn(new GenerationResult(
                "TEXT",
                List.of(candidate("SRT front", "SRT back", "SRT excerpt"))
        ));
        when(stagingBatchRepository.save(any(FlashcardStagingBatch.class))).thenAnswer(invocation -> {
            FlashcardStagingBatch batch = invocation.getArgument(0);
            batch.setId(UUID.randomUUID());
            return batch;
        });
        when(stagingCardRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        StagingBatchResponse response = service.generateFromTranscriptFile(
                flashcardSet.getId(),
                file,
                null,
                null,
                null,
                null
        );

        assertThat(response.sourceType()).isEqualTo("VIDEO_TRANSCRIPT");
        assertThat(response.sourceName()).isEqualTo("lesson.srt");
        assertThat(response.cards()).hasSize(1);
    }

    @Test
    void generateFromTranscriptFileAcceptsVttAndCreatesVideoTranscriptStaging() {
        FlashcardSet flashcardSet = flashcardSet();
        MockMultipartFile file = vttFile();
        when(flashcardSetRepository.findByIdAndDeletedAtIsNull(flashcardSet.getId())).thenReturn(Optional.of(flashcardSet));
        when(currentUserService.requireAuthenticatedUser()).thenReturn(actor());
        when(transcriptTextExtractionService.extractFile(file)).thenReturn(new TranscriptTextExtractionResult(
                "lesson.vtt",
                longSourceText()
        ));
        when(flashcardTextGenerationService.generate(any())).thenReturn(new GenerationResult(
                "TEXT",
                List.of(candidate("VTT front", "VTT back", "VTT excerpt"))
        ));
        when(stagingBatchRepository.save(any(FlashcardStagingBatch.class))).thenAnswer(invocation -> {
            FlashcardStagingBatch batch = invocation.getArgument(0);
            batch.setId(UUID.randomUUID());
            return batch;
        });
        when(stagingCardRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        StagingBatchResponse response = service.generateFromTranscriptFile(
                flashcardSet.getId(),
                file,
                null,
                null,
                null,
                null
        );

        assertThat(response.sourceType()).isEqualTo("VIDEO_TRANSCRIPT");
        assertThat(response.sourceName()).isEqualTo("lesson.vtt");
        assertThat(response.cards()).hasSize(1);
    }

    @Test
    void generateFromTranscriptFileRejectsUnsupportedExtension() {
        FlashcardSet flashcardSet = flashcardSet();
        when(flashcardSetRepository.findByIdAndDeletedAtIsNull(flashcardSet.getId())).thenReturn(Optional.of(flashcardSet));

        assertThatThrownBy(() -> service.generateFromTranscriptFile(
                flashcardSet.getId(),
                new MockMultipartFile("file", "lesson.txt", "text/plain", transcriptSourceText().getBytes()),
                null,
                null,
                null,
                null
        ))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));

        verify(transcriptTextExtractionService, never()).extractFile(any());
        verify(stagingBatchRepository, never()).save(any());
    }

    @Test
    void generateFromTranscriptFileRejectsEmptyFile() {
        FlashcardSet flashcardSet = flashcardSet();
        when(flashcardSetRepository.findByIdAndDeletedAtIsNull(flashcardSet.getId())).thenReturn(Optional.of(flashcardSet));

        assertThatThrownBy(() -> service.generateFromTranscriptFile(
                flashcardSet.getId(),
                new MockMultipartFile("file", "lesson.srt", "text/plain", new byte[0]),
                null,
                null,
                null,
                null
        ))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));

        verify(transcriptTextExtractionService, never()).extractFile(any());
        verify(stagingBatchRepository, never()).save(any());
    }

    @Test
    void transcriptCleanerRemovesSrtVttTimestampsHeaderCueNumbersAndTags() {
        DefaultFlashcardTranscriptTextExtractionService extractionService = new DefaultFlashcardTranscriptTextExtractionService();
        String transcript = """
                \uFEFFWEBVTT

                1
                00:00:01,000 --> 00:00:04,000
                <v Trainer>Hello and welcome to the flashcard lesson.</v>

                2
                00:00:04.000 --> 00:00:09.000 align:start position:0%
                <i>We will review core ideas and practice them carefully.</i>
                """;

        TranscriptTextExtractionResult result = extractionService.extractRaw(transcript, "Video");

        assertThat(result.text()).doesNotContain("WEBVTT");
        assertThat(result.text()).doesNotContain("-->");
        assertThat(result.text()).doesNotContain("<i>");
        assertThat(result.text()).doesNotContain("<v Trainer>");
        assertThat(result.text()).contains("Hello and welcome to the flashcard lesson.");
        assertThat(result.text()).contains("We will review core ideas and practice them carefully.");
    }

    @Test
    void importRejectsQuestionFromAnotherCourse() {
        FlashcardSet flashcardSet = flashcardSet();
        Question question = question(UUID.randomUUID(), UUID.randomUUID(), "Wrong course");
        when(flashcardSetRepository.findByIdAndDeletedAtIsNull(flashcardSet.getId())).thenReturn(Optional.of(flashcardSet));
        when(currentUserService.requireAuthenticatedUser()).thenReturn(actor());
        when(questionRepository.findAllById(List.of(question.getId()))).thenReturn(List.of(question));

        assertThatThrownBy(() -> service.importQuestionBank(
                flashcardSet.getId(),
                new ImportQuestionBankRequest(List.of(question.getId()))
        ))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));

        verify(stagingBatchRepository, never()).save(any());
        verify(stagingCardRepository, never()).saveAll(anyList());
    }

    @Test
    void importRejectsAlreadyImportedQuestionBankQuestion() {
        FlashcardSet flashcardSet = flashcardSet();
        Question question = question(
                flashcardSet.getLesson().getCourse().getId(),
                UUID.randomUUID(),
                "Already imported"
        );
        when(flashcardSetRepository.findByIdAndDeletedAtIsNull(flashcardSet.getId())).thenReturn(Optional.of(flashcardSet));
        when(currentUserService.requireAuthenticatedUser()).thenReturn(actor());
        when(questionRepository.findAllById(List.of(question.getId()))).thenReturn(List.of(question));
        when(stagingCardRepository.findImportedSourceQuestionIds(any(), any(), any()))
                .thenReturn(List.of(question.getId()));

        assertThatThrownBy(() -> service.importQuestionBank(
                flashcardSet.getId(),
                new ImportQuestionBankRequest(List.of(question.getId()))
        ))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));

        verify(stagingBatchRepository, never()).save(any());
        verify(stagingCardRepository, never()).saveAll(anyList());
    }

    @Test
    void updateStagingCardValidatesFrontAndBack() {
        FlashcardStagingCard card = stagingCard(stagingBatch(flashcardSet()), "draft", 0);
        when(stagingCardRepository.findById(card.getId())).thenReturn(Optional.of(card));

        assertThatThrownBy(() -> service.updateCard(
                card.getId(),
                new UpdateStagingCardRequest(" ", "Back", null, null, null, null, null)
        ))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));

        verify(stagingCardRepository, never()).save(any());
    }

    @Test
    void rejectStagingCardChangesStatusAndDoesNotCreateRealFlashcard() {
        FlashcardStagingCard card = stagingCard(stagingBatch(flashcardSet()), "draft", 0);
        when(stagingCardRepository.findById(card.getId())).thenReturn(Optional.of(card));

        service.rejectCard(card.getId());

        assertThat(card.getStatus()).isEqualTo("rejected");
        verify(stagingCardRepository).save(card);
        verify(flashcardCardRepository, never()).saveAll(anyList());
    }

    @Test
    void approveSelectedStagingCardsCreatesRealFlashcardsAppendedAfterExistingMaxOrderIndex() {
        FlashcardSet flashcardSet = flashcardSet();
        UserAccount actor = actor();
        FlashcardStagingBatch batch = stagingBatch(flashcardSet);
        FlashcardStagingCard first = stagingCard(batch, "draft", 0);
        FlashcardStagingCard second = stagingCard(batch, "draft", 1);
        when(flashcardSetRepository.findByIdAndDeletedAtIsNull(flashcardSet.getId())).thenReturn(Optional.of(flashcardSet));
        when(currentUserService.requireAuthenticatedUser()).thenReturn(actor);
        when(stagingCardRepository.findByIdIn(List.of(first.getId(), second.getId()))).thenReturn(List.of(second, first));
        when(flashcardCardRepository.findMaxOrderIndexBySetId(flashcardSet.getId())).thenReturn(7);
        when(flashcardCardRepository.saveAll(anyList())).thenAnswer(invocation -> {
            List<FlashcardCard> cards = invocation.getArgument(0);
            cards.forEach(card -> card.setId(UUID.randomUUID()));
            return cards;
        });
        when(stagingCardRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(stagingCardRepository.countByBatchIdAndStatus(batch.getId(), "draft")).thenReturn(0L);

        ApproveStagingCardsResponse response = service.approve(
                flashcardSet.getId(),
                new ApproveStagingCardsRequest(List.of(first.getId(), second.getId()))
        );

        assertThat(response.approvedCount()).isEqualTo(2);
        ArgumentCaptor<List<FlashcardCard>> flashcardsCaptor = ArgumentCaptor.forClass(List.class);
        verify(flashcardCardRepository).saveAll(flashcardsCaptor.capture());
        assertThat(flashcardsCaptor.getValue()).extracting(FlashcardCard::getOrderIndex).containsExactly(8, 9);
        assertThat(flashcardsCaptor.getValue()).extracting(FlashcardCard::getFrontText).containsExactly("Front 0", "Front 1");
        assertThat(first.getStatus()).isEqualTo("approved");
        assertThat(second.getStatus()).isEqualTo("approved");
        assertThat(batch.getStatus()).isEqualTo("approved");
        assertThat(batch.getApprovedBy()).isSameAs(actor);
    }

    @Test
    void approveGeneratedTranscriptStagingCardWithNullSourceQuestionCreatesRealFlashcard() {
        FlashcardSet flashcardSet = flashcardSet();
        FlashcardStagingBatch batch = stagingBatch(flashcardSet);
        batch.setSourceType("VIDEO_TRANSCRIPT");
        batch.setSourceName("lesson.srt");
        FlashcardStagingCard card = stagingCard(batch, "draft", 0);
        card.setSourceQuestionId(null);
        when(flashcardSetRepository.findByIdAndDeletedAtIsNull(flashcardSet.getId())).thenReturn(Optional.of(flashcardSet));
        when(currentUserService.requireAuthenticatedUser()).thenReturn(actor());
        when(stagingCardRepository.findByIdIn(List.of(card.getId()))).thenReturn(List.of(card));
        when(flashcardCardRepository.findMaxOrderIndexBySetId(flashcardSet.getId())).thenReturn(-1);
        when(flashcardCardRepository.saveAll(anyList())).thenAnswer(invocation -> {
            List<FlashcardCard> cards = invocation.getArgument(0);
            cards.forEach(flashcard -> flashcard.setId(UUID.randomUUID()));
            return cards;
        });
        when(stagingCardRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(stagingCardRepository.countByBatchIdAndStatus(batch.getId(), "draft")).thenReturn(0L);

        ApproveStagingCardsResponse response = service.approve(
                flashcardSet.getId(),
                new ApproveStagingCardsRequest(List.of(card.getId()))
        );

        assertThat(response.approvedCount()).isEqualTo(1);
        ArgumentCaptor<List<FlashcardCard>> flashcardsCaptor = ArgumentCaptor.forClass(List.class);
        verify(flashcardCardRepository).saveAll(flashcardsCaptor.capture());
        assertThat(flashcardsCaptor.getValue()).extracting(FlashcardCard::getFrontText).containsExactly("Front 0");
        assertThat(card.getStatus()).isEqualTo("approved");
        assertThat(batch.getStatus()).isEqualTo("approved");
    }

    @Test
    void approveCannotDuplicateAlreadyApprovedStagingCards() {
        FlashcardSet flashcardSet = flashcardSet();
        FlashcardStagingCard card = stagingCard(stagingBatch(flashcardSet), "approved", 0);
        when(flashcardSetRepository.findByIdAndDeletedAtIsNull(flashcardSet.getId())).thenReturn(Optional.of(flashcardSet));
        when(currentUserService.requireAuthenticatedUser()).thenReturn(actor());
        when(stagingCardRepository.findByIdIn(List.of(card.getId()))).thenReturn(List.of(card));

        assertThatThrownBy(() -> service.approve(
                flashcardSet.getId(),
                new ApproveStagingCardsRequest(List.of(card.getId()))
        ))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));

        verify(flashcardCardRepository, never()).saveAll(anyList());
    }

    @Test
    void approveRejectsNullOrEmptyStagingCardIds() {
        FlashcardSet flashcardSet = flashcardSet();
        when(flashcardSetRepository.findByIdAndDeletedAtIsNull(flashcardSet.getId())).thenReturn(Optional.of(flashcardSet));

        assertThatThrownBy(() -> service.approve(flashcardSet.getId(), null))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));

        assertThatThrownBy(() -> service.approve(
                flashcardSet.getId(),
                new ApproveStagingCardsRequest(List.of())
        ))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));

        verify(flashcardCardRepository, never()).saveAll(anyList());
    }

    @Test
    void sourceQuestionsReturnsOnlySameCourseQuestions() {
        FlashcardSet flashcardSet = flashcardSet();
        UUID bankId = UUID.randomUUID();
        Question sameCourseQuestion = question(flashcardSet.getLesson().getCourse().getId(), bankId, "Same course");
        Question otherCourseQuestion = question(UUID.randomUUID(), bankId, "Other course");
        QuestionAnswer answer = answer(sameCourseQuestion.getId(), "Correct", true, 0);
        QuestionBank bank = bank(flashcardSet.getLesson().getCourse().getId(), "Bank A");
        bank.setId(bankId);
        when(flashcardSetRepository.findByIdAndDeletedAtIsNull(flashcardSet.getId())).thenReturn(Optional.of(flashcardSet));
        when(questionRepository.findAll(any(Specification.class), any(Sort.class)))
                .thenReturn(List.of(sameCourseQuestion, otherCourseQuestion));
        when(questionAnswerRepository.findByQuestionIdInOrderByQuestionIdAscOrderIndexAsc(List.of(sameCourseQuestion.getId())))
                .thenReturn(List.of(answer));
        when(questionBankRepository.findAllById(any())).thenReturn(List.of(bank));

        List<SourceQuestionResponse> response = service.listSourceQuestions(
                flashcardSet.getId(),
                null,
                null,
                null,
                null
        );

        assertThat(response).hasSize(1);
        assertThat(response.get(0).questionId()).isEqualTo(sameCourseQuestion.getId());
        assertThat(response.get(0).questionBankName()).isEqualTo("Bank A");
        assertThat(response.get(0).correctAnswers()).containsExactly("Correct");
        assertThat(response.get(0).imported()).isFalse();
    }

    @Test
    void sourceQuestionsMarkDraftAndApprovedStagingSourcesAsImported() {
        FlashcardSet flashcardSet = flashcardSet();
        UUID bankId = UUID.randomUUID();
        Question draftImportedQuestion = question(flashcardSet.getLesson().getCourse().getId(), bankId, "Draft import");
        Question approvedImportedQuestion = question(flashcardSet.getLesson().getCourse().getId(), bankId, "Approved import");
        Question notImportedQuestion = question(flashcardSet.getLesson().getCourse().getId(), bankId, "Not imported");
        when(flashcardSetRepository.findByIdAndDeletedAtIsNull(flashcardSet.getId())).thenReturn(Optional.of(flashcardSet));
        when(questionRepository.findAll(any(Specification.class), any(Sort.class)))
                .thenReturn(List.of(draftImportedQuestion, approvedImportedQuestion, notImportedQuestion));
        when(questionAnswerRepository.findByQuestionIdInOrderByQuestionIdAscOrderIndexAsc(anyList()))
                .thenReturn(List.of());
        when(questionBankRepository.findAllById(any())).thenReturn(List.of());
        when(stagingCardRepository.findImportedSourceQuestionIds(any(), any(), any()))
                .thenReturn(List.of(draftImportedQuestion.getId(), approvedImportedQuestion.getId()));

        List<SourceQuestionResponse> response = service.listSourceQuestions(
                flashcardSet.getId(),
                null,
                null,
                null,
                null
        );

        assertThat(response).extracting(SourceQuestionResponse::imported)
                .containsExactly(true, true, false);
    }

    @Test
    void sourceQuestionsDoNotMarkRejectedStagingSourcesAsImported() {
        FlashcardSet flashcardSet = flashcardSet();
        UUID bankId = UUID.randomUUID();
        Question rejectedQuestion = question(flashcardSet.getLesson().getCourse().getId(), bankId, "Rejected import");
        when(flashcardSetRepository.findByIdAndDeletedAtIsNull(flashcardSet.getId())).thenReturn(Optional.of(flashcardSet));
        when(questionRepository.findAll(any(Specification.class), any(Sort.class)))
                .thenReturn(List.of(rejectedQuestion));
        when(questionAnswerRepository.findByQuestionIdInOrderByQuestionIdAscOrderIndexAsc(List.of(rejectedQuestion.getId())))
                .thenReturn(List.of());
        when(questionBankRepository.findAllById(any())).thenReturn(List.of());
        when(stagingCardRepository.findImportedSourceQuestionIds(any(), any(), any()))
                .thenReturn(List.of());

        List<SourceQuestionResponse> response = service.listSourceQuestions(
                flashcardSet.getId(),
                null,
                null,
                null,
                null
        );

        assertThat(response).hasSize(1);
        assertThat(response.get(0).imported()).isFalse();
    }

    private FlashcardSet flashcardSet() {
        Course course = course();
        CourseSection section = section(course);
        Lesson lesson = lesson(course, section);
        FlashcardSet flashcardSet = new FlashcardSet();
        flashcardSet.setId(UUID.randomUUID());
        flashcardSet.setLesson(lesson);
        flashcardSet.setCourse(course);
        flashcardSet.setTitle("Flashcards");
        flashcardSet.setCreatedAt(Instant.now());
        flashcardSet.setUpdatedAt(Instant.now());
        return flashcardSet;
    }

    private String longSourceText() {
        return """
                Smart Learnly flashcard lessons help trainees review important concepts after a structured lesson. \
                Trainers can paste lesson content, inspect generated draft cards, edit the wording, and approve only \
                the cards that are accurate enough to become real learning material.
                """;
    }

    private String transcriptSourceText() {
        return """
                Welcome to this lesson transcript. The trainer explains how staging flashcards are generated from \
                video captions, then reviewed before publication. The workflow keeps draft cards separate from real \
                flashcards until an admin or trainer approves them.
                """;
    }

    private MockMultipartFile pdfFile() {
        return new MockMultipartFile("file", "lesson.pdf", "application/pdf", "pdf-content".getBytes());
    }

    private MockMultipartFile docxFile() {
        return new MockMultipartFile(
                "file",
                "lesson.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "docx-content".getBytes()
        );
    }

    private MockMultipartFile srtFile() {
        return new MockMultipartFile("file", "lesson.srt", "text/plain", transcriptSourceText().getBytes());
    }

    private MockMultipartFile vttFile() {
        return new MockMultipartFile("file", "lesson.vtt", "text/vtt", transcriptSourceText().getBytes());
    }

    private GeneratedFlashcardCandidate candidate(String frontText, String backText, String sourceExcerpt) {
        return new GeneratedFlashcardCandidate(frontText, backText, "Generated from pasted text.", sourceExcerpt);
    }

    private FlashcardStagingBatch stagingBatch(FlashcardSet flashcardSet) {
        FlashcardStagingBatch batch = new FlashcardStagingBatch();
        batch.setId(UUID.randomUUID());
        batch.setFlashcardSet(flashcardSet);
        batch.setLesson(flashcardSet.getLesson());
        batch.setCourse(flashcardSet.getLesson().getCourse());
        batch.setCreatedBy(actor());
        batch.setSourceType("QUESTION_BANK");
        batch.setStatus("draft");
        batch.setSourceName("Bank A");
        batch.setCreatedAt(Instant.now());
        batch.setUpdatedAt(Instant.now());
        return batch;
    }

    private FlashcardStagingCard stagingCard(FlashcardStagingBatch batch, String status, int sortOrder) {
        FlashcardStagingCard card = new FlashcardStagingCard();
        card.setId(UUID.randomUUID());
        card.setBatch(batch);
        card.setSourceQuestionId(UUID.randomUUID());
        card.setFrontText("Front " + sortOrder);
        card.setBackText("Back " + sortOrder);
        card.setStatus(status);
        card.setSortOrder(sortOrder);
        card.setCreatedAt(Instant.now());
        card.setUpdatedAt(Instant.now());
        return card;
    }

    private Question question(UUID courseId, UUID bankId, String questionText) {
        Question question = new Question();
        question.setId(UUID.randomUUID());
        question.setQuestionBankId(bankId);
        question.setCourseId(courseId);
        question.setQuestionText(questionText);
        question.setQuestionType(QuestionType.MULTIPLE_CHOICE);
        question.setDifficulty((short) 2);
        question.setExplanation(null);
        question.setIsAiGenerated(false);
        question.setStatus(QuestionStatus.APPROVED);
        question.setCreatedAt(Instant.now());
        question.setUpdatedAt(Instant.now());
        return question;
    }

    private QuestionAnswer answer(UUID questionId, String text, boolean correct, int orderIndex) {
        QuestionAnswer answer = new QuestionAnswer();
        answer.setId(UUID.randomUUID());
        answer.setQuestionId(questionId);
        answer.setAnswerText(text);
        answer.setIsCorrect(correct);
        answer.setOrderIndex(orderIndex);
        return answer;
    }

    private QuestionBank bank(UUID courseId, String name) {
        QuestionBank bank = new QuestionBank();
        bank.setId(UUID.randomUUID());
        bank.setCourseId(courseId);
        bank.setName(name);
        bank.setStatus("approved");
        bank.setCreatedAt(Instant.now());
        bank.setUpdatedAt(Instant.now());
        return bank;
    }

    private Course course() {
        Course course = new Course();
        course.setId(UUID.randomUUID());
        course.setTitle("Course");
        course.setSlug("course");
        return course;
    }

    private CourseSection section(Course course) {
        CourseSection section = new CourseSection();
        section.setId(UUID.randomUUID());
        section.setCourse(course);
        section.setTitle("Section");
        section.setSortOrder(0);
        return section;
    }

    private Lesson lesson(Course course, CourseSection section) {
        Lesson lesson = new Lesson();
        lesson.setId(UUID.randomUUID());
        lesson.setCourse(course);
        lesson.setSection(section);
        lesson.setTitle("Flashcards");
        lesson.setType(LessonType.FLASHCARD);
        lesson.setStatus(LessonStatus.DRAFT);
        lesson.setPreview(false);
        lesson.setSortOrder(0);
        return lesson;
    }

    private UserAccount actor() {
        UserAccount actor = new UserAccount();
        actor.setId(UUID.randomUUID());
        actor.setEmail("trainer@smartlearnly.dev");
        actor.setFullName("Trainer");
        actor.setRole("TRAINER");
        return actor;
    }
}
