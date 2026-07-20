package com.smartlearnly.backend.videoai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.course.repository.CourseRepository;
import com.smartlearnly.backend.course.service.CourseAccessService;
import com.smartlearnly.backend.curriculum.entity.CurriculumLesson;
import com.smartlearnly.backend.curriculum.entity.CurriculumScope;
import com.smartlearnly.backend.curriculum.repository.CurriculumLessonRepository;
import com.smartlearnly.backend.curriculum.service.TrainerClassCurriculumService;
import com.smartlearnly.backend.flashcard.entity.FlashcardSet;
import com.smartlearnly.backend.flashcard.repository.FlashcardSetRepository;
import com.smartlearnly.backend.hls.entity.HlsLesson;
import com.smartlearnly.backend.hls.repository.HlsLessonRepository;
import com.smartlearnly.backend.learning.lesson.entity.LessonType;
import com.smartlearnly.backend.videoai.config.VideoAiProperties;
import com.smartlearnly.backend.videoai.generation.VideoAiGenerationProperties;
import com.smartlearnly.backend.videoai.transcription.FasterWhisperProperties;
import com.smartlearnly.backend.videoai.dto.VideoAiDtos.ChapterRequest;
import com.smartlearnly.backend.videoai.dto.VideoAiDtos.ChapterResponse;
import com.smartlearnly.backend.videoai.dto.VideoAiDtos.ContentResponse;
import com.smartlearnly.backend.videoai.dto.VideoAiDtos.FlashcardTargetResponse;
import com.smartlearnly.backend.videoai.dto.VideoAiDtos.GenerateJobRequest;
import com.smartlearnly.backend.videoai.dto.VideoAiDtos.GenerateFlashcardsRequest;
import com.smartlearnly.backend.videoai.dto.VideoAiDtos.JobResponse;
import com.smartlearnly.backend.videoai.dto.VideoAiDtos.PublishContentRequest;
import com.smartlearnly.backend.videoai.dto.VideoAiDtos.SaveContentRequest;
import com.smartlearnly.backend.videoai.dto.VideoAiDtos.StatusResponse;
import com.smartlearnly.backend.videoai.dto.VideoAiDtos.TranscriptSegmentRequest;
import com.smartlearnly.backend.videoai.dto.VideoAiDtos.TranscriptSegmentResponse;
import com.smartlearnly.backend.videoai.entity.VideoAiChapter;
import com.smartlearnly.backend.videoai.entity.VideoAiContent;
import com.smartlearnly.backend.videoai.entity.VideoAiJob;
import com.smartlearnly.backend.videoai.entity.VideoAiTranscriptSegment;
import com.smartlearnly.backend.videoai.repository.VideoAiContentRepository;
import com.smartlearnly.backend.videoai.repository.VideoAiJobRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VideoAiAuthoringService {
    private static final String MASTER = "MASTER";
    private static final String CLASS = "CLASS";
    private static final String VIDEO_ARTIFACTS = "VIDEO_ARTIFACTS";

    private final VideoAiProperties properties;
    private final FasterWhisperProperties transcriptionProperties;
    private final VideoAiGenerationProperties generationProperties;
    private final CourseAccessService courseAccessService;
    private final TrainerClassCurriculumService trainerClassCurriculumService;
    private final CurriculumLessonRepository curriculumLessonRepository;
    private final CourseRepository courseRepository;
    private final HlsLessonRepository hlsLessonRepository;
    private final VideoAiJobRepository jobRepository;
    private final VideoAiContentRepository contentRepository;
    private final FlashcardSetRepository flashcardSetRepository;
    private final CurrentUserService currentUserService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public StatusResponse adminStatus(UUID courseId, UUID lessonId) {
        return status(resolveMaster(courseId, lessonId));
    }

    @Transactional(readOnly = true)
    public StatusResponse trainerStatus(UUID classId, UUID lessonId) {
        return status(resolveTrainer(classId, lessonId, false));
    }

    @Transactional
    public JobResponse createAdminJob(UUID courseId, UUID lessonId, GenerateJobRequest request) {
        return createJob(resolveMaster(courseId, lessonId), request);
    }

    @Transactional
    public JobResponse createTrainerJob(UUID classId, UUID lessonId, GenerateJobRequest request) {
        return createJob(resolveTrainer(classId, lessonId, true), request);
    }

    @Transactional(readOnly = true)
    public ContentResponse getAdminCurrent(UUID courseId, UUID lessonId) {
        return toContent(requireCurrent(resolveMaster(courseId, lessonId)));
    }

    @Transactional(readOnly = true)
    public ContentResponse getTrainerCurrent(UUID classId, UUID lessonId) {
        return toContent(requireCurrent(resolveTrainer(classId, lessonId, false)));
    }

    @Transactional
    public ContentResponse saveAdmin(UUID courseId, UUID lessonId, UUID contentId, SaveContentRequest request) {
        return save(resolveMaster(courseId, lessonId), contentId, request);
    }

    @Transactional
    public ContentResponse saveTrainer(UUID classId, UUID lessonId, UUID contentId, SaveContentRequest request) {
        return save(resolveTrainer(classId, lessonId, true), contentId, request);
    }

    @Transactional
    public ContentResponse publishAdmin(UUID courseId, UUID lessonId, UUID contentId, PublishContentRequest request) {
        return publish(resolveMaster(courseId, lessonId), contentId, request);
    }

    @Transactional
    public ContentResponse publishTrainer(UUID classId, UUID lessonId, UUID contentId, PublishContentRequest request) {
        return publish(resolveTrainer(classId, lessonId, true), contentId, request);
    }

    @Transactional
    public JobResponse retryAdmin(UUID courseId, UUID lessonId, UUID jobId) {
        return retry(resolveMaster(courseId, lessonId), jobId);
    }

    @Transactional
    public JobResponse retryTrainer(UUID classId, UUID lessonId, UUID jobId) {
        return retry(resolveTrainer(classId, lessonId, true), jobId);
    }

    @Transactional(readOnly = true)
    public JobResponse getAdminJob(UUID courseId, UUID lessonId, UUID jobId) {
        return getJob(resolveMaster(courseId, lessonId), jobId);
    }

    @Transactional(readOnly = true)
    public JobResponse getTrainerJob(UUID classId, UUID lessonId, UUID jobId) {
        return getJob(resolveTrainer(classId, lessonId, false), jobId);
    }

    @Transactional(readOnly = true)
    public List<FlashcardTargetResponse> adminFlashcardTargets(UUID courseId, UUID lessonId) {
        return flashcardTargets(resolveMaster(courseId, lessonId));
    }

    @Transactional(readOnly = true)
    public List<FlashcardTargetResponse> trainerFlashcardTargets(UUID classId, UUID lessonId) {
        return flashcardTargets(resolveTrainer(classId, lessonId, false));
    }

    @Transactional
    public JobResponse createAdminFlashcardJob(
            UUID courseId, UUID lessonId, UUID contentId, GenerateFlashcardsRequest request) {
        return createFlashcardJob(resolveMaster(courseId, lessonId), contentId, request);
    }

    @Transactional
    public JobResponse createTrainerFlashcardJob(
            UUID classId, UUID lessonId, UUID contentId, GenerateFlashcardsRequest request) {
        return createFlashcardJob(resolveTrainer(classId, lessonId, true), contentId, request);
    }

    private StatusResponse status(LessonContext context) {
        Optional<HlsLesson> hls = hlsLessonRepository.findByLessonId(context.lesson().getId());
        boolean ready = hls.map(HlsLesson::isReady).orElse(false);
        UUID sourceVersion = hls.map(HlsLesson::getProcessingJobId).orElse(null);
        boolean audioReady = hls.map(value -> value.getAiAudioObjectKey() != null
                && !value.getAiAudioObjectKey().isBlank()).orElse(false);
        String reason = availabilityReason();
        if (reason == null && !ready) reason = "HLS_NOT_READY";
        else if (reason == null && sourceVersion == null) reason = "SOURCE_VERSION_MISSING";
        else if (reason == null && !audioReady) reason = "AI_AUDIO_NOT_READY";
        VideoAiJob job = sourceVersion == null ? null : latestJob(context, sourceVersion).orElse(null);
        VideoAiContent content = sourceVersion == null ? null : current(context, sourceVersion).orElse(null);
        return new StatusResponse(
                properties.isEnabled(), reason == null, reason, ready, sourceVersion,
                job == null ? null : toJob(job),
                content == null ? null : content.getId(),
                content == null ? null : content.getStatus(),
                content == null ? null : content.getRevision());
    }

    private JobResponse createJob(LessonContext context, GenerateJobRequest request) {
        String unavailable = availabilityReason();
        if (unavailable != null) {
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE, "Video AI is not configured: " + unavailable);
        }
        HlsLesson hls = requireEligibleHls(context.lesson().getId());
        Optional<VideoAiJob> active = activeJob(context, hls.getProcessingJobId());
        if (active.isPresent()) return toJob(active.get());
        VideoAiJob job = new VideoAiJob();
        job.setLessonId(context.lesson().getId());
        job.setLessonScope(context.scope());
        job.setCourseId(context.courseId());
        job.setClassId(context.classId());
        job.setSourceVersion(hls.getProcessingJobId());
        job.setJobType(VIDEO_ARTIFACTS);
        job.setSourceLanguage(normalizeLanguage(request == null ? null : request.sourceLanguage()));
        job.setRequestedBy(currentUserService.requireAuthenticatedUser().getId());
        try {
            return toJob(jobRepository.saveAndFlush(job));
        } catch (DataIntegrityViolationException conflict) {
            return activeJob(context, hls.getProcessingJobId()).map(this::toJob)
                    .orElseThrow(() -> new BusinessException(ErrorCode.CONFLICT,
                            "A Video AI job is already active"));
        }
    }

    private JobResponse createFlashcardJob(
            LessonContext context, UUID contentId, GenerateFlashcardsRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Flashcard generation request is required");
        }
        VideoAiContent content = requireContent(context, contentId);
        HlsLesson hls = requireEligibleHls(context.lesson().getId());
        if (!content.getSourceVersion().equals(hls.getProcessingJobId())
                || normalize(content.getTranscriptText()) == null) {
            throw new BusinessException(ErrorCode.CONFLICT, "Video AI content is stale or incomplete");
        }
        CurriculumLesson target = curriculumLessonRepository.findById(request.targetLessonId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Flashcard target lesson was not found"));
        if (!context.lesson().getCurriculumVersionId().equals(target.getCurriculumVersionId())
                || target.getType() != LessonType.FLASHCARD) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "Flashcard target must belong to the same curriculum");
        }
        FlashcardSet set = flashcardSetRepository.findByCurriculumLessonIdAndDeletedAtIsNull(target.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CONFLICT,
                        "Create a flashcard set for the target lesson first"));
        Optional<VideoAiJob> active = activeJob(context, hls.getProcessingJobId(), "FLASHCARD_CANDIDATES");
        if (active.isPresent()) return toJob(active.get());
        VideoAiJob job = new VideoAiJob();
        job.setLessonId(context.lesson().getId());
        job.setLessonScope(context.scope());
        job.setCourseId(context.courseId());
        job.setClassId(context.classId());
        job.setSourceVersion(hls.getProcessingJobId());
        job.setJobType("FLASHCARD_CANDIDATES");
        job.setContentId(content.getId());
        job.setTargetLessonId(set.getCurriculumLessonId());
        job.setDesiredCount(request.desiredCount() == null ? 10 : request.desiredCount());
        job.setDifficulty(normalize(request.difficulty()) == null ? "medium" : request.difficulty().toLowerCase(Locale.ROOT));
        job.setRequestedBy(currentUserService.requireAuthenticatedUser().getId());
        return toJob(jobRepository.saveAndFlush(job));
    }

    private ContentResponse save(LessonContext context, UUID contentId, SaveContentRequest request) {
        VideoAiContent content = requireContent(context, contentId);
        if (!"draft".equals(content.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "Only draft AI content can be edited");
        }
        if (request == null || !content.getRevision().equals(request.revision())) {
            throw new BusinessException(ErrorCode.CONFLICT, "AI content was updated by another request");
        }
        if (request.summary() != null) content.setSummary(normalize(request.summary()));
        if (request.keyPoints() != null) content.setKeyPointsJson(writeKeyPoints(request.keyPoints()));
        if (request.segments() != null) {
            List<VideoAiTranscriptSegment> segments = toSegments(request.segments());
            content.replaceSegments(segments);
            content.setTranscriptText(segments.stream().map(VideoAiTranscriptSegment::getText)
                    .reduce((left, right) -> left + " " + right).orElse(""));
        }
        if (request.chapters() != null) content.replaceChapters(toChapters(request.chapters()));
        validateContent(content);
        return toContent(contentRepository.saveAndFlush(content));
    }

    private ContentResponse publish(LessonContext context, UUID contentId, PublishContentRequest request) {
        VideoAiContent content = requireContent(context, contentId);
        if (!"draft".equals(content.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "Only draft AI content can be published");
        }
        if (request == null || !content.getRevision().equals(request.revision())) {
            throw new BusinessException(ErrorCode.CONFLICT, "AI content was updated by another request");
        }
        HlsLesson hls = requireEligibleHls(context.lesson().getId());
        if (!content.getSourceVersion().equals(hls.getProcessingJobId())) {
            content.setStatus("superseded");
            contentRepository.save(content);
            throw new BusinessException(ErrorCode.CONFLICT, "The source video changed; generate new AI content");
        }
        validateContent(content);
        List<VideoAiContent> previous = contentRepository.findPublishedForLesson(
                context.lesson().getId(), context.scope(), context.classId());
        previous.stream().filter(value -> !value.getId().equals(contentId))
                .forEach(value -> value.setStatus("superseded"));
        contentRepository.saveAll(previous);
        contentRepository.flush();
        content.setStatus("published");
        content.setPublishedAt(Instant.now());
        content.setPublishedBy(currentUserService.requireAuthenticatedUser().getId());
        return toContent(contentRepository.saveAndFlush(content));
    }

    private JobResponse retry(LessonContext context, UUID jobId) {
        VideoAiJob job = jobRepository.findByIdAndLessonId(jobId, context.lesson().getId())
                .filter(value -> sameContext(value, context))
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Video AI job was not found"));
        if (!"failed".equals(job.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "Only failed Video AI jobs can be retried");
        }
        HlsLesson hls = requireEligibleHls(context.lesson().getId());
        if (!job.getSourceVersion().equals(hls.getProcessingJobId())) {
            job.setStatus("superseded");
            jobRepository.save(job);
            throw new BusinessException(ErrorCode.CONFLICT, "The source video changed; create a new job");
        }
        job.setStatus("pending");
        job.setStage("queued");
        job.setProgressPercent(0);
        job.setErrorMessage(null);
        job.setAttemptCount(0);
        job.setNextAttemptAt(Instant.now());
        job.setLeaseOwner(null);
        job.setLeaseExpiresAt(null);
        job.setLeaseHeartbeatAt(null);
        return toJob(jobRepository.save(job));
    }

    private JobResponse getJob(LessonContext context, UUID jobId) {
        VideoAiJob job = jobRepository.findByIdAndLessonId(jobId, context.lesson().getId())
                .filter(value -> sameContext(value, context))
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Video AI job was not found"));
        return toJob(job);
    }

    private List<FlashcardTargetResponse> flashcardTargets(LessonContext context) {
        return curriculumLessonRepository
                .findByCurriculumVersionIdOrderBySortOrderAscCreatedAtAsc(context.lesson().getCurriculumVersionId())
                .stream().filter(lesson -> lesson.getType() == LessonType.FLASHCARD)
                .map(lesson -> flashcardSetRepository.findByCurriculumLessonIdAndDeletedAtIsNull(lesson.getId())
                        .map(set -> new FlashcardTargetResponse(lesson.getId(), set.getId(), lesson.getTitle())))
                .flatMap(Optional::stream).toList();
    }

    private LessonContext resolveMaster(UUID courseId, UUID lessonId) {
        courseAccessService.requireUpdatableCourse(courseId);
        courseRepository.findByIdAndDeletedAtIsNull(courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Course was not found"));
        CurriculumLesson lesson = curriculumLessonRepository.findById(lessonId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Lesson was not found"));
        var version = lesson.getSection().getCurriculumVersion();
        if (!courseId.equals(version.getCourseId()) || version.getScope() != CurriculumScope.MASTER) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Lesson was not found");
        }
        requireVideo(lesson);
        return new LessonContext(courseId, null, MASTER, lesson);
    }

    private LessonContext resolveTrainer(UUID classId, UUID lessonId, boolean write) {
        CurriculumLesson lesson = write
                ? trainerClassCurriculumService.requireOwnedClassLessonForWrite(classId, lessonId)
                : trainerClassCurriculumService.requireOwnedClassLessonForRead(classId, lessonId);
        requireVideo(lesson);
        UUID courseId = lesson.getSection().getCurriculumVersion().getCourseId();
        return new LessonContext(courseId, classId, CLASS, lesson);
    }

    private void requireVideo(CurriculumLesson lesson) {
        if (lesson.getType() != LessonType.VIDEO) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Video AI is available only for video lessons");
        }
    }

    private HlsLesson requireEligibleHls(UUID lessonId) {
        HlsLesson hls = hlsLessonRepository.findByLessonId(lessonId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONFLICT, "Upload and process the video first"));
        if (!hls.isReady() || hls.getProcessingJobId() == null) {
            throw new BusinessException(ErrorCode.CONFLICT, "HLS video processing is not complete");
        }
        if (hls.getAiAudioObjectKey() == null || hls.getAiAudioObjectKey().isBlank()) {
            throw new BusinessException(ErrorCode.CONFLICT, "This video does not have an AI audio derivative; upload it again");
        }
        return hls;
    }

    private VideoAiContent requireCurrent(LessonContext context) {
        HlsLesson hls = hlsLessonRepository.findByLessonId(context.lesson().getId()).orElse(null);
        return (hls == null || !hls.isReady() || hls.getProcessingJobId() == null
                ? Optional.<VideoAiContent>empty()
                : current(context, hls.getProcessingJobId())).orElseThrow(() ->
                new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Video AI content was not found"));
    }

    private Optional<VideoAiContent> current(LessonContext context, UUID sourceVersion) {
        return context.classId() == null
                ? contentRepository.findFirstByLessonIdAndLessonScopeAndClassIdIsNullAndSourceVersionOrderByUpdatedAtDesc(
                        context.lesson().getId(), context.scope(), sourceVersion)
                : contentRepository.findFirstByLessonIdAndLessonScopeAndClassIdAndSourceVersionOrderByUpdatedAtDesc(
                        context.lesson().getId(), context.scope(), context.classId(), sourceVersion);
    }

    private VideoAiContent requireContent(LessonContext context, UUID contentId) {
        return contentRepository.findById(contentId).filter(value -> sameContext(value, context))
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Video AI content was not found"));
    }

    private Optional<VideoAiJob> latestJob(LessonContext context, UUID sourceVersion) {
        return jobRepository.findLatestForSource(context.lesson().getId(), context.scope(), context.classId(), sourceVersion,
                VIDEO_ARTIFACTS,
                PageRequest.of(0, 1)).stream().findFirst();
    }

    private String availabilityReason() {
        if (!properties.isEnabled()) return "VIDEO_AI_DISABLED";
        if (!transcriptionProperties.isEnabled()
                || !"faster-whisper".equalsIgnoreCase(transcriptionProperties.getProvider())) {
            return "TRANSCRIPTION_NOT_CONFIGURED";
        }
        if (!generationProperties.isEnabled()
                || generationProperties.getApiKey() == null
                || generationProperties.getApiKey().isBlank()) {
            return "GENERATION_NOT_CONFIGURED";
        }
        return null;
    }

    private Optional<VideoAiJob> activeJob(LessonContext context, UUID sourceVersion) {
        return activeJob(context, sourceVersion, VIDEO_ARTIFACTS);
    }

    private Optional<VideoAiJob> activeJob(LessonContext context, UUID sourceVersion, String jobType) {
        return jobRepository.findActive(context.lesson().getId(), context.scope(), context.classId(), sourceVersion,
                jobType, PageRequest.of(0, 1)).stream().findFirst();
    }

    private boolean sameContext(VideoAiContent value, LessonContext context) {
        return value.getLessonId().equals(context.lesson().getId())
                && value.getLessonScope().equals(context.scope())
                && java.util.Objects.equals(value.getClassId(), context.classId());
    }

    private boolean sameContext(VideoAiJob value, LessonContext context) {
        return value.getLessonScope().equals(context.scope())
                && java.util.Objects.equals(value.getClassId(), context.classId());
    }

    private List<VideoAiTranscriptSegment> toSegments(List<TranscriptSegmentRequest> requests) {
        List<VideoAiTranscriptSegment> values = new ArrayList<>();
        long previousEnd = -1;
        for (int index = 0; index < requests.size(); index++) {
            TranscriptSegmentRequest request = requests.get(index);
            if (request.endMs() <= request.startMs() || request.startMs() < previousEnd) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST,
                        "Transcript segments must be ordered, non-overlapping, and have a positive duration");
            }
            VideoAiTranscriptSegment segment = new VideoAiTranscriptSegment();
            segment.setSegmentIndex(index);
            segment.setStartMs(request.startMs());
            segment.setEndMs(request.endMs());
            segment.setText(request.text().trim());
            values.add(segment);
            previousEnd = request.endMs();
        }
        return values;
    }

    private List<VideoAiChapter> toChapters(List<ChapterRequest> requests) {
        List<VideoAiChapter> values = new ArrayList<>();
        long previousEnd = -1;
        for (int index = 0; index < requests.size(); index++) {
            ChapterRequest request = requests.get(index);
            if (request.endMs() <= request.startMs() || request.startMs() < previousEnd) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST,
                        "Chapters must be ordered, non-overlapping, and have a positive duration");
            }
            VideoAiChapter chapter = new VideoAiChapter();
            chapter.setChapterIndex(index);
            chapter.setStartMs(request.startMs());
            chapter.setEndMs(request.endMs());
            chapter.setTitle(request.title().trim());
            chapter.setSummary(normalize(request.summary()));
            values.add(chapter);
            previousEnd = request.endMs();
        }
        return values;
    }

    private void validateContent(VideoAiContent content) {
        if (normalize(content.getSummary()) == null || content.getSegments().isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "A summary and at least one transcript segment are required");
        }
        long duration = content.getSegments().get(content.getSegments().size() - 1).getEndMs();
        boolean invalidChapter = content.getChapters().stream()
                .anyMatch(chapter -> chapter.getEndMs() > duration || chapter.getStartMs() >= chapter.getEndMs());
        if (invalidChapter) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Chapter timestamps exceed the transcript duration");
        }
    }

    private String writeKeyPoints(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values.stream().map(String::trim).filter(v -> !v.isEmpty()).toList());
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Key points are invalid");
        }
    }

    private List<String> readKeyPoints(String value) {
        try {
            return objectMapper.readValue(value == null ? "[]" : value, new TypeReference<>() { });
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }

    public ContentResponse toContent(VideoAiContent content) {
        return new ContentResponse(
                content.getId(), content.getLessonId(), content.getSourceVersion(), content.getLanguage(),
                content.getTranscriptText(), content.getSummary(), readKeyPoints(content.getKeyPointsJson()),
                content.getStatus(), content.getRevision(),
                content.getSegments().stream().map(segment -> new TranscriptSegmentResponse(
                        segment.getSegmentIndex(), segment.getStartMs(), segment.getEndMs(), segment.getText())).toList(),
                content.getChapters().stream().map(chapter -> new ChapterResponse(
                        chapter.getChapterIndex(), chapter.getStartMs(), chapter.getEndMs(),
                        chapter.getTitle(), chapter.getSummary())).toList(),
                content.getUpdatedAt(), content.getPublishedAt());
    }

    private JobResponse toJob(VideoAiJob job) {
        return new JobResponse(job.getId(), job.getJobType(), job.getStatus(), job.getStage(),
                job.getProgressPercent(), job.getAttemptCount(), job.getErrorMessage(), job.getContentId(),
                job.getTargetLessonId(), job.getResultBatchId(), job.getResultSetId(),
                job.getCreatedAt(), job.getCompletedAt());
    }

    private String normalizeLanguage(String value) {
        String normalized = normalize(value);
        if (normalized == null) return "auto";
        normalized = normalized.toLowerCase(Locale.ROOT);
        return List.of("auto", "vi", "en").contains(normalized) ? normalized : "auto";
    }

    private String normalize(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private record LessonContext(UUID courseId, UUID classId, String scope, CurriculumLesson lesson) {
    }
}
