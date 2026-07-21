package com.smartlearnly.backend.videoai.service;

import com.smartlearnly.backend.classroom.entity.ClassOffering;
import com.smartlearnly.backend.classroom.repository.ClassOfferingRepository;
import com.smartlearnly.backend.course.entity.Course;
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
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoAiAutoPreparationService {
    private static final String VIDEO_ARTIFACTS = "VIDEO_ARTIFACTS";
    private static final long MAX_AUTOMATIC_ATTEMPTS_PER_SOURCE = 3;

    private final VideoAiProperties videoAiProperties;
    private final FasterWhisperProperties transcriptionProperties;
    private final VideoAiGenerationProperties generationProperties;
    private final HlsLessonRepository hlsLessonRepository;
    private final CurriculumLessonRepository lessonRepository;
    private final CurriculumVersionRepository versionRepository;
    private final CourseRepository courseRepository;
    private final ClassOfferingRepository classOfferingRepository;
    private final VideoAiJobRepository jobRepository;

    @Transactional
    public void enqueueAfterVideoReady(UUID lessonId, UUID sourceVersion) {
        if (!runtimeReady()) {
            log.info("Skipping automatic AI preparation because the AI runtime is not configured");
            return;
        }

        HlsLesson hls = hlsLessonRepository.findByLessonId(lessonId).orElse(null);
        if (hls == null || !hls.isReady() || !sourceVersion.equals(hls.getProcessingJobId())
                || hls.getAiAudioObjectKey() == null || hls.getAiAudioObjectKey().isBlank()) {
            log.info("Skipping automatic AI preparation for lesson {} because its source is not ready", lessonId);
            return;
        }

        CurriculumLesson lesson = lessonRepository.findById(lessonId).orElse(null);
        if (lesson == null || lesson.getType() != LessonType.VIDEO) {
            log.info("Skipping automatic AI preparation for non-curriculum video lesson {}", lessonId);
            return;
        }
        CurriculumVersion version = versionRepository.findById(lesson.getCurriculumVersionId()).orElse(null);
        if (version == null) return;

        UUID classId = version.getScope() == CurriculumScope.CLASS ? version.getClassId() : null;
        String scope = classId == null ? "MASTER" : "CLASS";
        Optional<VideoAiJob> latest = jobRepository.findLatestForSource(
                lessonId, scope, classId, sourceVersion, VIDEO_ARTIFACTS, PageRequest.of(0, 1))
                .stream().findFirst();
        if (latest.isPresent()) {
            if (!"failed".equals(latest.get().getStatus())) return;
            long previousAttempts = jobRepository.countForSource(
                    lessonId, scope, classId, sourceVersion, VIDEO_ARTIFACTS);
            if (previousAttempts >= MAX_AUTOMATIC_ATTEMPTS_PER_SOURCE) {
                log.warn("Automatic AI preparation retry limit reached for lesson {}", lessonId);
                return;
            }
        }

        UUID requestedBy = resolveRequester(version);
        if (requestedBy == null) {
            log.warn("Could not determine an owner for automatic AI preparation of lesson {}", lessonId);
            return;
        }

        VideoAiJob job = new VideoAiJob();
        job.setLessonId(lessonId);
        job.setLessonScope(scope);
        job.setCourseId(version.getCourseId());
        job.setClassId(classId);
        job.setSourceVersion(sourceVersion);
        job.setJobType(VIDEO_ARTIFACTS);
        job.setSourceLanguage("auto");
        job.setRequestedBy(requestedBy);
        try {
            jobRepository.saveAndFlush(job);
            log.info("Queued automatic AI preparation for lesson {} and source {}", lessonId, sourceVersion);
        } catch (DataIntegrityViolationException conflict) {
            log.info("Automatic AI preparation is already queued for lesson {}", lessonId);
        }
    }

    private UUID resolveRequester(CurriculumVersion version) {
        if (version.getCreatedBy() != null) return version.getCreatedBy();
        if (version.getClassId() != null) {
            ClassOffering offering = classOfferingRepository.findByIdAndDeletedAtIsNull(version.getClassId()).orElse(null);
            if (offering != null && offering.getTrainerId() != null) return offering.getTrainerId();
            if (offering != null) return offering.getCreatedBy();
        }
        Course course = courseRepository.findByIdAndDeletedAtIsNull(version.getCourseId()).orElse(null);
        return course == null || course.getCreator() == null ? null : course.getCreator().getId();
    }

    private boolean runtimeReady() {
        return videoAiProperties.isEnabled()
                && transcriptionProperties.isEnabled()
                && "faster-whisper".equalsIgnoreCase(transcriptionProperties.getProvider())
                && generationProperties.isEnabled()
                && generationProperties.getApiKey() != null
                && !generationProperties.getApiKey().isBlank();
    }
}
