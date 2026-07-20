package com.smartlearnly.backend.videoai.service;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.curriculum.entity.CurriculumLesson;
import com.smartlearnly.backend.curriculum.entity.CurriculumScope;
import com.smartlearnly.backend.curriculum.repository.CurriculumLessonRepository;
import com.smartlearnly.backend.curriculum.service.CurriculumResolution;
import com.smartlearnly.backend.curriculum.service.CurriculumResolutionService;
import com.smartlearnly.backend.hls.entity.HlsLesson;
import com.smartlearnly.backend.hls.repository.HlsLessonRepository;
import com.smartlearnly.backend.learning.lesson.entity.LessonStatus;
import com.smartlearnly.backend.learning.lesson.entity.LessonType;
import com.smartlearnly.backend.videoai.dto.VideoAiDtos.ContentResponse;
import com.smartlearnly.backend.videoai.entity.VideoAiContent;
import com.smartlearnly.backend.videoai.repository.VideoAiContentRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VideoAiLearningService {
    private final CurriculumResolutionService curriculumResolutionService;
    private final CurriculumLessonRepository curriculumLessonRepository;
    private final HlsLessonRepository hlsLessonRepository;
    private final VideoAiContentRepository contentRepository;
    private final CurrentUserService currentUserService;
    private final VideoAiAuthoringService mapper;

    @Transactional(readOnly = true)
    public Optional<ContentResponse> getPublished(UUID courseId, UUID lessonReferenceId, UUID classId) {
        UUID studentId = currentUserService.requireAuthenticatedUser().getId();
        CurriculumResolution resolution = classId == null
                ? curriculumResolutionService.resolveOnlineLearning(courseId, studentId)
                : curriculumResolutionService.resolveClassLearning(courseId, classId, studentId);
        CurriculumLesson lesson = curriculumLessonRepository
                .findEffectiveLessonReference(resolution.version().getId(), lessonReferenceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Lesson was not found"));
        if (lesson.getType() != LessonType.VIDEO || lesson.getStatus() != LessonStatus.PUBLISHED) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Published video lesson was not found");
        }
        HlsLesson hls = hlsLessonRepository.findByLessonId(lesson.getId()).orElse(null);
        if (hls == null || !hls.isReady() || hls.getProcessingJobId() == null) return Optional.empty();

        boolean classScoped = resolution.version().getScope() == CurriculumScope.CLASS;
        Optional<VideoAiContent> content = classScoped
                ? contentRepository.findFirstByLessonIdAndLessonScopeAndClassIdAndStatusAndSourceVersionOrderByUpdatedAtDesc(
                        lesson.getId(), "CLASS", classId, "published", hls.getProcessingJobId())
                : contentRepository.findFirstByLessonIdAndLessonScopeAndClassIdIsNullAndStatusAndSourceVersionOrderByUpdatedAtDesc(
                        lesson.getId(), "MASTER", "published", hls.getProcessingJobId());
        return content.map(mapper::toContent);
    }
}
