package com.smartlearnly.backend.hls.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.curriculum.repository.CurriculumLessonRepository;
import com.smartlearnly.backend.file.service.CloudflareR2StorageClient;
import com.smartlearnly.backend.hls.config.HlsProperties;
import com.smartlearnly.backend.hls.dto.HlsProcessingCallbackRequest;
import com.smartlearnly.backend.hls.entity.HlsLesson;
import com.smartlearnly.backend.hls.repository.HlsLessonRepository;
import com.smartlearnly.backend.learning.lesson.entity.Lesson;
import com.smartlearnly.backend.learning.lesson.repository.LessonRepository;
import com.smartlearnly.backend.videoai.service.VideoAiAutoPreparationService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HlsProcessingStateServiceTest {

    @Mock
    HlsLessonRepository hlsRepository;
    @Mock
    LessonRepository lessonRepository;
    @Mock
    CurriculumLessonRepository curriculumLessonRepository;
    @Mock
    CloudflareR2StorageClient r2StorageClient;
    @Mock
    VideoAiAutoPreparationService videoAiAutoPreparationService;

    HlsProperties properties;
    HlsProcessingStateService service;

    @BeforeEach
    void setUp() {
        properties = new HlsProperties();
        properties.setOutputBucket("private-hls");
        service = new HlsProcessingStateService(
                hlsRepository,
                lessonRepository,
                curriculumLessonRepository,
                properties,
                r2StorageClient,
                videoAiAutoPreparationService
        );
    }

    @Test
    void readyCallbackPersistsVersionedPrivateAudioDerivative() {
        UUID lessonId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        String prefix = "hls/" + lessonId + "/" + jobId;
        HlsLesson hls = processingLesson(lessonId, jobId, prefix);
        when(lessonRepository.findById(lessonId)).thenReturn(Optional.of(mock(Lesson.class)));
        when(hlsRepository.findByLessonId(lessonId)).thenReturn(Optional.of(hls));
        HlsProcessingCallbackRequest request = new HlsProcessingCallbackRequest(
                jobId,
                lessonId,
                "ready",
                prefix,
                prefix + "/master.m3u8",
                List.of("480p", "720p"),
                prefix + "/ai/source.mp3",
                123_456L,
                null
        );

        service.applyCallback(request);

        assertThat(hls.getHlsStatus()).isEqualTo("ready");
        assertThat(hls.getAiAudioObjectKey()).isEqualTo(prefix + "/ai/source.mp3");
        assertThat(hls.getAiAudioDurationMs()).isEqualTo(123_456L);
        assertThat(hls.getProcessingCompletedAt()).isNotNull();
        verify(hlsRepository).save(hls);
        verify(videoAiAutoPreparationService).enqueueAfterVideoReady(lessonId, jobId);
    }

    @Test
    void callbackRejectsAudioFromAnotherSourceVersion() {
        UUID lessonId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        String prefix = "hls/" + lessonId + "/" + jobId;
        HlsLesson hls = processingLesson(lessonId, jobId, prefix);
        when(lessonRepository.findById(lessonId)).thenReturn(Optional.of(mock(Lesson.class)));
        when(hlsRepository.findByLessonId(lessonId)).thenReturn(Optional.of(hls));
        HlsProcessingCallbackRequest request = new HlsProcessingCallbackRequest(
                jobId,
                lessonId,
                "ready",
                prefix,
                prefix + "/master.m3u8",
                List.of("720p"),
                "hls/another-version/ai/source.mp3",
                20_000L,
                null
        );

        assertThatThrownBy(() -> service.applyCallback(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Callback AI audio does not match reserved job");
        assertThat(hls.getHlsStatus()).isEqualTo("processing");
    }

    private HlsLesson processingLesson(UUID lessonId, UUID jobId, String prefix) {
        HlsLesson hls = new HlsLesson();
        hls.setLessonId(lessonId);
        hls.setProcessingJobId(jobId);
        hls.setProcessingOutputPrefix(prefix);
        hls.setHlsStatus("processing");
        return hls;
    }
}
