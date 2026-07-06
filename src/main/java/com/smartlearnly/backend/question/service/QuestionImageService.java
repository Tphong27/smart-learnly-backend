package com.smartlearnly.backend.question.service;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.file.config.StorageProperties;
import com.smartlearnly.backend.file.service.FileStorageService;
import com.smartlearnly.backend.question.dto.QuestionImageResponse;
import com.smartlearnly.backend.question.entity.Question;
import com.smartlearnly.backend.question.entity.QuestionStatus;
import com.smartlearnly.backend.question.repository.QuestionRepository;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class QuestionImageService {
    private static final Map<String, String> ALLOWED_TYPES = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp"
    );

    private final QuestionRepository questionRepository;
    private final FileStorageService fileStorageService;
    private final StorageProperties storageProperties;
    private final Tika tika = new Tika();

    @Transactional
    public QuestionImageResponse uploadOrReplace(UUID questionId, MultipartFile file) {
        Question question = findEditableQuestion(questionId);
        byte[] content = readAndValidateSize(file);
        String contentType = detectContentType(content);
        String extension = ALLOWED_TYPES.get(contentType);
        if (extension == null) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_MEDIA_TYPE, "Question image must be JPEG, PNG, or WebP");
        }

        String objectKey = "questions/%s/%s.%s".formatted(question.getId(), UUID.randomUUID(), extension);
        FileStorageService.StoredFile stored = fileStorageService.store(
                storageProperties.getQuestionImageBucket(),
                objectKey,
                contentType,
                content
        );
        question.setImageUrl(stored.url());
        question.setImageObjectKey(stored.objectPath());
        questionRepository.save(question);
        return new QuestionImageResponse(question.getId(), stored.url(), stored.contentType(), stored.size(), stored.fileName());
    }

    @Transactional
    public QuestionImageResponse remove(UUID questionId) {
        Question question = findEditableQuestion(questionId);
        question.setImageUrl(null);
        question.setImageObjectKey(null);
        questionRepository.save(question);
        return new QuestionImageResponse(question.getId(), null, null, 0, null);
    }

    private Question findEditableQuestion(UUID questionId) {
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Question not found"));
        if (question.getStatus() == QuestionStatus.ARCHIVED) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "Cannot modify image for an archived question");
        }
        return question;
    }

    private byte[] readAndValidateSize(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Question image file is required");
        }
        if (file.getSize() > storageProperties.getQuestionImageMaxSize().toBytes()) {
            throw new BusinessException(ErrorCode.PAYLOAD_TOO_LARGE);
        }
        try {
            byte[] content = file.getBytes();
            if (content.length == 0) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST, "Question image file is required");
            }
            return content;
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Question image file could not be read");
        }
    }

    private String detectContentType(byte[] content) {
        try {
            return tika.detect(content);
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_MEDIA_TYPE, "Question image type could not be detected");
        }
    }
}
