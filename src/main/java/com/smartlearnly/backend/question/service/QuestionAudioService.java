package com.smartlearnly.backend.question.service;

import com.smartlearnly.backend.question.dto.QuestionAudioResponse;
import com.smartlearnly.backend.question.dto.QuestionMediaAttachmentResponse;
import com.smartlearnly.backend.question.entity.QuestionMediaType;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class QuestionAudioService {
    private final QuestionMediaService questionMediaService;

    @Transactional
    public QuestionAudioResponse uploadOrReplace(UUID questionId, MultipartFile file) {
        QuestionMediaAttachmentResponse media = questionMediaService.replacePrimary(questionId, QuestionMediaType.AUDIO, file);
        return new QuestionAudioResponse(questionId, media.mediaUrl(), media.contentType(), media.size(), media.fileName());
    }

    @Transactional
    public QuestionAudioResponse remove(UUID questionId) {
        QuestionMediaAttachmentResponse media = questionMediaService.removePrimary(questionId, QuestionMediaType.AUDIO);
        if (media == null) {
            return new QuestionAudioResponse(questionId, null, null, 0, null);
        }
        return new QuestionAudioResponse(questionId, media.mediaUrl(), media.contentType(), media.size(), media.fileName());
    }
}
