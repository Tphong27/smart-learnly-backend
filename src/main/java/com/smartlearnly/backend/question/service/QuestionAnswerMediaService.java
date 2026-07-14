package com.smartlearnly.backend.question.service;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.file.config.StorageProperties;
import com.smartlearnly.backend.file.service.FileStorageService;
import com.smartlearnly.backend.question.dto.QuestionAnswerMediaResponse;
import com.smartlearnly.backend.question.entity.Question;
import com.smartlearnly.backend.question.entity.QuestionAnswer;
import com.smartlearnly.backend.question.entity.QuestionAnswerMediaAttachment;
import com.smartlearnly.backend.question.entity.QuestionBank;
import com.smartlearnly.backend.question.entity.QuestionMediaType;
import com.smartlearnly.backend.question.entity.QuestionStatus;
import com.smartlearnly.backend.question.repository.QuestionAnswerMediaAttachmentRepository;
import com.smartlearnly.backend.question.repository.QuestionAnswerRepository;
import com.smartlearnly.backend.question.repository.QuestionBankRepository;
import com.smartlearnly.backend.question.repository.QuestionRepository;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class QuestionAnswerMediaService {

    public static final int MAX_PER_ANSWER_PER_TYPE = 1;

    private static final Map<String, String> IMAGE_TYPES = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp"
    );
    private static final Map<String, String> AUDIO_TYPES = Map.of(
            "audio/mpeg", "mp3",
            "audio/mp4", "m4a",
            "audio/x-m4a", "m4a",
            "audio/wav", "wav",
            "audio/x-wav", "wav"
    );
    private static final Map<String, String> VIDEO_TYPES = Map.ofEntries(
            Map.entry("video/mp4", "mp4"),
            Map.entry("video/webm", "webm"),
            Map.entry("video/quicktime", "mov"),
            Map.entry("video/x-matroska", "mkv"),
            Map.entry("video/x-msvideo", "avi"),
            Map.entry("video/x-ms-wmv", "wmv"),
            Map.entry("video/3gpp", "3gp"),
            Map.entry("video/x-flv", "flv"),
            Map.entry("video/mpeg", "mpg")
    );

    private final QuestionRepository questionRepository;
    private final QuestionAnswerRepository answerRepository;
    private final QuestionAnswerMediaAttachmentRepository mediaAttachmentRepository;
    private final QuestionBankRepository questionBankRepository;
    private final FileStorageService fileStorageService;
    private final StorageProperties storageProperties;
    private final Tika tika = new Tika();

    @Transactional(readOnly = true)
    public List<QuestionAnswerMediaResponse> list(UUID questionId, UUID answerId) {
        QuestionAnswer answer = findEditableAnswer(questionId, answerId);
        return orderedAttachments(answer.getId()).stream().map(this::toResponse).toList();
    }

    @Transactional
    public QuestionAnswerMediaResponse upload(UUID questionId, UUID answerId, String mediaType, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "An answer media file is required");
        }
        QuestionMediaType resolvedType = parseMediaType(mediaType);
        QuestionAnswer answer = findEditableAnswer(questionId, answerId);

        long existing = mediaAttachmentRepository.countByAnswerIdAndMediaType(answer.getId(), resolvedType);
        if (existing + 1 > MAX_PER_ANSWER_PER_TYPE) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, maxCountMessage(resolvedType));
        }

        byte[] content = readAndValidateSize(file, maxSize(resolvedType), requiredFileMessage(resolvedType));
        String detected = detectContentType(content, resolvedType);
        String extension = allowedTypes(resolvedType).get(detected);
        if (extension == null) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_MEDIA_TYPE, unsupportedTypeMessage(resolvedType));
        }

        QuestionAnswerMediaAttachment existingAttachment =
                mediaAttachmentRepository.findByAnswerIdAndMediaType(answer.getId(), resolvedType).orElse(null);

        String objectKey = "answers/%s/%s/%s.%s".formatted(
                answer.getId(),
                resolvedType == QuestionMediaType.IMAGE ? "images" : resolvedType == QuestionMediaType.AUDIO ? "audios" : "videos",
                UUID.randomUUID(),
                extension
        );
        FileStorageService.StoredFile stored = fileStorageService.store(
                storageProperties.getQuestionMediaBucket(),
                objectKey,
                detected,
                content
        );

        QuestionAnswerMediaAttachment attachment =
                existingAttachment == null ? new QuestionAnswerMediaAttachment() : existingAttachment;
        attachment.setAnswerId(answer.getId());
        attachment.setMediaType(resolvedType);
        attachment.setMediaUrl(stored.url());
        attachment.setObjectKey(stored.objectPath());
        attachment.setBucket(storageProperties.getQuestionMediaBucket());
        attachment.setOriginalFileName(normalizeFileName(file.getOriginalFilename(), stored.fileName()));
        attachment.setContentType(stored.contentType());
        attachment.setFileSize(stored.size());
        attachment.setDisplayOrder(1);
        attachment.setImportSource("manual");
        return toResponse(mediaAttachmentRepository.save(attachment));
    }

    @Transactional
    public void delete(UUID questionId, UUID answerId, UUID attachmentId) {
        QuestionAnswer answer = findEditableAnswer(questionId, answerId);
        QuestionAnswerMediaAttachment attachment = mediaAttachmentRepository
                .findByAnswerIdAndId(answer.getId(), attachmentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Answer media attachment not found"));
        mediaAttachmentRepository.delete(attachment);
        mediaAttachmentRepository.flush();
    }

    public QuestionMediaType parseMediaType(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Media type is required");
        }
        String normalized = value.trim().replace('-', '_').toUpperCase(Locale.ROOT);
        try {
            return QuestionMediaType.valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Media type must be image, audio, or video");
        }
    }

    public QuestionAnswerMediaResponse toResponse(QuestionAnswerMediaAttachment attachment) {
        if (attachment == null) {
            return null;
        }
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

    private List<QuestionAnswerMediaAttachment> orderedAttachments(UUID answerId) {
        List<QuestionAnswerMediaAttachment> attachments = new ArrayList<>();
        attachments.addAll(mediaAttachmentRepository.findByAnswerIdAndMediaTypeOrderByDisplayOrderAsc(answerId, QuestionMediaType.IMAGE));
        attachments.addAll(mediaAttachmentRepository.findByAnswerIdAndMediaTypeOrderByDisplayOrderAsc(answerId, QuestionMediaType.AUDIO));
        attachments.addAll(mediaAttachmentRepository.findByAnswerIdAndMediaTypeOrderByDisplayOrderAsc(answerId, QuestionMediaType.VIDEO));
        return attachments;
    }

    private QuestionAnswer findEditableAnswer(UUID questionId, UUID answerId) {
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Question not found"));
        if (question.getStatus() == QuestionStatus.ARCHIVED) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "Cannot modify media for an archived question");
        }
        QuestionBank bank = questionBankRepository.findById(question.getQuestionBankId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Question bank not found"));
        if ("archived".equals(bank.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "Cannot modify media in an archived question bank");
        }
        QuestionAnswer answer = answerRepository.findById(answerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Answer not found"));
        if (!answer.getQuestionId().equals(questionId)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Answer does not belong to the question");
        }
        return answer;
    }

    private byte[] readAndValidateSize(MultipartFile file, DataSize maxSize, String requiredMessage) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, requiredMessage);
        }
        if (file.getSize() > maxSize.toBytes()) {
            throw new BusinessException(ErrorCode.PAYLOAD_TOO_LARGE);
        }
        try {
            byte[] content = file.getBytes();
            if (content.length == 0) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST, requiredMessage);
            }
            return content;
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Answer media file could not be read");
        }
    }

    private String detectContentType(byte[] content, QuestionMediaType mediaType) {
        try {
            return tika.detect(content);
        } catch (Exception exception) {
            String base = switch (mediaType) {
                case IMAGE -> "Answer image";
                case AUDIO -> "Answer audio";
                case VIDEO -> "Answer video";
            };
            throw new BusinessException(ErrorCode.UNSUPPORTED_MEDIA_TYPE, base + " type could not be detected");
        }
    }

    private Map<String, String> allowedTypes(QuestionMediaType mediaType) {
        return switch (mediaType) {
            case IMAGE -> IMAGE_TYPES;
            case AUDIO -> AUDIO_TYPES;
            case VIDEO -> VIDEO_TYPES;
        };
    }

    private DataSize maxSize(QuestionMediaType mediaType) {
        return switch (mediaType) {
            case IMAGE -> storageProperties.getQuestionImageMaxSize();
            case AUDIO -> storageProperties.getQuestionAudioMaxSize();
            case VIDEO -> storageProperties.getQuestionVideoMaxSize();
        };
    }

    private String maxCountMessage(QuestionMediaType mediaType) {
        return switch (mediaType) {
            case IMAGE -> "An answer can have at most 1 image";
            case AUDIO -> "An answer can have at most 1 audio file";
            case VIDEO -> "An answer can have at most 1 video";
        };
    }

    private String requiredFileMessage(QuestionMediaType mediaType) {
        return switch (mediaType) {
            case IMAGE -> "Answer image file is required";
            case AUDIO -> "Answer audio file is required";
            case VIDEO -> "Answer video file is required";
        };
    }

    private String unsupportedTypeMessage(QuestionMediaType mediaType) {
        return switch (mediaType) {
            case IMAGE -> "Answer image must be JPEG, PNG, or WebP";
            case AUDIO -> "Answer audio must be MP3, M4A, or WAV";
            case VIDEO -> "Answer video must be MP4, WebM, MOV, AVI, MKV, WMV, 3GP, FLV, or MPEG";
        };
    }

    private String normalizeFileName(String originalFileName, String fallback) {
        if (originalFileName == null || originalFileName.isBlank()) {
            return fallback;
        }
        String trimmed = originalFileName.trim();
        int slashIndex = Math.max(trimmed.lastIndexOf('/'), trimmed.lastIndexOf('\\'));
        return slashIndex >= 0 ? trimmed.substring(slashIndex + 1) : trimmed;
    }

    private String toApiValue(Enum<?> value) {
        return value == null ? null : value.name().toLowerCase(Locale.ROOT);
    }
}