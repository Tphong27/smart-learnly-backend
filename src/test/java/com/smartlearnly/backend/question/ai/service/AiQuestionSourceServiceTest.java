package com.smartlearnly.backend.question.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.course.access.service.CourseAccessService;
import com.smartlearnly.backend.file.config.StorageProperties;
import com.smartlearnly.backend.file.service.FileStorageService.StoredFile;
import com.smartlearnly.backend.file.service.SupabaseStorageClient;
import com.smartlearnly.backend.flashcard.staging.service.FlashcardDocumentTextExtractionService;
import com.smartlearnly.backend.flashcard.staging.service.FlashcardDocumentTextExtractionService.DocumentTextExtractionResult;
import com.smartlearnly.backend.question.ai.dto.AiQuestionDraftDtos;
import com.smartlearnly.backend.question.ai.entity.AiQuestionGenerationBatch;
import com.smartlearnly.backend.question.ai.entity.AiQuestionGenerationSource;
import com.smartlearnly.backend.question.ai.entity.AiQuestionGenerationSourceChunk;
import com.smartlearnly.backend.question.ai.generation.QuestionGenerationProvider;
import com.smartlearnly.backend.question.ai.repository.AiQuestionGenerationBatchRepository;
import com.smartlearnly.backend.question.ai.repository.AiQuestionGenerationSourceChunkRepository;
import com.smartlearnly.backend.question.ai.repository.AiQuestionGenerationSourceRepository;
import com.smartlearnly.backend.videoai.entity.VideoAiContent;
import com.smartlearnly.backend.videoai.entity.VideoAiTranscriptSegment;
import com.smartlearnly.backend.videoai.repository.VideoAiContentRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
class AiQuestionSourceServiceTest {
    @Mock
    private CourseAccessService courseAccessService;
    @Mock
    private AiQuestionGenerationBatchRepository batchRepository;
    @Mock
    private AiQuestionGenerationSourceRepository sourceRepository;
    @Mock
    private AiQuestionGenerationSourceChunkRepository sourceChunkRepository;
    @Mock
    private StorageProperties storageProperties;
    @Mock
    private SupabaseStorageClient supabaseStorageClient;
    @Mock
    private FlashcardDocumentTextExtractionService documentTextExtractionService;
    @Mock
    private VideoAiContentRepository videoAiContentRepository;

    private final List<AiQuestionGenerationSource> savedSources = new ArrayList<>();
    private final List<AiQuestionGenerationSourceChunk> savedChunks = new ArrayList<>();

    private AiQuestionSourceService service;
    private UUID courseId;
    private UUID batchId;
    private UUID sourceId;

    @BeforeEach
    void setUp() {
        service = new AiQuestionSourceService(
                courseAccessService,
                batchRepository,
                sourceRepository,
                sourceChunkRepository,
                storageProperties,
                supabaseStorageClient,
                documentTextExtractionService,
                videoAiContentRepository
        );
        courseId = UUID.randomUUID();
        batchId = UUID.randomUUID();
        sourceId = UUID.randomUUID();
        installRepositoryFakes();
        lenient().when(storageProperties.getAiQuestionSourceFileMaxSize()).thenReturn(DataSize.ofMegabytes(25));
        lenient().when(storageProperties.getAiQuestionSourceFileBucket()).thenReturn("ai-question-source-files");
    }

    @Test
    void sourceCapabilities_returnsConfiguredLimitsAndRequiresReadableCourse() {
        when(storageProperties.getAiQuestionSourceFileMaxSize()).thenReturn(DataSize.ofMegabytes(25));

        AiQuestionDraftDtos.SourceCapabilitiesResponse response = service.sourceCapabilities(courseId);

        assertThat(response.minTextCharacters()).isEqualTo(100);
        assertThat(response.maxDocumentBytes()).isEqualTo(DataSize.ofMegabytes(25).toBytes());
        assertThat(response.maxSourcesPerBatch()).isEqualTo(8);
        assertThat(response.acceptedDocumentMimeTypes()).contains("application/pdf", "text/plain");
        assertThat(response.acceptedDocumentExtensions()).containsExactly("pdf", "docx", "txt");
        verify(courseAccessService).requireReadableCourse(courseId);
    }

    @Test
    void listSources_returnsOnlyTranscriptsWithEnoughTextAndHandlesMissingSegments() {
        VideoAiContent withSegment = transcript("This transcript has enough useful characters. ".repeat(4));
        VideoAiContent withoutSegments = transcript("Another transcript with enough characters for the AI question source list. ".repeat(3));
        withoutSegments.getSegments().clear();
        withoutSegments.setRevision(null);
        VideoAiContent shortTranscript = transcript("too short");
        when(videoAiContentRepository.findPublishedMasterTranscriptsByCourseId(courseId))
                .thenReturn(List.of(withSegment, withoutSegments, shortTranscript));

        List<AiQuestionDraftDtos.SourceOptionResponse> response = service.listSources(courseId);

        assertThat(response).hasSize(2);
        assertThat(response.get(0).sourceKind()).isEqualTo(AiQuestionGenerationSource.KIND_TRANSCRIPT);
        assertThat(response.get(0).durationSeconds()).isEqualTo(65L);
        assertThat(response.get(1).durationSeconds()).isNull();
        assertThat(response.get(1).version()).isEqualTo("0");
        verify(courseAccessService).requireReadableCourse(courseId);
    }

    @Test
    void sourceDownloadUrl_returnsSignedUrl_whenSourceIsDownloadable() {
        AiQuestionGenerationBatch batch = batch(courseId);
        AiQuestionGenerationSource source = source(batchId, true, "sources/file.txt");
        when(batchRepository.findById(batchId)).thenReturn(Optional.of(batch));
        when(sourceRepository.findById(sourceId)).thenReturn(Optional.of(source));
        when(supabaseStorageClient.createSignedUrl("ai-question-source-files", "sources/file.txt", 300))
                .thenReturn("https://signed.example.com/file.txt");

        AiQuestionDraftDtos.SourceDownloadUrlResponse response =
                service.sourceDownloadUrl(courseId, batchId, sourceId);

        assertThat(response.url()).isEqualTo("https://signed.example.com/file.txt");
        assertThat(response.fileName()).isEqualTo("source.txt");
        assertThat(response.mimeType()).isEqualTo("text/plain");
        assertThat(response.fileSizeBytes()).isEqualTo(123L);
        assertThat(response.expiresAt()).isAfter(Instant.now());
    }

    @Test
    void sourceDownloadUrl_rejectsMissingWrongCourseForeignOrNonDownloadableSources() {
        assertThatThrownBy(() -> service.sourceDownloadUrl(courseId, batchId, sourceId))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));

        AiQuestionGenerationBatch otherCourse = batch(UUID.randomUUID());
        when(batchRepository.findById(batchId)).thenReturn(Optional.of(otherCourse));
        assertThatThrownBy(() -> service.sourceDownloadUrl(courseId, batchId, sourceId))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));

        AiQuestionGenerationBatch batch = batch(courseId);
        when(batchRepository.findById(batchId)).thenReturn(Optional.of(batch));
        when(sourceRepository.findById(sourceId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.sourceDownloadUrl(courseId, batchId, sourceId))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));

        when(sourceRepository.findById(sourceId)).thenReturn(Optional.of(source(UUID.randomUUID(), true, "sources/file.txt")));
        assertThatThrownBy(() -> service.sourceDownloadUrl(courseId, batchId, sourceId))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));

        when(sourceRepository.findById(sourceId)).thenReturn(Optional.of(source(batchId, false, null)));
        assertThatThrownBy(() -> service.sourceDownloadUrl(courseId, batchId, sourceId))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));

        when(sourceRepository.findById(sourceId)).thenReturn(Optional.of(source(batchId, true, " ")));
        assertThatThrownBy(() -> service.sourceDownloadUrl(courseId, batchId, sourceId))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
    }

    @Test
    void persistAndBuildSourceInputs_persistsPastedTextWithDefaultAndCustomNames() {
        AiQuestionGenerationBatch batch = batch(courseId);
        AiQuestionDraftDtos.CreateBatchRequest request = request(
                List.of(),
                List.of(
                        new AiQuestionDraftDtos.PastedTextSourceRequest(null, longText("First pasted source")),
                        new AiQuestionDraftDtos.PastedTextSourceRequest(" Custom note ", longText("Second pasted source"))));

        List<QuestionGenerationProvider.SourceInput> inputs =
                service.persistAndBuildSourceInputs(courseId, batch, request, List.of());

        assertThat(inputs).hasSize(2);
        assertThat(savedSources).extracting(AiQuestionGenerationSource::getSourceKind)
                .containsOnly(AiQuestionGenerationSource.KIND_PASTED_TEXT);
        assertThat(savedSources).extracting(AiQuestionGenerationSource::getSourceName)
                .containsExactly("Pasted text 1", "Custom note");
        assertThat(savedChunks).hasSize(2);
    }

    @Test
    void persistAndBuildSourceInputs_allowsNullCollectionsAndReturnsNoSourceInputs() {
        AiQuestionGenerationBatch batch = batch(courseId);
        AiQuestionDraftDtos.CreateBatchRequest request = new AiQuestionDraftDtos.CreateBatchRequest(
                null,
                null,
                List.of("multiple_choice"),
                1,
                null,
                "vi",
                null,
                "idem");

        List<QuestionGenerationProvider.SourceInput> inputs =
                service.persistAndBuildSourceInputs(courseId, batch, request, null);

        assertThat(inputs).isEmpty();
        assertThat(savedSources).isEmpty();
        assertThat(savedChunks).isEmpty();
    }

    @Test
    void persistAndBuildSourceInputs_mergesShortParagraphsIntoSingleChunk() {
        AiQuestionGenerationBatch batch = batch(courseId);
        String text = longText("first paragraph") + "\n\n" + longText("second paragraph");

        service.persistAndBuildSourceInputs(courseId, batch,
                request(List.of(), List.of(new AiQuestionDraftDtos.PastedTextSourceRequest("Merged", text))),
                List.of());

        assertThat(savedChunks).hasSize(1);
        assertThat(savedChunks.get(0).getContentExcerpt()).contains("\n\n");
    }

    @Test
    void persistAndBuildSourceInputs_splitsLargePastedTextIntoMultipleChunks() {
        AiQuestionGenerationBatch batch = batch(courseId);
        String text = "Paragraph one ".repeat(260) + "\n\n" + "Paragraph two ".repeat(260);

        service.persistAndBuildSourceInputs(courseId, batch,
                request(List.of(), List.of(new AiQuestionDraftDtos.PastedTextSourceRequest("Large", text))),
                List.of());

        assertThat(savedChunks).hasSizeGreaterThanOrEqualTo(2);
        assertThat(savedChunks).extracting(AiQuestionGenerationSourceChunk::getChunkReference)
                .contains("chunk-1", "Large-2");
    }

    @Test
    void persistAndBuildSourceInputs_persistsTxtFileAndStoresAuditCopy() {
        AiQuestionGenerationBatch batch = batch(courseId);
        MockMultipartFile file = new MockMultipartFile(
                "files",
                "folder\\source!.txt",
                "application/octet-stream",
                longText("TXT file").getBytes(StandardCharsets.UTF_8));
        when(supabaseStorageClient.store(eq("ai-question-source-files"), any(), eq("text/plain"), any()))
                .thenAnswer(invocation -> new StoredFile(
                        "https://storage.example/source.txt",
                        invocation.getArgument(1),
                        "source.txt",
                        "text/plain",
                        ((byte[]) invocation.getArgument(3)).length));

        List<QuestionGenerationProvider.SourceInput> inputs =
                service.persistAndBuildSourceInputs(courseId, batch, request(List.of(), List.of()), List.of(file));

        assertThat(inputs).hasSize(1);
        assertThat(savedSources.get(0).getSourceKind()).isEqualTo(AiQuestionGenerationSource.KIND_TEMPORARY_FILE);
        assertThat(savedSources.get(0).getSourceName()).isEqualTo("source!.txt");
        assertThat(savedSources.get(0).getMimeType()).isEqualTo("text/plain");
        assertThat(savedSources.get(0).getDownloadable()).isTrue();
        assertThat(savedSources.get(0).getSourcePayloadRef()).contains(batchId.toString());
        assertThat(savedChunks).hasSize(1);
    }

    @Test
    void persistAndBuildSourceInputs_persistsDocxUsingExtractorAndStoredObjectPathOverride() {
        AiQuestionGenerationBatch batch = batch(courseId);
        MockMultipartFile file = new MockMultipartFile(
                "files",
                "lesson.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "binary".getBytes(StandardCharsets.UTF_8));
        when(documentTextExtractionService.extract(file))
                .thenReturn(new DocumentTextExtractionResult("DOCX", "lesson.docx", longText("Extracted docx")));
        when(supabaseStorageClient.store(eq("ai-question-source-files"), any(), any(), any()))
                .thenReturn(new StoredFile("https://storage.example/override", "override/path.docx", "lesson.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", 6));

        service.persistAndBuildSourceInputs(courseId, batch, request(List.of(), List.of()), List.of(file));

        assertThat(savedSources.get(0).getSourceVersion()).isEqualTo("DOCX");
        assertThat(savedSources.get(0).getSourcePayloadRef()).isEqualTo("override/path.docx");
    }

    @Test
    void persistAndBuildSourceInputs_usesExtensionWhenExtractorSourceTypeIsBlank() {
        AiQuestionGenerationBatch batch = batch(courseId);
        MockMultipartFile file = new MockMultipartFile(
                "files",
                "slides.pdf",
                "application/pdf",
                "pdf".getBytes(StandardCharsets.UTF_8));
        when(documentTextExtractionService.extract(file))
                .thenReturn(new DocumentTextExtractionResult(" ", "slides.pdf", longText("PDF text")));
        when(supabaseStorageClient.store(eq("ai-question-source-files"), any(), any(), any()))
                .thenAnswer(invocation -> new StoredFile("url", invocation.getArgument(1), "slides.pdf", "application/pdf", 3));

        service.persistAndBuildSourceInputs(courseId, batch, request(List.of(), List.of()), List.of(file));

        assertThat(savedSources.get(0).getSourceVersion()).isEqualTo("PDF");
    }

    @Test
    void persistAndBuildSourceInputs_infersBlankOrOctetStreamMimeTypesForAcceptedExtensions() {
        AiQuestionGenerationBatch batch = batch(courseId);
        MockMultipartFile txt = new MockMultipartFile("files", "notes.txt", null, longText("txt").getBytes(StandardCharsets.UTF_8));
        MockMultipartFile pdf = new MockMultipartFile("files", "paper.pdf", " ", "pdf".getBytes(StandardCharsets.UTF_8));
        MockMultipartFile docx = new MockMultipartFile("files", "paper.docx", "application/octet-stream", "docx".getBytes(StandardCharsets.UTF_8));
        when(documentTextExtractionService.extract(pdf))
                .thenReturn(new DocumentTextExtractionResult("PDF", "paper.pdf", longText("pdf")));
        when(documentTextExtractionService.extract(docx))
                .thenReturn(new DocumentTextExtractionResult("DOCX", "paper.docx", longText("docx")));
        when(supabaseStorageClient.store(eq("ai-question-source-files"), any(), any(), any()))
                .thenAnswer(invocation -> new StoredFile("url", invocation.getArgument(1), "file", invocation.getArgument(2), 10));

        service.persistAndBuildSourceInputs(courseId, batch, request(List.of(), List.of()), List.of(txt, pdf, docx));

        assertThat(savedSources).extracting(AiQuestionGenerationSource::getMimeType)
                .containsExactly(
                        "text/plain",
                        "application/pdf",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    }

    @Test
    void persistAndBuildSourceInputs_persistsTranscriptSourcesWithSegmentsAndDeduplicatesIds() {
        AiQuestionGenerationBatch batch = batch(courseId);
        VideoAiContent transcript = transcript(longText("Transcript source"));
        transcript.setLessonScope("MASTER");
        transcript.setStatus("published");
        transcript.getSegments().add(segment(transcript, 2, 65_000L, 70_000L, "   "));
        when(videoAiContentRepository.findById(transcript.getId())).thenReturn(Optional.of(transcript));

        List<QuestionGenerationProvider.SourceInput> inputs = service.persistAndBuildSourceInputs(courseId, batch,
                request(List.of(transcript.getId(), transcript.getId()), List.of()), List.of());

        assertThat(inputs).hasSize(1);
        assertThat(savedSources.get(0).getSourceKind()).isEqualTo(AiQuestionGenerationSource.KIND_TRANSCRIPT);
        assertThat(savedSources.get(0).getSourcePayloadRef()).isEqualTo("video_ai_contents:" + transcript.getId());
        assertThat(savedChunks).hasSize(1);
        assertThat(savedChunks.get(0).getChunkReference()).contains("segment-1@00:00-01:05");
    }

    @Test
    void persistAndBuildSourceInputs_handlesNullTranscriptRevisionSegmentIndexAndTimes() {
        AiQuestionGenerationBatch batch = batch(courseId);
        VideoAiContent transcript = transcript(longText("Transcript null metadata"));
        transcript.setRevision(null);
        transcript.getSegments().clear();
        transcript.getSegments().add(segment(transcript, null, null, null, longText("segment")));
        when(videoAiContentRepository.findById(transcript.getId())).thenReturn(Optional.of(transcript));

        service.persistAndBuildSourceInputs(courseId, batch, request(List.of(transcript.getId()), List.of()), List.of());

        assertThat(savedSources.get(0).getSourceVersion()).isEqualTo("0");
        assertThat(savedChunks.get(0).getChunkReference()).contains("segment-0@00:00-00:00");
    }

    @Test
    void persistAndBuildSourceInputs_rejectsTranscriptWhoseSegmentsHaveNoUsableText() {
        AiQuestionGenerationBatch batch = batch(courseId);
        VideoAiContent transcript = transcript(longText("Transcript blank segments"));
        transcript.getSegments().clear();
        transcript.getSegments().add(segment(transcript, 1, 0L, 1_000L, "   "));
        when(videoAiContentRepository.findById(transcript.getId())).thenReturn(Optional.of(transcript));

        assertThatThrownBy(() -> service.persistAndBuildSourceInputs(courseId, batch, request(List.of(transcript.getId()), List.of()), List.of()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.AI_SOURCE_INVALID));
    }

    @Test
    void persistAndBuildSourceInputs_fallsBackToTextChunksWhenTranscriptHasNoSegments() {
        AiQuestionGenerationBatch batch = batch(courseId);
        VideoAiContent transcript = transcript(longText("Transcript no segments"));
        transcript.setLessonScope("MASTER");
        transcript.setStatus("published");
        transcript.getSegments().clear();
        when(videoAiContentRepository.findById(transcript.getId())).thenReturn(Optional.of(transcript));

        service.persistAndBuildSourceInputs(courseId, batch, request(List.of(transcript.getId()), List.of()), List.of());

        assertThat(savedChunks).hasSize(1);
        assertThat(savedChunks.get(0).getChunkReference()).isEqualTo("Transcript-1");
    }

    @Test
    void listSources_filtersNullTranscriptTextAndHandlesNonPositiveSegmentEnds() {
        VideoAiContent nullText = transcript(null);
        VideoAiContent nonPositiveDuration = transcript(longText("duration"));
        nonPositiveDuration.getSegments().clear();
        nonPositiveDuration.getSegments().add(segment(nonPositiveDuration, 1, 0L, null, longText("duration")));
        nonPositiveDuration.getSegments().add(segment(nonPositiveDuration, 2, 0L, -1L, longText("duration")));
        when(videoAiContentRepository.findPublishedMasterTranscriptsByCourseId(courseId))
                .thenReturn(List.of(nullText, nonPositiveDuration));

        List<AiQuestionDraftDtos.SourceOptionResponse> response = service.listSources(courseId);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).durationSeconds()).isNull();
    }

    @Test
    void buildSourceInputsForBatch_rebuildsExistingSourcesAndChunks() {
        AiQuestionGenerationSource source = source(batchId, false, "payload");
        AiQuestionGenerationSourceChunk chunk = chunk(source.getId(), 0, "chunk-1", "excerpt");
        savedSources.add(source);
        savedChunks.add(chunk);

        List<QuestionGenerationProvider.SourceInput> inputs = service.buildSourceInputsForBatch(batchId);

        assertThat(inputs).hasSize(1);
        assertThat(inputs.get(0).generationSourceId()).isEqualTo(source.getId());
        assertThat(inputs.get(0).chunks()).hasSize(1);
        assertThat(inputs.get(0).chunks().get(0).chunkReference()).isEqualTo("chunk-1");
    }

    @Test
    void persistAndBuildSourceInputs_rejectsTooManyOrOversizedSources() {
        AiQuestionGenerationBatch batch = batch(courseId);

        List<AiQuestionDraftDtos.PastedTextSourceRequest> tooMany = new ArrayList<>();
        for (int index = 0; index < 9; index += 1) {
            tooMany.add(new AiQuestionDraftDtos.PastedTextSourceRequest("source-" + index, longText("source " + index)));
        }
        assertThatThrownBy(() -> service.persistAndBuildSourceInputs(courseId, batch, request(List.of(), tooMany), List.of()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.AI_INVALID_GENERATION_CONFIG));

        String tooLarge = "x".repeat(50_001);
        assertThatThrownBy(() -> service.persistAndBuildSourceInputs(courseId, batch,
                request(List.of(), List.of(new AiQuestionDraftDtos.PastedTextSourceRequest("large", tooLarge))), List.of()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.PAYLOAD_TOO_LARGE));
    }

    @Test
    void persistAndBuildSourceInputs_rejectsShortPastedTextAndBudgetOverflow() {
        AiQuestionGenerationBatch batch = batch(courseId);
        assertThatThrownBy(() -> service.persistAndBuildSourceInputs(courseId, batch,
                request(List.of(), List.of(new AiQuestionDraftDtos.PastedTextSourceRequest("short", "too short"))), List.of()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.AI_SOURCE_INVALID));

        List<AiQuestionDraftDtos.PastedTextSourceRequest> hugeBudget = List.of(
                new AiQuestionDraftDtos.PastedTextSourceRequest("one", "a".repeat(50_000)),
                new AiQuestionDraftDtos.PastedTextSourceRequest("two", "b".repeat(50_000)),
                new AiQuestionDraftDtos.PastedTextSourceRequest("three", "c".repeat(50_000)),
                new AiQuestionDraftDtos.PastedTextSourceRequest("four", "d".repeat(50_000)),
                new AiQuestionDraftDtos.PastedTextSourceRequest("five", "e".repeat(50_000)),
                new AiQuestionDraftDtos.PastedTextSourceRequest("six", "f".repeat(50_000)),
                new AiQuestionDraftDtos.PastedTextSourceRequest("seven", "g".repeat(1_000)));
        assertThatThrownBy(() -> service.persistAndBuildSourceInputs(courseId, batch, request(List.of(), hugeBudget), List.of()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.PAYLOAD_TOO_LARGE));
    }

    @Test
    void persistAndBuildSourceInputs_rejectsInvalidFiles() throws IOException {
        AiQuestionGenerationBatch batch = batch(courseId);
        List<MultipartFile> filesWithNull = new ArrayList<>();
        filesWithNull.add(null);
        assertFileInvalid(batch, filesWithNull, ErrorCode.INVALID_REQUEST);
        assertFileInvalid(batch, List.of(new MockMultipartFile("files", "empty.txt", "text/plain", new byte[0])), ErrorCode.INVALID_REQUEST);

        when(storageProperties.getAiQuestionSourceFileMaxSize()).thenReturn(DataSize.ofBytes(3));
        assertFileInvalid(batch, List.of(new MockMultipartFile("files", "large.txt", "text/plain", "large".getBytes(StandardCharsets.UTF_8))), ErrorCode.PAYLOAD_TOO_LARGE);
        when(storageProperties.getAiQuestionSourceFileMaxSize()).thenReturn(DataSize.ofMegabytes(25));

        assertFileInvalid(batch, List.of(new MockMultipartFile("files", "bad.exe", "application/octet-stream", "data".getBytes(StandardCharsets.UTF_8))), ErrorCode.UNSUPPORTED_MEDIA_TYPE);
        assertFileInvalid(batch, List.of(new MockMultipartFile("files", "bad.txt", "application/json", "data".getBytes(StandardCharsets.UTF_8))), ErrorCode.UNSUPPORTED_MEDIA_TYPE);
        assertFileInvalid(batch, List.of(new MockMultipartFile("files", "nofile", "text/plain", "data".getBytes(StandardCharsets.UTF_8))), ErrorCode.UNSUPPORTED_MEDIA_TYPE);
        assertFileInvalid(batch, List.of(new MockMultipartFile("files", "bad.", "text/plain", "data".getBytes(StandardCharsets.UTF_8))), ErrorCode.UNSUPPORTED_MEDIA_TYPE);
        assertFileInvalid(batch, List.of(new MockMultipartFile("files", "bad..txt", "text/plain", "data".getBytes(StandardCharsets.UTF_8))), ErrorCode.INVALID_REQUEST);
        assertFileInvalid(batch, List.of(new MockMultipartFile("files", " ", "text/plain", "data".getBytes(StandardCharsets.UTF_8))), ErrorCode.INVALID_REQUEST);
        assertFileInvalid(batch, List.of(new MockMultipartFile("files", "folder/", "text/plain", "data".getBytes(StandardCharsets.UTF_8))), ErrorCode.INVALID_REQUEST);
        assertFileInvalid(batch, List.of(new MockMultipartFile("files", null, "text/plain", "data".getBytes(StandardCharsets.UTF_8))), ErrorCode.INVALID_REQUEST);

        MultipartFile unreadable = mock(MultipartFile.class);
        when(unreadable.isEmpty()).thenReturn(false);
        when(unreadable.getSize()).thenReturn(123L);
        when(unreadable.getOriginalFilename()).thenReturn("source.txt");
        when(unreadable.getContentType()).thenReturn("text/plain");
        when(unreadable.getBytes()).thenThrow(new IOException("boom"));
        assertFileInvalid(batch, List.of(unreadable), ErrorCode.INVALID_REQUEST);
    }

    @Test
    void persistAndBuildSourceInputs_rejectsInvalidDocumentExtractionText() {
        AiQuestionGenerationBatch batch = batch(courseId);
        MockMultipartFile shortPdf = new MockMultipartFile("files", "short.pdf", "application/pdf", "pdf".getBytes(StandardCharsets.UTF_8));
        when(documentTextExtractionService.extract(shortPdf))
                .thenReturn(new DocumentTextExtractionResult("PDF", "short.pdf", "short"));
        assertFileInvalid(batch, List.of(shortPdf), ErrorCode.AI_SOURCE_INVALID);

        MockMultipartFile hugePdf = new MockMultipartFile("files", "huge.pdf", "application/pdf", "pdf".getBytes(StandardCharsets.UTF_8));
        when(documentTextExtractionService.extract(hugePdf))
                .thenReturn(new DocumentTextExtractionResult("PDF", "huge.pdf", "x".repeat(300_001)));
        assertFileInvalid(batch, List.of(hugePdf), ErrorCode.PAYLOAD_TOO_LARGE);
    }

    @Test
    void persistAndBuildSourceInputs_rollsBackUploadedAuditFilesWhenLaterChunkSaveFails() {
        AiQuestionGenerationBatch batch = batch(courseId);
        MockMultipartFile file = new MockMultipartFile(
                "files",
                "source.txt",
                "text/plain",
                longText("rollback").getBytes(StandardCharsets.UTF_8));
        when(supabaseStorageClient.store(eq("ai-question-source-files"), any(), any(), any()))
                .thenReturn(new StoredFile("url", "stored/source.txt", "source.txt", "text/plain", 100));
        doThrow(new BusinessException(ErrorCode.AI_SOURCE_INVALID, "chunk failed"))
                .when(sourceChunkRepository).save(any());

        assertThatThrownBy(() -> service.persistAndBuildSourceInputs(courseId, batch, request(List.of(), List.of()), List.of(file)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.AI_SOURCE_INVALID));
        verify(supabaseStorageClient).deleteObject("ai-question-source-files", "stored/source.txt");
    }

    @Test
    void persistAndBuildSourceInputs_rejectsMissingOutOfScopeShortOrOversizedTranscripts() {
        AiQuestionGenerationBatch batch = batch(courseId);
        UUID missing = UUID.randomUUID();
        when(videoAiContentRepository.findById(missing)).thenReturn(Optional.empty());
        assertTranscriptInvalid(batch, missing, ErrorCode.AI_SOURCE_OUT_OF_SCOPE);

        VideoAiContent otherCourse = transcript(longText("other"));
        otherCourse.setCourseId(UUID.randomUUID());
        when(videoAiContentRepository.findById(otherCourse.getId())).thenReturn(Optional.of(otherCourse));
        assertTranscriptInvalid(batch, otherCourse.getId(), ErrorCode.AI_SOURCE_OUT_OF_SCOPE);

        VideoAiContent classScoped = transcript(longText("class"));
        classScoped.setClassId(UUID.randomUUID());
        when(videoAiContentRepository.findById(classScoped.getId())).thenReturn(Optional.of(classScoped));
        assertTranscriptInvalid(batch, classScoped.getId(), ErrorCode.AI_SOURCE_OUT_OF_SCOPE);

        VideoAiContent lessonScoped = transcript(longText("lesson"));
        lessonScoped.setLessonScope("CLASS");
        when(videoAiContentRepository.findById(lessonScoped.getId())).thenReturn(Optional.of(lessonScoped));
        assertTranscriptInvalid(batch, lessonScoped.getId(), ErrorCode.AI_SOURCE_OUT_OF_SCOPE);

        VideoAiContent draft = transcript(longText("draft"));
        draft.setStatus("draft");
        when(videoAiContentRepository.findById(draft.getId())).thenReturn(Optional.of(draft));
        assertTranscriptInvalid(batch, draft.getId(), ErrorCode.AI_SOURCE_OUT_OF_SCOPE);

        VideoAiContent shortTranscript = transcript("short");
        when(videoAiContentRepository.findById(shortTranscript.getId())).thenReturn(Optional.of(shortTranscript));
        assertTranscriptInvalid(batch, shortTranscript.getId(), ErrorCode.AI_SOURCE_INVALID);

        VideoAiContent hugeTranscript = transcript("x".repeat(200_001));
        when(videoAiContentRepository.findById(hugeTranscript.getId())).thenReturn(Optional.of(hugeTranscript));
        assertTranscriptInvalid(batch, hugeTranscript.getId(), ErrorCode.PAYLOAD_TOO_LARGE);
    }

    private void installRepositoryFakes() {
        lenient().when(sourceRepository.save(any())).thenAnswer(invocation -> {
            AiQuestionGenerationSource source = invocation.getArgument(0);
            if (source.getId() == null) {
                source.setId(UUID.randomUUID());
            }
            savedSources.removeIf(existing -> existing.getId().equals(source.getId()));
            savedSources.add(source);
            return source;
        });
        lenient().when(sourceChunkRepository.save(any())).thenAnswer(invocation -> {
            AiQuestionGenerationSourceChunk chunk = invocation.getArgument(0);
            if (chunk.getId() == null) {
                chunk.setId(UUID.randomUUID());
            }
            savedChunks.removeIf(existing -> existing.getId().equals(chunk.getId()));
            savedChunks.add(chunk);
            return chunk;
        });
        lenient().when(sourceRepository.findByBatchId(any())).thenAnswer(invocation -> {
            UUID id = invocation.getArgument(0);
            return savedSources.stream().filter(source -> id.equals(source.getBatchId())).toList();
        });
        lenient().when(sourceChunkRepository.findByGenerationSourceIdOrderByChunkIndexAsc(any())).thenAnswer(invocation -> {
            UUID id = invocation.getArgument(0);
            return savedChunks.stream()
                    .filter(chunk -> id.equals(chunk.getGenerationSourceId()))
                    .sorted((left, right) -> left.getChunkIndex().compareTo(right.getChunkIndex()))
                    .toList();
        });
    }

    private void assertFileInvalid(
            AiQuestionGenerationBatch batch,
            List<MultipartFile> files,
            ErrorCode errorCode
    ) {
        assertThatThrownBy(() -> service.persistAndBuildSourceInputs(courseId, batch, request(List.of(), List.of()), files))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(errorCode));
    }

    private void assertTranscriptInvalid(
            AiQuestionGenerationBatch batch,
            UUID transcriptId,
            ErrorCode errorCode
    ) {
        assertThatThrownBy(() -> service.persistAndBuildSourceInputs(courseId, batch, request(List.of(transcriptId), List.of()), List.of()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(errorCode));
    }

    private AiQuestionDraftDtos.CreateBatchRequest request(
            List<UUID> transcriptIds,
            List<AiQuestionDraftDtos.PastedTextSourceRequest> pastedTexts
    ) {
        return new AiQuestionDraftDtos.CreateBatchRequest(
                transcriptIds,
                pastedTexts,
                List.of("multiple_choice"),
                1,
                null,
                "vi",
                null,
                "idem");
    }

    private String longText(String seed) {
        return (seed + " content for AI question source validation. ").repeat(4);
    }

    private VideoAiContent transcript(String text) {
        VideoAiContent content = new VideoAiContent();
        content.setId(UUID.randomUUID());
        content.setCourseId(courseId);
        content.setLessonId(UUID.randomUUID());
        content.setLessonScope("MASTER");
        content.setLanguage("vi");
        content.setTranscriptText(text);
        content.setStatus("published");
        content.setRevision(2L);
        content.setUpdatedAt(Instant.parse("2026-07-28T10:00:00Z"));
        content.getSegments().add(segment(content, 1, 0L, 65_000L, text));
        return content;
    }

    private VideoAiTranscriptSegment segment(
            VideoAiContent content,
            Integer index,
            Long startMs,
            Long endMs,
            String text
    ) {
        VideoAiTranscriptSegment segment = new VideoAiTranscriptSegment();
        segment.setId(UUID.randomUUID());
        segment.setContent(content);
        segment.setSegmentIndex(index);
        segment.setStartMs(startMs);
        segment.setEndMs(endMs);
        segment.setText(text);
        return segment;
    }

    private AiQuestionGenerationBatch batch(UUID owningCourseId) {
        AiQuestionGenerationBatch batch = new AiQuestionGenerationBatch();
        batch.setId(batchId);
        batch.setCourseId(owningCourseId);
        batch.setRequestedBy(UUID.randomUUID());
        batch.setStatus(AiQuestionGenerationBatch.STATUS_READY);
        batch.setRequestedQuestionTypes("multiple_choice");
        batch.setRequestedCount(3);
        batch.setGeneratedCount(0);
        batch.setUsableCount(0);
        batch.setLanguage("vi");
        batch.setProvider("gemini");
        batch.setModel("gemini-test");
        batch.setRetryCount(0);
        batch.setCreatedAt(Instant.now());
        batch.setUpdatedAt(Instant.now());
        return batch;
    }

    private AiQuestionGenerationSource source(
            UUID owningBatchId,
            boolean downloadable,
            String payloadRef
    ) {
        AiQuestionGenerationSource source = new AiQuestionGenerationSource();
        source.setId(sourceId);
        source.setBatchId(owningBatchId);
        source.setSourceKind(AiQuestionGenerationSource.KIND_TEMPORARY_FILE);
        source.setSourcePayloadRef(payloadRef);
        source.setDownloadable(downloadable);
        source.setSourceName("source.txt");
        source.setSourceChecksum("checksum");
        source.setSourceVersion("1");
        source.setMimeType("text/plain");
        source.setFileSizeBytes(123L);
        source.setNormalizedCharCount(120);
        return source;
    }

    private AiQuestionGenerationSourceChunk chunk(
            UUID generationSourceId,
            int index,
            String reference,
            String excerpt
    ) {
        AiQuestionGenerationSourceChunk chunk = new AiQuestionGenerationSourceChunk();
        chunk.setId(UUID.randomUUID());
        chunk.setGenerationSourceId(generationSourceId);
        chunk.setChunkIndex(index);
        chunk.setChunkReference(reference);
        chunk.setContentExcerpt(excerpt);
        chunk.setContentChecksum("checksum");
        return chunk;
    }
}
