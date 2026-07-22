package com.smartlearnly.backend.videoai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.course.entity.Course;
import com.smartlearnly.backend.course.repository.CourseRepository;
import com.smartlearnly.backend.course.service.CourseAccessService;
import com.smartlearnly.backend.curriculum.entity.CurriculumLesson;
import com.smartlearnly.backend.curriculum.entity.CurriculumScope;
import com.smartlearnly.backend.curriculum.entity.CurriculumSection;
import com.smartlearnly.backend.curriculum.entity.CurriculumVersion;
import com.smartlearnly.backend.curriculum.repository.CurriculumLessonRepository;
import com.smartlearnly.backend.curriculum.service.TrainerClassCurriculumService;
import com.smartlearnly.backend.flashcard.repository.FlashcardSetRepository;
import com.smartlearnly.backend.hls.entity.HlsLesson;
import com.smartlearnly.backend.hls.repository.HlsLessonRepository;
import com.smartlearnly.backend.learning.lesson.entity.LessonType;
import com.smartlearnly.backend.user.entity.UserAccount;
import com.smartlearnly.backend.videoai.config.VideoAiProperties;
import com.smartlearnly.backend.videoai.dto.VideoAiDtos.GenerateJobRequest;
import com.smartlearnly.backend.videoai.dto.VideoAiDtos.JobResponse;
import com.smartlearnly.backend.videoai.dto.VideoAiDtos.StatusResponse;
import com.smartlearnly.backend.videoai.entity.VideoAiContent;
import com.smartlearnly.backend.videoai.entity.VideoAiJob;
import com.smartlearnly.backend.videoai.entity.VideoAiTranscriptSegment;
import com.smartlearnly.backend.videoai.generation.VideoAiGenerationProperties;
import com.smartlearnly.backend.videoai.repository.VideoAiContentRepository;
import com.smartlearnly.backend.videoai.repository.VideoAiJobRepository;
import com.smartlearnly.backend.videoai.transcription.FasterWhisperProperties;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

class VideoAiAuthoringServiceTest {
    private final CourseAccessService courseAccessService = mock(CourseAccessService.class);
    private final TrainerClassCurriculumService trainerCurriculumService = mock(TrainerClassCurriculumService.class);
    private final CurriculumLessonRepository lessonRepository = mock(CurriculumLessonRepository.class);
    private final CourseRepository courseRepository = mock(CourseRepository.class);
    private final HlsLessonRepository hlsRepository = mock(HlsLessonRepository.class);
    private final VideoAiJobRepository jobRepository = mock(VideoAiJobRepository.class);
    private final VideoAiContentRepository contentRepository = mock(VideoAiContentRepository.class);
    private final CurrentUserService currentUserService = mock(CurrentUserService.class);
    private VideoAiAuthoringService service;

    @BeforeEach
    void setUp() {
        VideoAiProperties videoAi = new VideoAiProperties();
        videoAi.setEnabled(true);
        FasterWhisperProperties transcription = new FasterWhisperProperties();
        transcription.setEnabled(true);
        transcription.setProvider("faster-whisper");
        VideoAiGenerationProperties generation = new VideoAiGenerationProperties();
        generation.setEnabled(true);
        generation.setApiKey("configured");
        service = new VideoAiAuthoringService(
                videoAi,
                transcription,
                generation,
                courseAccessService,
                trainerCurriculumService,
                lessonRepository,
                courseRepository,
                hlsRepository,
                jobRepository,
                contentRepository,
                mock(FlashcardSetRepository.class),
                currentUserService,
                new ObjectMapper());
    }

    @Test
    void statusSeparatesCompletedTranscriptFromAiSuggestions() {
        Fixture fixture = fixture();
        stubContext(fixture);
        when(jobRepository.findLatestForSource(
                fixture.lessonId(), "MASTER", null, fixture.sourceVersion(),
                "VIDEO_TRANSCRIPT", PageRequest.of(0, 1)))
                .thenReturn(List.of());
        when(jobRepository.findLatestForSource(
                fixture.lessonId(), "MASTER", null, fixture.sourceVersion(),
                "VIDEO_ARTIFACTS", PageRequest.of(0, 1)))
                .thenReturn(List.of());

        StatusResponse response = service.adminStatus(fixture.courseId(), fixture.lessonId());

        assertThat(response.hlsReady()).isTrue();
        assertThat(response.transcriptReady()).isTrue();
        assertThat(response.suggestionsReady()).isFalse();
        assertThat(response.eligible()).isTrue();
    }

    @Test
    void clickingAiStartsGenerationFromExistingTranscriptWithoutRetranscribing() {
        Fixture fixture = fixture();
        stubContext(fixture);
        UserAccount actor = new UserAccount();
        actor.setId(UUID.randomUUID());
        when(currentUserService.requireAuthenticatedUser()).thenReturn(actor);
        when(jobRepository.findActive(
                fixture.lessonId(), "MASTER", null, fixture.sourceVersion(),
                "VIDEO_ARTIFACTS", PageRequest.of(0, 1)))
                .thenReturn(List.of());
        when(jobRepository.saveAndFlush(any(VideoAiJob.class))).thenAnswer(invocation -> {
            VideoAiJob job = invocation.getArgument(0);
            job.setId(UUID.randomUUID());
            return job;
        });

        JobResponse response = service.createAdminJob(
                fixture.courseId(), fixture.lessonId(), new GenerateJobRequest("auto"));

        assertThat(response.jobType()).isEqualTo("VIDEO_ARTIFACTS");
        assertThat(response.contentId()).isEqualTo(fixture.content().getId());
    }

    private void stubContext(Fixture fixture) {
        when(courseRepository.findByIdAndDeletedAtIsNull(fixture.courseId()))
                .thenReturn(Optional.of(new Course()));
        when(lessonRepository.findById(fixture.lessonId()))
                .thenReturn(Optional.of(fixture.lesson()));
        when(hlsRepository.findByLessonId(fixture.lessonId()))
                .thenReturn(Optional.of(fixture.hls()));
        when(contentRepository
                .findFirstByLessonIdAndLessonScopeAndClassIdIsNullAndSourceVersionOrderByUpdatedAtDesc(
                        fixture.lessonId(), "MASTER", fixture.sourceVersion()))
                .thenReturn(Optional.of(fixture.content()));
    }

    private Fixture fixture() {
        UUID courseId = UUID.randomUUID();
        UUID lessonId = UUID.randomUUID();
        UUID sourceVersion = UUID.randomUUID();
        CurriculumVersion version = new CurriculumVersion();
        version.setCourseId(courseId);
        version.setScope(CurriculumScope.MASTER);
        CurriculumSection section = new CurriculumSection();
        section.setCurriculumVersion(version);
        CurriculumLesson lesson = new CurriculumLesson();
        lesson.setId(lessonId);
        lesson.setType(LessonType.VIDEO);
        lesson.setSection(section);
        HlsLesson hls = new HlsLesson();
        hls.setLessonId(lessonId);
        hls.setHlsStatus("ready");
        hls.setProcessingJobId(sourceVersion);
        hls.setAiAudioObjectKey("hls/lesson/source/ai/source.mp3");
        VideoAiContent content = new VideoAiContent();
        content.setId(UUID.randomUUID());
        content.setTranscriptText("A complete transcript ready for AI suggestions.");
        VideoAiTranscriptSegment segment = new VideoAiTranscriptSegment();
        segment.setSegmentIndex(0);
        segment.setStartMs(0L);
        segment.setEndMs(2_000L);
        segment.setText("A complete transcript ready for AI suggestions.");
        content.addSegment(segment);
        return new Fixture(courseId, lessonId, sourceVersion, lesson, hls, content);
    }

    private record Fixture(
            UUID courseId,
            UUID lessonId,
            UUID sourceVersion,
            CurriculumLesson lesson,
            HlsLesson hls,
            VideoAiContent content) {
    }
}
