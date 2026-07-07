package com.smartlearnly.backend.question.service;

import com.smartlearnly.backend.question.dto.QuestionImageResponse;
import com.smartlearnly.backend.question.dto.QuestionMediaAttachmentResponse;
import com.smartlearnly.backend.question.entity.QuestionMediaType;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class QuestionImageService {
    private final QuestionMediaService questionMediaService;

    @Transactional
    public QuestionImageResponse uploadOrReplace(UUID questionId, MultipartFile file) {
        QuestionMediaAttachmentResponse media = questionMediaService.replacePrimary(questionId, QuestionMediaType.IMAGE, file);
        return new QuestionImageResponse(questionId, media.mediaUrl(), media.contentType(), media.size(), media.fileName());
    }

    @Transactional
    public QuestionImageResponse remove(UUID questionId) {
        QuestionMediaAttachmentResponse media = questionMediaService.removePrimary(questionId, QuestionMediaType.IMAGE);
        if (media == null) {
            return new QuestionImageResponse(questionId, null, null, 0, null);
        }
        return new QuestionImageResponse(questionId, media.mediaUrl(), media.contentType(), media.size(), media.fileName());
    }
}
