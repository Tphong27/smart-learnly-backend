package com.smartlearnly.backend.curriculum.service;

import com.smartlearnly.backend.course.dto.LessonResourceResponse;
import com.smartlearnly.backend.course.dto.LessonResponse;
import com.smartlearnly.backend.course.dto.SectionResponse;
import com.smartlearnly.backend.curriculum.dto.ClassCurriculumBindingResponse;
import com.smartlearnly.backend.curriculum.dto.CurriculumLessonResponse;
import com.smartlearnly.backend.curriculum.dto.CurriculumMetadataResponse;
import com.smartlearnly.backend.curriculum.dto.CurriculumResourceResponse;
import com.smartlearnly.backend.curriculum.dto.CurriculumSectionResponse;
import com.smartlearnly.backend.curriculum.dto.CurriculumVersionResponse;
import com.smartlearnly.backend.curriculum.entity.ClassCurriculumBinding;
import com.smartlearnly.backend.curriculum.entity.CurriculumCustomizationState;
import com.smartlearnly.backend.curriculum.entity.CurriculumLesson;
import com.smartlearnly.backend.curriculum.entity.CurriculumLessonResource;
import com.smartlearnly.backend.curriculum.entity.CurriculumScope;
import com.smartlearnly.backend.curriculum.entity.CurriculumSection;
import com.smartlearnly.backend.curriculum.entity.CurriculumStatus;
import com.smartlearnly.backend.curriculum.entity.CurriculumVersion;
import com.smartlearnly.backend.learning.dto.LearningContentResponse;
import com.smartlearnly.backend.learning.dto.LearningLessonResponse;
import com.smartlearnly.backend.learning.dto.LearningResourceResponse;
import com.smartlearnly.backend.learning.dto.LearningSectionResponse;
import com.smartlearnly.backend.learning.dto.LearningStats;
import com.smartlearnly.backend.learning.lesson.entity.LessonStatus;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class CurriculumDtoMapper {
    public CurriculumVersionResponse toCurriculumVersionResponse(CurriculumVersion version) {
        return new CurriculumVersionResponse(
                version.getId(),
                version.getCourseId(),
                version.getClassId(),
                enumLower(version.getScope()),
                enumLower(version.getStatus()),
                version.getVersionNumber(),
                version.getTitle(),
                version.getSourceVersionId(),
                version.getCreatedBy(),
                version.getPublishedAt(),
                version.getArchivedAt(),
                orderedSections(version).stream().map(this::toCurriculumSectionResponse).toList(),
                version.getCreatedAt(),
                version.getUpdatedAt()
        );
    }

    public CurriculumSectionResponse toCurriculumSectionResponse(CurriculumSection section) {
        return new CurriculumSectionResponse(
                section.getId(),
                section.getSourceSectionId(),
                section.getSourceCurriculumSectionId(),
                section.getTitle(),
                section.getSortOrder(),
                orderedLessons(section).stream().map(this::toCurriculumLessonResponse).toList(),
                section.getCreatedAt(),
                section.getUpdatedAt()
        );
    }

    public CurriculumLessonResponse toCurriculumLessonResponse(CurriculumLesson lesson) {
        return new CurriculumLessonResponse(
                lesson.getId(),
                lesson.getLessonIdentityId(),
                lesson.getSourceLessonId(),
                lesson.getSourceCurriculumLessonId(),
                lesson.getTitle(),
                enumUpper(lesson.getType()),
                lesson.getVideoUrl(),
                lesson.getContent(),
                lesson.getAttachmentUrl(),
                lesson.getDurationSeconds(),
                Boolean.TRUE.equals(lesson.getPreview()),
                enumLower(lesson.getStatus()),
                lesson.getTestId(),
                orderedResources(lesson).stream().map(this::toCurriculumResourceResponse).toList(),
                lesson.getSortOrder(),
                lesson.getCreatedAt(),
                lesson.getUpdatedAt()
        );
    }

    public CurriculumResourceResponse toCurriculumResourceResponse(CurriculumLessonResource resource) {
        return new CurriculumResourceResponse(
                resource.getId(),
                resource.getSourceResourceId(),
                resource.getSourceCurriculumResourceId(),
                resource.getUrl(),
                resource.getObjectPath(),
                resource.getName(),
                resource.getFileSize(),
                resource.getContentType(),
                resource.getSortOrder()
        );
    }

    public ClassCurriculumBindingResponse toBindingResponse(ClassCurriculumBinding binding) {
        return new ClassCurriculumBindingResponse(
                binding.getId(),
                binding.getClassId(),
                binding.getCourseId(),
                binding.getBaseMasterVersionId(),
                binding.getDraftVersionId(),
                binding.getPublishedVersionId(),
                enumLower(binding.getCustomizationState()),
                binding.getCreatedAt(),
                binding.getUpdatedAt()
        );
    }

    public SectionResponse toSectionResponse(CurriculumSection section) {
        CurriculumVersion version = section.getCurriculumVersion();
        return new SectionResponse(
                section.getId(),
                version == null ? null : version.getCourseId(),
                section.getTitle(),
                section.getSortOrder(),
                section.getCreatedAt(),
                section.getUpdatedAt()
        );
    }

    public LessonResponse toLessonResponse(CurriculumLesson lesson) {
        CurriculumVersion version = lesson.getSection().getCurriculumVersion();
        return new LessonResponse(
                lesson.getId(),
                version.getCourseId(),
                lesson.getSection().getId(),
                lesson.getTitle(),
                enumUpper(lesson.getType()),
                lesson.getVideoUrl(),
                lesson.getContent(),
                lesson.getAttachmentUrl(),
                lesson.getDurationSeconds(),
                Boolean.TRUE.equals(lesson.getPreview()),
                enumLower(lesson.getStatus()),
                orderedResources(lesson).stream().map(this::toLessonResourceResponse).toList(),
                lesson.getSortOrder(),
                lesson.getCreatedAt(),
                lesson.getUpdatedAt()
        );
    }

    public LessonResourceResponse toLessonResourceResponse(CurriculumLessonResource resource) {
        return new LessonResourceResponse(
                resource.getId(),
                resource.getUrl(),
                resource.getObjectPath(),
                resource.getName(),
                resource.getFileSize(),
                resource.getContentType(),
                resource.getSortOrder()
        );
    }

    public LearningContentResponse toLearningContentResponse(
            CurriculumVersion version,
            String courseTitle,
            String courseThumbnail,
            Set<UUID> completedLessonIds) {
        return toLearningContentResponse(version, courseTitle, courseThumbnail, completedLessonIds, null, false);
    }

    public LearningContentResponse toLearningContentResponse(
            CurriculumVersion version,
            String courseTitle,
            String courseThumbnail,
            Set<UUID> completedLessonIds,
            CurriculumMetadataResponse metadata) {
        return toLearningContentResponse(version, courseTitle, courseThumbnail, completedLessonIds, metadata, false);
    }

    public LearningContentResponse toPreviewLearningContentResponse(
            CurriculumVersion version,
            String courseTitle,
            String courseThumbnail,
            CurriculumMetadataResponse metadata) {
        return toLearningContentResponse(version, courseTitle, courseThumbnail, Set.of(), metadata, true);
    }

    private LearningContentResponse toLearningContentResponse(
            CurriculumVersion version,
            String courseTitle,
            String courseThumbnail,
            Set<UUID> completedLessonIds,
            CurriculumMetadataResponse metadata,
            boolean previewOnly) {
        List<LearningSectionResponse> sections = orderedSections(version).stream()
                .map(section -> toLearningSectionResponse(section, completedLessonIds, previewOnly))
                .filter(section -> !section.lessons().isEmpty())
                .toList();

        return new LearningContentResponse(
                version.getCourseId(),
                courseTitle,
                courseThumbnail,
                sections,
                calculateStats(sections),
                metadata
        );
    }

    public LearningSectionResponse toLearningSectionResponse(
            CurriculumSection section,
            Set<UUID> completedLessonIds) {
        return toLearningSectionResponse(section, completedLessonIds, false);
    }

    private LearningSectionResponse toLearningSectionResponse(
            CurriculumSection section,
            Set<UUID> completedLessonIds,
            boolean previewOnly) {
        List<LearningLessonResponse> lessons = orderedLessons(section).stream()
                .filter(lesson -> lesson.getStatus() == LessonStatus.PUBLISHED)
                .filter(lesson -> !previewOnly || Boolean.TRUE.equals(lesson.getPreview()))
                .map(lesson -> toLearningLessonResponse(lesson, completedLessonIds.contains(lesson.getLessonIdentityId())))
                .toList();

        return new LearningSectionResponse(
                section.getId(),
                section.getTitle(),
                section.getSortOrder(),
                lessons
        );
    }

    public LearningLessonResponse toLearningLessonResponse(CurriculumLesson lesson, boolean completed) {
        List<LearningResourceResponse> resources = orderedResources(lesson).stream()
                .map(resource -> new LearningResourceResponse(
                        resource.getUrl(),
                        resource.getName(),
                        resource.getContentType()))
                .toList();

        return new LearningLessonResponse(
                lesson.getId(),
                lesson.getTitle(),
                enumUpper(lesson.getType()),
                enumLower(lesson.getStatus()),
                lesson.getVideoUrl(),
                lesson.getContent(),
                lesson.getAttachmentUrl(),
                lesson.getDurationSeconds(),
                Boolean.TRUE.equals(lesson.getPreview()),
                lesson.getSortOrder(),
                completed,
                resources,
                false,
                null,
                lesson.getLessonIdentityId()
        );
    }

    public CurriculumMetadataResponse toMetadata(CurriculumVersion version, UUID classId, String source) {
        boolean customized = version.getScope() == CurriculumScope.CLASS;
        return new CurriculumMetadataResponse(
                version.getId(),
                enumLower(version.getScope()),
                version.getCourseId(),
                classId,
                customized,
                source
        );
    }

    public String learningSource(CurriculumVersion version) {
        return version.getScope() == CurriculumScope.CLASS ? "class_published" : "master_inherited";
    }

    public String editorSource(CurriculumVersion version) {
        if (version.getStatus() == CurriculumStatus.DRAFT) {
            return "class_draft";
        }
        return learningSource(version);
    }

    public String bindingState(ClassCurriculumBinding binding) {
        CurriculumCustomizationState state = binding.getCustomizationState();
        return state == null ? null : state.name().toLowerCase(Locale.ROOT);
    }

    private List<CurriculumSection> orderedSections(CurriculumVersion version) {
        return version.getSections().stream()
                .sorted(Comparator
                        .comparing(CurriculumSection::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(CurriculumSection::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(CurriculumSection::getId, Comparator.nullsLast(UUID::compareTo)))
                .toList();
    }

    private List<CurriculumLesson> orderedLessons(CurriculumSection section) {
        return section.getLessons().stream()
                .sorted(Comparator
                        .comparing(CurriculumLesson::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(CurriculumLesson::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(CurriculumLesson::getId, Comparator.nullsLast(UUID::compareTo)))
                .toList();
    }

    private List<CurriculumLessonResource> orderedResources(CurriculumLesson lesson) {
        return lesson.getResources().stream()
                .sorted(Comparator
                        .comparing(CurriculumLessonResource::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(CurriculumLessonResource::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(CurriculumLessonResource::getId, Comparator.nullsLast(UUID::compareTo)))
                .toList();
    }

    private LearningStats calculateStats(List<LearningSectionResponse> sections) {
        int totalVideos = 0;
        int totalDocuments = 0;
        int totalQuizzes = 0;
        int totalDurationSeconds = 0;
        int totalLessons = 0;

        for (LearningSectionResponse section : sections) {
            for (LearningLessonResponse lesson : section.lessons()) {
                totalLessons++;
                if ("VIDEO".equals(lesson.lessonType())) {
                    totalVideos++;
                } else if ("PDF".equals(lesson.lessonType())) {
                    totalDocuments++;
                } else if ("QUIZ".equals(lesson.lessonType())) {
                    totalQuizzes++;
                }
                if (lesson.durationSeconds() != null) {
                    totalDurationSeconds += lesson.durationSeconds();
                }
            }
        }

        return new LearningStats(
                sections.size(),
                totalLessons,
                totalVideos,
                totalDocuments,
                totalQuizzes,
                totalDurationSeconds);
    }

    private String enumLower(Enum<?> value) {
        return value == null ? null : value.name().toLowerCase(Locale.ROOT);
    }

    private String enumUpper(Enum<?> value) {
        return value == null ? null : value.name();
    }
}
