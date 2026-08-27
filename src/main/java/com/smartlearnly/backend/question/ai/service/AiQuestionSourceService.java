package com.smartlearnly.backend.question.ai.service;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.course.access.service.CourseAccessService;
import com.smartlearnly.backend.file.config.StorageProperties;
import com.smartlearnly.backend.file.service.CloudflareR2StorageClient;
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
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** Quản lý nguồn đầu vào, file audit và transcript dùng cho Question AI generation. */
@Service
@RequiredArgsConstructor
public class AiQuestionSourceService {
    private static final int MIN_SOURCE_CHARACTERS = 100;
    private static final int MAX_PASTED_TEXT_CHARACTERS = 50_000;
    private static final int MAX_TRANSCRIPT_CHARACTERS = 200_000;
    private static final int MAX_SOURCES_PER_BATCH = 3;
    private static final int MAX_NORMALIZED_CHARACTERS_PER_BATCH = 300_000;
    private static final int TARGET_CHUNK_CHARACTERS = 2_800;
    private static final int SIGNED_URL_TTL_SECONDS = 300;
    private static final List<String> ACCEPTED_DOCUMENT_MIME_TYPES = List.of(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "text/plain"
    );
    private static final List<String> ACCEPTED_DOCUMENT_EXTENSIONS = List.of("pdf", "docx", "txt");

    private final CourseAccessService courseAccessService;
    private final AiQuestionGenerationBatchRepository batchRepository;
    private final AiQuestionGenerationSourceRepository sourceRepository;
    private final AiQuestionGenerationSourceChunkRepository sourceChunkRepository;
    private final StorageProperties storageProperties;
    private final CloudflareR2StorageClient r2StorageClient;
    private final FlashcardDocumentTextExtractionService documentTextExtractionService;
    private final VideoAiContentRepository videoAiContentRepository;

    /** Liệt kê transcript master đã publish và đủ độ dài để dùng làm nguồn generation. */
    @Transactional(readOnly = true)
    public List<AiQuestionDraftDtos.SourceOptionResponse> listSources(UUID courseId) {
        courseAccessService.requireReadableCourse(courseId);
        return videoAiContentRepository.findPublishedMasterTranscriptsByCourseId(courseId).stream()
                .filter(content -> normalizeSourceText(content.getTranscriptText()).length() >= MIN_SOURCE_CHARACTERS)
                .map(content -> new AiQuestionDraftDtos.SourceOptionResponse(
                        content.getId(),
                        content.getId(),
                        content.getCourseId(),
                        content.getLessonId(),
                        null,
                        AiQuestionGenerationSource.KIND_TRANSCRIPT,
                        "Video transcript",
                        null,
                        content.getLanguage(),
                        durationSeconds(content),
                        checksum(normalizeSourceText(content.getTranscriptText())),
                        String.valueOf(content.getRevision() == null ? 0 : content.getRevision()),
                        Math.max(1, content.getSegments().size()),
                        normalizeSourceText(content.getTranscriptText()).length(),
                        content.getUpdatedAt()
                ))
                .toList();
    }

    /** Trả về giới hạn và định dạng nguồn để frontend validation đồng bộ với backend. */
    @Transactional(readOnly = true)
    public AiQuestionDraftDtos.SourceCapabilitiesResponse sourceCapabilities(UUID courseId) {
        courseAccessService.requireReadableCourse(courseId);
        return new AiQuestionDraftDtos.SourceCapabilitiesResponse(
                MIN_SOURCE_CHARACTERS,
                MAX_PASTED_TEXT_CHARACTERS,
                storageProperties.getAiQuestionSourceFileMaxSize().toBytes(),
                MAX_TRANSCRIPT_CHARACTERS,
                MAX_SOURCES_PER_BATCH,
                MAX_NORMALIZED_CHARACTERS_PER_BATCH,
                ACCEPTED_DOCUMENT_MIME_TYPES,
                ACCEPTED_DOCUMENT_EXTENSIONS
        );
    }

    /** Tạo URL tải file audit ngắn hạn sau khi kiểm tra source thuộc đúng batch/course. */
    @Transactional(readOnly = true)
    public AiQuestionDraftDtos.SourceDownloadUrlResponse sourceDownloadUrl(
            UUID courseId,
            UUID batchId,
            UUID sourceId
    ) {
        courseAccessService.requireReadableCourse(courseId);
        AiQuestionGenerationBatch batch = batchRepository.findById(batchId)
                .filter(value -> courseId.equals(value.getCourseId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "AI generation batch not found"));
        AiQuestionGenerationSource source = sourceRepository.findById(sourceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "AI generation source not found"));
        if (!batch.getId().equals(source.getBatchId())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "AI generation source not found");
        }
        if (!Boolean.TRUE.equals(source.getDownloadable())
                || source.getSourcePayloadRef() == null
                || source.getSourcePayloadRef().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "This source does not have a downloadable audit file");
        }
        Instant expiresAt = Instant.now().plusSeconds(SIGNED_URL_TTL_SECONDS);
        String url = r2StorageClient.getPresignedUrl(
                storageProperties.getAiQuestionSourceFileBucket(),
                source.getSourcePayloadRef(),
                SIGNED_URL_TTL_SECONDS
        );
        return new AiQuestionDraftDtos.SourceDownloadUrlResponse(
                url,
                expiresAt,
                source.getSourceName(),
                source.getMimeType(),
                source.getFileSizeBytes()
        );
    }

    /** Chuẩn hóa, bắt buộc ít nhất một nguồn, rồi lưu input provider cho batch mới. */
    @Transactional
    public List<QuestionGenerationProvider.SourceInput> persistAndBuildSourceInputs(
            UUID courseId,
            AiQuestionGenerationBatch batch,
            AiQuestionDraftDtos.CreateBatchRequest request,
            List<MultipartFile> files
    ) {
        List<SourceSpec> specs = resolveSourceSpecs(courseId, request, files);
        if (specs.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.AI_SOURCE_INVALID,
                    "At least one source material is required");
        }
        validateSourceBudget(specs);
        return buildSourceInputs(persistSources(batch, specs));
    }

    /** Dựng lại provider input từ source đã lưu khi retry batch thất bại. */
    @Transactional(readOnly = true)
    public List<QuestionGenerationProvider.SourceInput> buildSourceInputsForBatch(UUID batchId) {
        return buildSourceInputs(sourceRepository.findByBatchId(batchId));
    }

    /** Lưu metadata, file audit và chunk; xóa file đã upload nếu cả batch nguồn thất bại. */
    private List<AiQuestionGenerationSource> persistSources(
            AiQuestionGenerationBatch batch,
            List<SourceSpec> specs
    ) {
        List<AiQuestionGenerationSource> savedSources = new ArrayList<>();
        List<UploadedObject> uploadedObjects = new ArrayList<>();
        try {
            for (SourceSpec spec : specs) {
                AiQuestionGenerationSource source = new AiQuestionGenerationSource();
                source.setBatchId(batch.getId());
                source.setSourceKind(spec.kind());
                source.setTranscriptContentId(spec.transcriptContentId());
                source.setLessonId(spec.lessonId());
                source.setSourceName(spec.sourceName());
                source.setSourceChecksum(spec.checksum());
                source.setSourceVersion(spec.version());
                source.setMimeType(spec.mimeType());
                source.setFileSizeBytes(spec.fileSizeBytes());
                source.setNormalizedCharCount(spec.normalizedCharCount());
                source.setDownloadable(spec.fileContent() != null);
                String filePayloadRef = spec.fileContent() == null
                        ? null
                        : auditObjectPath(batch.getId(), UUID.randomUUID(), spec.fileName());
                source.setSourcePayloadRef(filePayloadRef != null ? filePayloadRef : spec.payloadRef());
                source = sourceRepository.save(source);

                if (spec.fileContent() != null) {
                    r2StorageClient.putPrivateObject(
                            storageProperties.getAiQuestionSourceFileBucket(),
                            filePayloadRef,
                            spec.mimeType(),
                            new ByteArrayInputStream(spec.fileContent()),
                            spec.fileContent().length
                    );
                    uploadedObjects.add(new UploadedObject(
                            storageProperties.getAiQuestionSourceFileBucket(),
                            filePayloadRef));
                }

                persistSourceChunks(source, spec.chunks());
                savedSources.add(source);
            }
            return List.copyOf(savedSources);
        }
        catch (RuntimeException exception) {
            uploadedObjects.forEach(upload ->
                    r2StorageClient.deleteObject(upload.bucket(), upload.objectPath()));
            throw exception;
        }
    }

    /** Gom các loại nguồn được request thành danh sách chuẩn hóa để kiểm tra bắt buộc. */
    private List<SourceSpec> resolveSourceSpecs(
            UUID courseId,
            AiQuestionDraftDtos.CreateBatchRequest request,
            List<MultipartFile> files
    ) {
        List<SourceSpec> specs = new ArrayList<>();
        specs.addAll(resolvePastedTextSpecs(request.pastedTextSources()));
        specs.addAll(resolveDocumentSpecs(files));
        specs.addAll(resolveTranscriptSpecs(courseId, request.transcriptContentIds()));
        return specs;
    }

    /** Chuẩn hóa các đoạn text người dùng dán trực tiếp. */
    private List<SourceSpec> resolvePastedTextSpecs(
            List<AiQuestionDraftDtos.PastedTextSourceRequest> pastedTextSources
    ) {
        if (pastedTextSources == null || pastedTextSources.isEmpty()) {
            return List.of();
        }
        List<SourceSpec> specs = new ArrayList<>();
        int index = 1;
        for (AiQuestionDraftDtos.PastedTextSourceRequest request : pastedTextSources) {
            String text = normalizeSourceText(request.text());
            validateSourceTextLength(text, MAX_PASTED_TEXT_CHARACTERS, "Pasted text");
            String name = normalizeNullable(request.sourceName());
            if (name == null) name = "Pasted text " + index;
            specs.add(new SourceSpec(
                    AiQuestionGenerationSource.KIND_PASTED_TEXT,
                    null,
                    null,
                    name,
                    checksum(text),
                    "pasted-" + checksum(text).substring(0, 12),
                    "text/plain",
                    null,
                    text.length(),
                    null,
                    null,
                    null,
                    chunkText(name, text, null)
            ));
            index += 1;
        }
        return specs;
    }

    /** Đọc PDF, DOCX hoặc TXT và giữ nguyên bytes để lưu file audit. */
    private List<SourceSpec> resolveDocumentSpecs(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return List.of();
        }
        List<SourceSpec> specs = new ArrayList<>();
        for (MultipartFile file : files) {
            validateFile(file);
            String fileName = sanitizeOriginalFileName(file.getOriginalFilename());
            String extension = extension(fileName);
            byte[] bytes = readBytes(file);
            String text;
            String sourceType;
            if ("txt".equals(extension)) {
                sourceType = "TXT";
                text = normalizeSourceText(new String(bytes, StandardCharsets.UTF_8));
            }
            else {
                DocumentTextExtractionResult extraction = documentTextExtractionService.extract(file);
                sourceType = normalizeNullable(extraction.sourceType());
                text = normalizeSourceText(extraction.text());
            }
            validateSourceTextLength(text, MAX_NORMALIZED_CHARACTERS_PER_BATCH, "Document text");
            String checksum = checksum(text);
            specs.add(new SourceSpec(
                    AiQuestionGenerationSource.KIND_TEMPORARY_FILE,
                    null,
                    null,
                    fileName,
                    checksum,
                    sourceType == null ? extension.toUpperCase(Locale.ROOT) : sourceType,
                    normalizeContentType(file.getContentType(), extension),
                    (long) bytes.length,
                    text.length(),
                    null,
                    fileName,
                    bytes,
                    chunkText(fileName, text, null)
            ));
        }
        return specs;
    }

    /** Kiểm tra transcript thuộc master published của đúng course trước khi dùng. */
    private List<SourceSpec> resolveTranscriptSpecs(UUID courseId, List<UUID> transcriptContentIds) {
        if (transcriptContentIds == null || transcriptContentIds.isEmpty()) {
            return List.of();
        }
        List<SourceSpec> specs = new ArrayList<>();
        for (UUID contentId : new LinkedHashSet<>(transcriptContentIds)) {
            VideoAiContent content = videoAiContentRepository.findById(contentId)
                    .orElseThrow(() -> new BusinessException(
                            ErrorCode.AI_SOURCE_OUT_OF_SCOPE,
                            "Transcript source was not found"));
            if (!courseId.equals(content.getCourseId())
                    || content.getClassId() != null
                    || !"MASTER".equalsIgnoreCase(content.getLessonScope())
                    || !"published".equalsIgnoreCase(content.getStatus())) {
                throw new BusinessException(
                        ErrorCode.AI_SOURCE_OUT_OF_SCOPE,
                        "Transcript source is not published for this course");
            }
            String text = normalizeSourceText(content.getTranscriptText());
            validateSourceTextLength(text, MAX_TRANSCRIPT_CHARACTERS, "Transcript text");
            List<SourceChunkSpec> chunks = content.getSegments() == null || content.getSegments().isEmpty()
                    ? chunkText("Transcript", text, null)
                    : content.getSegments().stream()
                    .filter(segment -> normalizeSourceText(segment.getText()).length() >= 1)
                    .map(segment -> new SourceChunkSpec(
                            transcriptReference(segment),
                            normalizeSourceText(segment.getText()),
                            checksum(normalizeSourceText(segment.getText())),
                            segment.getStartMs(),
                            segment.getEndMs()
                    ))
                    .toList();
            specs.add(new SourceSpec(
                    AiQuestionGenerationSource.KIND_TRANSCRIPT,
                    content.getId(),
                    content.getLessonId(),
                    "Video transcript",
                    checksum(text),
                    String.valueOf(content.getRevision() == null ? 0 : content.getRevision()),
                    "text/plain",
                    null,
                    text.length(),
                    "video_ai_contents:" + content.getId(),
                    null,
                    null,
                    chunks
            ));
        }
        return specs;
    }

    /** Giới hạn số nguồn và tổng nội dung trước khi gọi provider. */
    private void validateSourceBudget(List<SourceSpec> specs) {
        if (specs.size() > MAX_SOURCES_PER_BATCH) {
            throw new BusinessException(
                    ErrorCode.AI_INVALID_GENERATION_CONFIG,
                    "A generation batch can use at most 3 sources");
        }
        int totalChars = specs.stream().mapToInt(SourceSpec::normalizedCharCount).sum();
        if (totalChars > MAX_NORMALIZED_CHARACTERS_PER_BATCH) {
            throw new BusinessException(
                    ErrorCode.PAYLOAD_TOO_LARGE,
                    "Selected sources exceed the AI generation content budget");
        }
    }

    /** Lưu các chunk theo đúng thứ tự để evidence có thể tham chiếu ổn định. */
    private void persistSourceChunks(AiQuestionGenerationSource source, List<SourceChunkSpec> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            throw new BusinessException(ErrorCode.AI_SOURCE_INVALID, "Generation source has no usable content chunks");
        }
        for (int index = 0; index < chunks.size(); index += 1) {
            SourceChunkSpec spec = chunks.get(index);
            AiQuestionGenerationSourceChunk chunk = new AiQuestionGenerationSourceChunk();
            chunk.setGenerationSourceId(source.getId());
            chunk.setChunkIndex(index);
            chunk.setChunkReference(spec.reference());
            chunk.setContentExcerpt(spec.excerpt());
            chunk.setContentChecksum(spec.checksum());
            chunk.setStartMs(spec.startMs());
            chunk.setEndMs(spec.endMs());
            sourceChunkRepository.save(chunk);
        }
    }

    /** Dựng cấu trúc source/chunk mà QuestionGenerationProvider đang sử dụng. */
    private List<QuestionGenerationProvider.SourceInput> buildSourceInputs(
            List<AiQuestionGenerationSource> sources
    ) {
        List<QuestionGenerationProvider.SourceInput> inputs = new ArrayList<>();
        for (AiQuestionGenerationSource source : sources) {
            List<QuestionGenerationProvider.ChunkInput> chunks = sourceChunkRepository
                    .findByGenerationSourceIdOrderByChunkIndexAsc(source.getId())
                    .stream()
                    .map(chunk -> new QuestionGenerationProvider.ChunkInput(
                            chunk.getId(),
                            chunk.getChunkReference(),
                            chunk.getContentExcerpt()))
                    .toList();
            inputs.add(new QuestionGenerationProvider.SourceInput(
                    source.getId(),
                    source.getSourceName(),
                    source.getSourceChecksum(),
                    source.getSourceVersion(),
                    chunks
            ));
        }
        return inputs;
    }

    /** Chia text theo paragraph nhưng giữ mỗi chunk gần giới hạn provider hiện hành. */
    private List<SourceChunkSpec> chunkText(String sourceName, String text, String referencePrefix) {
        String normalized = normalizeSourceText(text);
        List<String> paragraphs = List.of(normalized.split("\\n\\s*\\n"));
        List<SourceChunkSpec> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int chunkIndex = 1;
        for (String paragraph : paragraphs) {
            String block = normalizeSourceText(paragraph);
            if (block.isBlank()) continue;
            if (current.length() > 0
                    && current.length() + block.length() + 2 > TARGET_CHUNK_CHARACTERS) {
                chunks.add(chunkSpec(referencePrefix, chunkIndex, current.toString()));
                chunkIndex += 1;
                current.setLength(0);
            }
            if (current.length() > 0) current.append("\n\n");
            current.append(block);
        }
        if (current.length() > 0) {
            chunks.add(chunkSpec(
                    referencePrefix == null ? sourceName : referencePrefix,
                    chunkIndex,
                    current.toString()));
        }
        if (chunks.isEmpty()) {
            chunks.add(chunkSpec(
                    referencePrefix == null ? sourceName : referencePrefix,
                    1,
                    normalized));
        }
        return chunks;
    }

    /** Tạo metadata checksum và reference cho một text chunk. */
    private SourceChunkSpec chunkSpec(String referencePrefix, int index, String text) {
        String excerpt = normalizeSourceText(text);
        String prefix = normalizeNullable(referencePrefix);
        return new SourceChunkSpec(
                (prefix == null ? "chunk" : prefix) + "-" + index,
                excerpt,
                checksum(excerpt),
                null,
                null
        );
    }

    /** Kiểm tra độ dài source sau khi làm sạch. */
    private void validateSourceTextLength(String text, int maxCharacters, String label) {
        int length = normalizeSourceText(text).length();
        if (length < MIN_SOURCE_CHARACTERS) {
            throw new BusinessException(
                    ErrorCode.AI_SOURCE_INVALID,
                    label + " must be at least 100 characters after cleaning");
        }
        if (length > maxCharacters) {
            throw new BusinessException(ErrorCode.PAYLOAD_TOO_LARGE, label + " exceeds the allowed size");
        }
    }

    /** Kiểm tra kích thước, extension và MIME của file upload. */
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Uploaded source file is required");
        }
        if (file.getSize() > storageProperties.getAiQuestionSourceFileMaxSize().toBytes()) {
            throw new BusinessException(ErrorCode.PAYLOAD_TOO_LARGE, "Uploaded source file exceeds 25 MB");
        }
        String fileName = sanitizeOriginalFileName(file.getOriginalFilename());
        String extension = extension(fileName);
        if (!ACCEPTED_DOCUMENT_EXTENSIONS.contains(extension)) {
            throw new BusinessException(
                    ErrorCode.UNSUPPORTED_MEDIA_TYPE,
                    "AI question source files must be PDF, DOCX, or TXT");
        }
        String contentType = normalizeContentType(file.getContentType(), extension);
        if (!ACCEPTED_DOCUMENT_MIME_TYPES.contains(contentType)) {
            throw new BusinessException(
                    ErrorCode.UNSUPPORTED_MEDIA_TYPE,
                    "AI question source file MIME type is not supported");
        }
    }

    /** Đọc bytes upload và chuyển IOException thành lỗi request ổn định. */
    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        }
        catch (IOException exception) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Uploaded source file could not be read");
        }
    }

    /** Loại bỏ path khỏi tên file và chặn traversal. */
    private String sanitizeOriginalFileName(String originalFileName) {
        if (originalFileName == null || originalFileName.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Uploaded source file name is required");
        }
        String normalized = originalFileName.trim().replace('\\', '/');
        String fileName = normalized.substring(normalized.lastIndexOf('/') + 1).trim();
        if (fileName.isBlank() || fileName.contains("..")) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Uploaded source file name is invalid");
        }
        return fileName;
    }

    /** Đọc extension file đã được làm sạch. */
    private String extension(String fileName) {
        int index = fileName.lastIndexOf('.');
        if (index < 0 || index == fileName.length() - 1) {
            throw new BusinessException(
                    ErrorCode.UNSUPPORTED_MEDIA_TYPE,
                    "Uploaded source file extension is required");
        }
        return fileName.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    /** Chuẩn hóa MIME khi browser chỉ gửi octet-stream hoặc bỏ trống. */
    private String normalizeContentType(String contentType, String extension) {
        String normalized = contentType == null ? "" : contentType.trim().toLowerCase(Locale.ROOT);
        if ("application/octet-stream".equals(normalized) || normalized.isBlank()) {
            return switch (extension) {
                case "pdf" -> "application/pdf";
                case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
                case "txt" -> "text/plain";
                default -> normalized;
            };
        }
        if (normalized.startsWith("text/plain")) {
            return "text/plain";
        }
        return normalized;
    }

    /** Tạo object path audit ổn định bên trong bucket AI question source. */
    private String auditObjectPath(UUID batchId, UUID sourceId, String fileName) {
        String safeName = sanitizeOriginalFileName(fileName).replaceAll("[^A-Za-z0-9._ -]", "_");
        return batchId + "/" + sourceId + "/" + safeName;
    }

    /** Làm sạch whitespace của source nhưng giữ ranh giới paragraph. */
    private String normalizeSourceText(String value) {
        if (value == null) return "";
        return value.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace('\u00a0', ' ')
                .replaceAll("[\\t\\x0B\\f ]+", " ")
                .replaceAll(" *\\n *", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    /** Tạo SHA-256 checksum từ source text đã chuẩn hóa. */
    private String checksum(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(normalizeSourceText(value).getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Checksum algorithm is unavailable");
        }
    }

    /** Tạo reference dễ đọc từ vị trí transcript segment. */
    private String transcriptReference(VideoAiTranscriptSegment segment) {
        return "segment-" + (segment.getSegmentIndex() == null ? 0 : segment.getSegmentIndex())
                + "@" + formatMillis(segment.getStartMs()) + "-" + formatMillis(segment.getEndMs());
    }

    /** Định dạng milliseconds thành phút:giây cho evidence reference. */
    private String formatMillis(Long millis) {
        long value = millis == null ? 0L : Math.max(0L, millis);
        long seconds = value / 1000L;
        return "%02d:%02d".formatted(seconds / 60L, seconds % 60L);
    }

    /** Tính thời lượng transcript từ segment kết thúc cuối cùng. */
    private Long durationSeconds(VideoAiContent content) {
        if (content.getSegments() == null || content.getSegments().isEmpty()) return null;
        return content.getSegments().stream()
                .map(VideoAiTranscriptSegment::getEndMs)
                .filter(value -> value != null && value > 0)
                .max(Long::compareTo)
                .map(value -> value / 1000L)
                .orElse(null);
    }

    /** Chuẩn hóa chuỗi optional thành null khi rỗng. */
    private String normalizeNullable(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    /** Dữ liệu nguồn đã chuẩn hóa trước khi lưu. */
    private record SourceSpec(
            String kind,
            UUID transcriptContentId,
            UUID lessonId,
            String sourceName,
            String checksum,
            String version,
            String mimeType,
            Long fileSizeBytes,
            int normalizedCharCount,
            String payloadRef,
            String fileName,
            byte[] fileContent,
            List<SourceChunkSpec> chunks
    ) {
    }

    /** Metadata của một chunk dùng để tạo evidence. */
    private record SourceChunkSpec(
            String reference,
            String excerpt,
            String checksum,
            Long startMs,
            Long endMs
    ) {
    }

    /** Object đã upload để rollback khi lưu source thất bại. */
    private record UploadedObject(String bucket, String objectPath) {
    }
}
