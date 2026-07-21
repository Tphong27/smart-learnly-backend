package com.smartlearnly.backend.videoai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.classroom.repository.ClassOfferingRepository;
import com.smartlearnly.backend.course.repository.CourseRepository;
import com.smartlearnly.backend.curriculum.entity.CurriculumLesson;
import com.smartlearnly.backend.curriculum.entity.CurriculumScope;
import com.smartlearnly.backend.curriculum.entity.CurriculumVersion;
import com.smartlearnly.backend.curriculum.repository.CurriculumLessonRepository;
import com.smartlearnly.backend.curriculum.repository.CurriculumVersionRepository;
import com.smartlearnly.backend.hls.entity.HlsLesson;
import com.smartlearnly.backend.hls.repository.HlsLessonRepository;
import com.smartlearnly.backend.learning.lesson.entity.LessonType;
import com.smartlearnly.backend.videoai.config.VideoAiProperties;
import com.smartlearnly.backend.videoai.entity.VideoAiJob;
import com.smartlearnly.backend.videoai.generation.VideoAiGenerationProperties;
import com.smartlearnly.backend.videoai.repository.VideoAiJobRepository;
import com.smartlearnly.backend.videoai.transcription.FasterWhisperProperties;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VideoAiAutoPreparationServiceTest {
    @Mock HlsLessonRepository hlsLessonRepository;
    @Mock CurriculumLessonRepository lessonRepository;
    @Mock CurriculumVersionRepository versionRepository;
    @Mock CourseRepository courseRepository;
    @Mock ClassOfferingRepository classOfferingRepository;
    @Mock VideoAiJobRepository jobRepository;

    private VideoAiAutoPreparationService service;

    @BeforeEach
    void setUp() {
        VideoAiProperties videoAi = new VideoAiProperties();
        videoAi.setEnabled(true);
        FasterWhisperProperties transcription = new FasterWhisperProperties();
        transcription.setEnabled(true);
        VideoAiGenerationProperties generation = new VideoAiGenerationProperties();
        generation.setEnabled(true);
        generation.setApiKey("configured");
        service = new VideoAiAutoPreparationService(
                videoAi,
                transcription,
                generation,
                hlsLessonRepository,
                lessonRepository,
                versionRepository,
                courseRepository,
                classOfferingRepository,
                jobRepository);
    }

    @Test
    void queuesTranscriptAndLearningAidJobWhenVideoBecomesReady() {
        UUID lessonId = UUID.randomUUID();
        UUID sourceVersion = UUID.randomUUID();
        UUID curriculumVersionId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID requestedBy = UUID.randomUUID();

        HlsLesson hls = new HlsLesson();
        hls.setLessonId(lessonId);
        hls.setHlsStatus("ready");
        hls.setProcessingJobId(sourceVersion);
        hls.setAiAudioObjectKey("hls/lesson/source/ai/source.mp3");
        CurriculumLesson lesson = new CurriculumLesson();
        lesson.setId(lessonId);
        lesson.setCurriculumVersionId(curriculumVersionId);
        lesson.setType(LessonType.VIDEO);
        CurriculumVersion version = new CurriculumVersion();
        version.setId(curriculumVersionId);
        version.setCourseId(courseId);
        version.setScope(CurriculumScope.MASTER);
        version.setCreatedBy(requestedBy);

        when(hlsLessonRepository.findByLessonId(lessonId)).thenReturn(Optional.of(hls));
        when(lessonRepository.findById(lessonId)).thenReturn(Optional.of(lesson));
        when(versionRepository.findById(curriculumVersionId)).thenReturn(Optional.of(version));
        when(jobRepository.findLatestForSource(
                lessonId, "MASTER", null, sourceVersion, "VIDEO_ARTIFACTS",
                org.springframework.data.domain.PageRequest.of(0, 1)))
                .thenReturn(List.of());

        service.enqueueAfterVideoReady(lessonId, sourceVersion);

        ArgumentCaptor<VideoAiJob> captor = ArgumentCaptor.forClass(VideoAiJob.class);
        verify(jobRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getLessonId()).isEqualTo(lessonId);
        assertThat(captor.getValue().getSourceVersion()).isEqualTo(sourceVersion);
        assertThat(captor.getValue().getRequestedBy()).isEqualTo(requestedBy);
        assertThat(captor.getValue().getJobType()).isEqualTo("VIDEO_ARTIFACTS");
    }

    @Test
    void retriesOneFailedAutomaticPreparationOnlyOnce() {
        UUID lessonId = UUID.randomUUID();
        UUID sourceVersion = UUID.randomUUID();
        UUID curriculumVersionId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID requestedBy = UUID.randomUUID();

        HlsLesson hls = readyHls(lessonId, sourceVersion);
        CurriculumLesson lesson = videoLesson(lessonId, curriculumVersionId);
        CurriculumVersion version = masterVersion(curriculumVersionId, courseId, requestedBy);
        VideoAiJob failed = new VideoAiJob();
        failed.setStatus("failed");

        when(hlsLessonRepository.findByLessonId(lessonId)).thenReturn(Optional.of(hls));
        when(lessonRepository.findById(lessonId)).thenReturn(Optional.of(lesson));
        when(versionRepository.findById(curriculumVersionId)).thenReturn(Optional.of(version));
        when(jobRepository.findLatestForSource(
                lessonId, "MASTER", null, sourceVersion, "VIDEO_ARTIFACTS",
                org.springframework.data.domain.PageRequest.of(0, 1)))
                .thenReturn(List.of(failed));
        when(jobRepository.countForSource(
                lessonId, "MASTER", null, sourceVersion, "VIDEO_ARTIFACTS"))
                .thenReturn(1L);

        service.enqueueAfterVideoReady(lessonId, sourceVersion);

        verify(jobRepository).saveAndFlush(org.mockito.ArgumentMatchers.any(VideoAiJob.class));
    }

    @Test
    void stopsRetryingAfterThreeFailedJobsForTheSameVideo() {
        UUID lessonId = UUID.randomUUID();
        UUID sourceVersion = UUID.randomUUID();
        UUID curriculumVersionId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID requestedBy = UUID.randomUUID();

        HlsLesson hls = readyHls(lessonId, sourceVersion);
        CurriculumLesson lesson = videoLesson(lessonId, curriculumVersionId);
        CurriculumVersion version = masterVersion(curriculumVersionId, courseId, requestedBy);
        VideoAiJob failed = new VideoAiJob();
        failed.setStatus("failed");

        when(hlsLessonRepository.findByLessonId(lessonId)).thenReturn(Optional.of(hls));
        when(lessonRepository.findById(lessonId)).thenReturn(Optional.of(lesson));
        when(versionRepository.findById(curriculumVersionId)).thenReturn(Optional.of(version));
        when(jobRepository.findLatestForSource(
                lessonId, "MASTER", null, sourceVersion, "VIDEO_ARTIFACTS",
                org.springframework.data.domain.PageRequest.of(0, 1)))
                .thenReturn(List.of(failed));
        when(jobRepository.countForSource(
                lessonId, "MASTER", null, sourceVersion, "VIDEO_ARTIFACTS"))
                .thenReturn(3L);

        service.enqueueAfterVideoReady(lessonId, sourceVersion);

        verify(jobRepository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any(VideoAiJob.class));
    }

    private HlsLesson readyHls(UUID lessonId, UUID sourceVersion) {
        HlsLesson hls = new HlsLesson();
        hls.setLessonId(lessonId);
        hls.setHlsStatus("ready");
        hls.setProcessingJobId(sourceVersion);
        hls.setAiAudioObjectKey("hls/lesson/source/ai/source.mp3");
        return hls;
    }

    private CurriculumLesson videoLesson(UUID lessonId, UUID curriculumVersionId) {
        CurriculumLesson lesson = new CurriculumLesson();
        lesson.setId(lessonId);
        lesson.setCurriculumVersionId(curriculumVersionId);
        lesson.setType(LessonType.VIDEO);
        return lesson;
    }

    private CurriculumVersion masterVersion(
            UUID curriculumVersionId, UUID courseId, UUID requestedBy) {
        CurriculumVersion version = new CurriculumVersion();
        version.setId(curriculumVersionId);
        version.setCourseId(courseId);
        version.setScope(CurriculumScope.MASTER);
        version.setCreatedBy(requestedBy);
        return version;
    }
}
