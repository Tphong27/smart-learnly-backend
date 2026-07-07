package com.smartlearnly.backend.question.service;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.file.config.StorageProperties;
import com.smartlearnly.backend.file.service.FileStorageService;
import com.smartlearnly.backend.question.dto.QuestionMediaAttachmentResponse;
import com.smartlearnly.backend.question.dto.QuestionMediaDtos;
import com.smartlearnly.backend.question.entity.Question;
import com.smartlearnly.backend.question.entity.QuestionMediaAttachment;
import com.smartlearnly.backend.question.entity.QuestionMediaType;
import com.smartlearnly.backend.question.entity.QuestionStatus;
import com.smartlearnly.backend.question.repository.QuestionMediaAttachmentRepository;
import com.smartlearnly.backend.question.repository.QuestionRepository;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class QuestionMediaService {
    public static final int MAX_IMAGES_PER_QUESTION = 5;
    public static final int MAX_AUDIOS_PER_QUESTION = 3;

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

    private final QuestionRepository questionRepository;
    private final QuestionMediaAttachmentRepository mediaAttachmentRepository;
    private final FileStorageService fileStorageService;
    private final StorageProperties storageProperties;
    private final Tika tika = new Tika();

    @Transactional(readOnly = true)
    public List<QuestionMediaAttachmentResponse> list(UUID questionId) {
        ensureQuestionExists(questionId);
        return orderedAttachments(questionId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public QuestionMediaDtos.UploadResponse upload(UUID questionId, String mediaType, List<MultipartFile> files) {
        QuestionMediaType resolvedType = parseMediaType(mediaType);
        Question question = findEditableQuestion(questionId);
        if (files == null || files.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "At least one media file is required");
        }
        long existingCount = mediaAttachmentRepository.countByQuestionIdAndMediaType(question.getId(), resolvedType);
        int maxCount = maxCount(resolvedType);
        if (existingCount + files.size() > maxCount) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, maxCountMessage(resolvedType));
        }

        List<QuestionMediaAttachment> saved = new ArrayList<>();
        int nextOrder = (int) existingCount + 1;
        for (MultipartFile file : files) {
            saved.add(storeAsAttachment(question, resolvedType, file, nextOrder, "manual", null));
            nextOrder += 1;
        }
        return new QuestionMediaDtos.UploadResponse(question.getId(), saved.stream().map(this::toResponse).toList());
    }

    @Transactional
    public QuestionMediaAttachmentResponse replacePrimary(UUID questionId, QuestionMediaType mediaType, MultipartFile file) {
        Question question = findEditableQuestion(questionId);
        QuestionMediaAttachment primary = mediaAttachmentRepository
                .findFirstByQuestionIdAndMediaTypeOrderByDisplayOrderAsc(question.getId(), mediaType)
                .orElse(null);
        return toResponse(storeAsAttachment(question, mediaType, file, 1, "manual", primary));
    }

    @Transactional
    public QuestionMediaAttachmentResponse removePrimary(UUID questionId, QuestionMediaType mediaType) {
        Question question = findEditableQuestion(questionId);
        mediaAttachmentRepository.findFirstByQuestionIdAndMediaTypeOrderByDisplayOrderAsc(question.getId(), mediaType)
                .ifPresent(mediaAttachmentRepository::delete);
        mediaAttachmentRepository.flush();
        normalizeDisplayOrder(question.getId(), mediaType);
        return mediaAttachmentRepository.findFirstByQuestionIdAndMediaTypeOrderByDisplayOrderAsc(question.getId(), mediaType)
                .map(this::toResponse)
                .orElse(null);
    }

    @Transactional
    public void delete(UUID questionId, UUID attachmentId) {
        Question question = findEditableQuestion(questionId);
        QuestionMediaAttachment attachment = mediaAttachmentRepository.findByQuestionIdAndId(question.getId(), attachmentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Question media attachment not found"));
        QuestionMediaType mediaType = attachment.getMediaType();
        mediaAttachmentRepository.delete(attachment);
        mediaAttachmentRepository.flush();
        normalizeDisplayOrder(question.getId(), mediaType);
    }

    @Transactional
    public List<QuestionMediaAttachmentResponse> reorder(UUID questionId, QuestionMediaDtos.ReorderRequest request) {
        Question question = findEditableQuestion(questionId);
        QuestionMediaType mediaType = parseMediaType(request.mediaType());
        List<UUID> attachmentIds = request.attachmentIds();
        if (attachmentIds == null || attachmentIds.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Attachment IDs are required");
        }
        Set<UUID> uniqueIds = new HashSet<>(attachmentIds);
        if (uniqueIds.size() != attachmentIds.size()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Attachment IDs must be unique");
        }

        List<QuestionMediaAttachment> attachments = mediaAttachmentRepository.findByQuestionIdAndMediaTypeOrderByDisplayOrderAsc(question.getId(), mediaType);
        if (attachments.size() != attachmentIds.size()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Reorder request must include all attachments for the media type");
        }
        Map<UUID, QuestionMediaAttachment> byId = attachments.stream()
                .collect(java.util.stream.Collectors.toMap(QuestionMediaAttachment::getId, attachment -> attachment));
        if (!byId.keySet().containsAll(uniqueIds)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Reorder request contains an attachment that does not belong to this question and media type");
        }

        for (int index = 0; index < attachmentIds.size(); index += 1) {
            byId.get(attachmentIds.get(index)).setDisplayOrder(index + 1000);
        }
        mediaAttachmentRepository.saveAll(byId.values());
        mediaAttachmentRepository.flush();

        for (int index = 0; index < attachmentIds.size(); index += 1) {
            byId.get(attachmentIds.get(index)).setDisplayOrder(index + 1);
        }
        mediaAttachmentRepository.saveAll(byId.values());
        mediaAttachmentRepository.flush();
        return mediaAttachmentRepository.findByQuestionIdAndMediaTypeOrderByDisplayOrderAsc(question.getId(), mediaType)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public QuestionMediaType parseMediaType(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Media type is required");
        }
        String normalized = value.trim().replace('-', '_').toUpperCase(Locale.ROOT);
        try {
            return QuestionMediaType.valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Media type must be image or audio");
        }
    }

    public QuestionMediaAttachmentResponse toResponse(QuestionMediaAttachment attachment) {
        if (attachment == null) {
            return null;
        }
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

    private QuestionMediaAttachment storeAsAttachment(
            Question question,
            QuestionMediaType mediaType,
            MultipartFile file,
            int displayOrder,
            String importSource,
            QuestionMediaAttachment existingAttachment
    ) {
        byte[] content = readAndValidateSize(file, maxSize(mediaType), requiredFileMessage(mediaType));
        String contentType = detectContentType(content, mediaType);
        String extension = allowedTypes(mediaType).get(contentType);
        if (extension == null) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_MEDIA_TYPE, unsupportedTypeMessage(mediaType));
        }

        String objectKey = "questions/%s/%s/%s.%s".formatted(
                question.getId(),
                mediaType == QuestionMediaType.IMAGE ? "images" : "audios",
                UUID.randomUUID(),
                extension
        );
        FileStorageService.StoredFile stored = fileStorageService.store(
                storageProperties.getQuestionMediaBucket(),
                objectKey,
                contentType,
                content
        );

        QuestionMediaAttachment attachment = existingAttachment == null ? new QuestionMediaAttachment() : existingAttachment;
        attachment.setQuestionId(question.getId());
        attachment.setMediaType(mediaType);
        attachment.setMediaUrl(stored.url());
        attachment.setObjectKey(stored.objectPath());
        attachment.setBucket(storageProperties.getQuestionMediaBucket());
        attachment.setOriginalFileName(normalizeFileName(file.getOriginalFilename(), stored.fileName()));
        attachment.setContentType(stored.contentType());
        attachment.setFileSize(stored.size());
        attachment.setDisplayOrder(displayOrder);
        attachment.setImportSource(importSource);
        return mediaAttachmentRepository.save(attachment);
    }

    private void normalizeDisplayOrder(UUID questionId, QuestionMediaType mediaType) {
        List<QuestionMediaAttachment> attachments = mediaAttachmentRepository.findByQuestionIdAndMediaTypeOrderByDisplayOrderAsc(questionId, mediaType);
        for (int index = 0; index < attachments.size(); index += 1) {
            attachments.get(index).setDisplayOrder(index + 1000);
        }
        mediaAttachmentRepository.saveAll(attachments);
        mediaAttachmentRepository.flush();
        for (int index = 0; index < attachments.size(); index += 1) {
            attachments.get(index).setDisplayOrder(index + 1);
        }
        mediaAttachmentRepository.saveAll(attachments);
        mediaAttachmentRepository.flush();
    }

    private List<QuestionMediaAttachment> orderedAttachments(UUID questionId) {
        List<QuestionMediaAttachment> attachments = new ArrayList<>();
        attachments.addAll(mediaAttachmentRepository.findByQuestionIdAndMediaTypeOrderByDisplayOrderAsc(questionId, QuestionMediaType.IMAGE));
        attachments.addAll(mediaAttachmentRepository.findByQuestionIdAndMediaTypeOrderByDisplayOrderAsc(questionId, QuestionMediaType.AUDIO));
        return attachments;
    }

    private Question ensureQuestionExists(UUID questionId) {
        return questionRepository.findById(questionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Question not found"));
    }

    private Question findEditableQuestion(UUID questionId) {
        Question question = ensureQuestionExists(questionId);
        if (question.getStatus() == QuestionStatus.ARCHIVED) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "Cannot modify media for an archived question");
        }
        return question;
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
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Question media file could not be read");
        }
    }

    private String detectContentType(byte[] content, QuestionMediaType mediaType) {
        try {
            return tika.detect(content);
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_MEDIA_TYPE, mediaType == QuestionMediaType.IMAGE
                    ? "Question image type could not be detected"
                    : "Question audio type could not be detected");
        }
    }

    private Map<String, String> allowedTypes(QuestionMediaType mediaType) {
        return mediaType == QuestionMediaType.IMAGE ? IMAGE_TYPES : AUDIO_TYPES;
    }

    private DataSize maxSize(QuestionMediaType mediaType) {
        return mediaType == QuestionMediaType.IMAGE
                ? storageProperties.getQuestionImageMaxSize()
                : storageProperties.getQuestionAudioMaxSize();
    }

    private int maxCount(QuestionMediaType mediaType) {
        return mediaType == QuestionMediaType.IMAGE ? MAX_IMAGES_PER_QUESTION : MAX_AUDIOS_PER_QUESTION;
    }

    private String maxCountMessage(QuestionMediaType mediaType) {
        return mediaType == QuestionMediaType.IMAGE
                ? "A question can have at most 5 images"
                : "A question can have at most 3 audio files";
    }

    private String requiredFileMessage(QuestionMediaType mediaType) {
        return mediaType == QuestionMediaType.IMAGE
                ? "Question image file is required"
                : "Question audio file is required";
    }

    private String unsupportedTypeMessage(QuestionMediaType mediaType) {
        return mediaType == QuestionMediaType.IMAGE
                ? "Question image must be JPEG, PNG, or WebP"
                : "Question audio must be MP3, M4A, or WAV";
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
