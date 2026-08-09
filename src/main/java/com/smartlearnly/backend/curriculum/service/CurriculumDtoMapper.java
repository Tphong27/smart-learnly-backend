package com.smartlearnly.backend.curriculum.service;

import com.smartlearnly.backend.curriculum.dto.LessonResourceResponse;
import com.smartlearnly.backend.curriculum.dto.LessonResponse;
import com.smartlearnly.backend.curriculum.dto.ModuleResponse;
import com.smartlearnly.backend.curriculum.dto.SectionResponse;
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
import com.smartlearnly.backend.learning.content.dto.LearningContentResponse;
import com.smartlearnly.backend.learning.content.dto.LearningLessonResponse;
import com.smartlearnly.backend.learning.content.dto.LearningResourceResponse;
import com.smartlearnly.backend.learning.content.dto.LearningSectionResponse;
import com.smartlearnly.backend.learning.content.dto.LearningStats;
import com.smartlearnly.backend.learning.lesson.entity.LessonStatus;
import com.smartlearnly.backend.learning.lesson.entity.LessonType;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CurriculumDtoMapper {
    private final ClassCurriculumCompositionService compositionService;
    /** Chuyển một phiên bản giáo trình sang dữ liệu trả về cho quản trị. */
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

    /** Chuyển một phần giáo trình cùng các bài học đã sắp xếp sang DTO quản trị. */
    public CurriculumSectionResponse toCurriculumSectionResponse(CurriculumSection section) {
        return new CurriculumSectionResponse(
                section.getId(),
                section.getSourceModuleId(),
                section.getSourceCurriculumSectionId(),
                section.getTitle(),
                section.getSortOrder(),
                orderedLessons(section).stream().map(this::toCurriculumLessonResponse).toList(),
                section.getCreatedAt(),
                section.getUpdatedAt()
        );
    }

    /** Chuyển bài học trong giáo trình sang DTO quản trị, bao gồm tài nguyên đính kèm. */
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

    /** Chuyển tài nguyên của bài học sang DTO quản trị. */
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

    /** Chuyển liên kết giữa lớp học và giáo trình sang DTO để quản trị trạng thái tùy biến. */
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

    /** Chuyển section của giáo trình sang định dạng section cũ mà API khóa học đang công khai. */
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

    /** Chuyển section của giáo trình sang định dạng module cũ để giữ nguyên JSON contract. */
    public ModuleResponse toModuleResponse(CurriculumSection section) {
        CurriculumVersion version = section.getCurriculumVersion();
        return new ModuleResponse(
                section.getSourceModuleId(),
                section.getSourceModuleId(),
                version == null ? null : version.getCourseId(),
                section.getTitle(),
                section.getSortOrder(),
                section.getCreatedAt(),
                section.getUpdatedAt()
        );
    }

    /** Chuyển bài học giáo trình sang DTO bài học của API khóa học. */
    public LessonResponse toLessonResponse(CurriculumLesson lesson) {
        CurriculumVersion version = lesson.getSection().getCurriculumVersion();
        return new LessonResponse(
                lesson.getId(),
                version.getCourseId(),
                lesson.getSection().getId(),
                lesson.getSection().getSourceModuleId(),
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

    /** Chuyển tài nguyên giáo trình sang DTO tài nguyên của API khóa học. */
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

    /** Tạo nội dung học đầy đủ cho học viên từ giáo trình và các bài đã hoàn thành. */
    public LearningContentResponse toLearningContentResponse(
            CurriculumVersion version,
            String courseTitle,
            String courseThumbnail,
            Set<UUID> completedLessonIds) {
        return toLearningContentResponse(
                version,
                courseTitle,
                courseThumbnail,
                completedLessonIds,
                null,
                false);
    }

    /** Tạo nội dung học đầy đủ kèm metadata về nguồn giáo trình. */
    public LearningContentResponse toLearningContentResponse(
            CurriculumVersion version,
            String courseTitle,
            String courseThumbnail,
            Set<UUID> completedLessonIds,
            CurriculumMetadataResponse metadata) {
        return toLearningContentResponse(
                version,
                courseTitle,
                courseThumbnail,
                completedLessonIds,
                metadata,
                false);
    }

    /** Tạo nội dung xem trước chỉ gồm các bài được cho phép preview. */
    public LearningContentResponse toPreviewLearningContentResponse(
            CurriculumVersion version,
            String courseTitle,
            String courseThumbnail,
            CurriculumMetadataResponse metadata) {
        return toLearningContentResponse(
                version,
                courseTitle,
                courseThumbnail,
                Set.of(),
                metadata,
                true);
    }

    /** Gom dữ liệu giáo trình thành nội dung học, đồng thời lọc theo chế độ xem trước khi cần. */
    private LearningContentResponse toLearningContentResponse(
            CurriculumVersion version,
            String courseTitle,
            String courseThumbnail,
            Set<UUID> completedLessonIds,
            CurriculumMetadataResponse metadata,
            boolean previewOnly) {
        List<LearningSectionResponse> sections = orderedSections(version).stream()
                .map(section -> toLearningSectionResponse(
                        section,
                        completedLessonIds,
                        previewOnly))
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

    /** Chuyển section sang dữ liệu học viên khi không ở chế độ xem trước. */
    public LearningSectionResponse toLearningSectionResponse(
            CurriculumSection section,
            Set<UUID> completedLessonIds) {
        return toLearningSectionResponse(section, completedLessonIds, false);
    }

    /** Chuyển section sang dữ liệu học viên, chỉ giữ bài đã xuất bản và đúng phạm vi truy cập. */
    private LearningSectionResponse toLearningSectionResponse(
            CurriculumSection section,
            Set<UUID> completedLessonIds,
            boolean previewOnly) {
        List<LearningLessonResponse> lessons = orderedLessons(section).stream()
                .filter(lesson -> lesson.getStatus() == LessonStatus.PUBLISHED)
                .filter(lesson -> !previewOnly || Boolean.TRUE.equals(lesson.getPreview()))
                .map(lesson -> toLearningLessonResponse(
                        lesson,
                        completedLessonIds.contains(lesson.getLessonIdentityId())))
                .toList();

        return new LearningSectionResponse(
                section.getId(),
                section.getTitle(),
                section.getSortOrder(),
                lessons
        );
    }

    /** Chuyển bài học sang dữ liệu học viên và gắn trạng thái hoàn thành hiện tại. */
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
                lesson.getLessonIdentityId(),
                lesson.getTestId()
        );
    }

    /** Tạo metadata mô tả nguồn và phạm vi của giáo trình đang được sử dụng. */
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

    /** Xác định nguồn giáo trình mà học viên đang học. */
    public String learningSource(CurriculumVersion version) {
        return version.getScope() == CurriculumScope.CLASS ? "class_published" : "master_inherited";
    }

    /** Xác định nguồn giáo trình mà màn hình biên tập cần hiển thị. */
    public String editorSource(CurriculumVersion version) {
        if (version.getStatus() == CurriculumStatus.DRAFT) {
            return "class_draft";
        }
        return learningSource(version);
    }

    /** Chuyển trạng thái tùy biến curriculum binding về chuỗi API ổn định. */
    public String bindingState(ClassCurriculumBinding binding) {
        CurriculumCustomizationState state = binding.getCustomizationState();
        return state == null ? null : state.name().toLowerCase(Locale.ROOT);
    }

    /** Sắp xếp section theo thứ tự nghiệp vụ ổn định trước khi trả về client. */
    private List<CurriculumSection> orderedSections(CurriculumVersion version) {
        return version.getSections().stream()
                .sorted(Comparator
                        .comparing(CurriculumSection::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(CurriculumSection::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(CurriculumSection::getId, Comparator.nullsLast(UUID::compareTo)))
                .toList();
    }

    /** Sắp xếp bài học trong section theo thứ tự nghiệp vụ ổn định. */
    private List<CurriculumLesson> orderedLessons(CurriculumSection section) {
        CurriculumVersion version = section.getCurriculumVersion();
        List<CurriculumLesson> lessons = compositionService.isCompositionVersion(version)
                ? compositionService.effectiveLessons(section)
                : section.getLessons();
        return lessons.stream()
                .sorted(Comparator
                        .comparing(CurriculumLesson::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(CurriculumLesson::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(CurriculumLesson::getId, Comparator.nullsLast(UUID::compareTo)))
                .toList();
    }

    /** Sắp xếp tài nguyên của bài học theo thứ tự nghiệp vụ ổn định. */
    private List<CurriculumLessonResource> orderedResources(CurriculumLesson lesson) {
        return lesson.getResources().stream()
                .sorted(Comparator
                        .comparing(CurriculumLessonResource::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(CurriculumLessonResource::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(CurriculumLessonResource::getId, Comparator.nullsLast(UUID::compareTo)))
                .toList();
    }

    /** Tính thống kê bài học để hiển thị tiến độ và tổng quan khóa học. */
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

    /** Chuẩn hóa enum thành chữ thường cho JSON contract. */
    private String enumLower(Enum<?> value) {
        return value == null ? null : value.name().toLowerCase(Locale.ROOT);
    }

    /** Chuẩn hóa enum thành chữ hoa cho JSON contract. */
    private String enumUpper(Enum<?> value) {
        return value == null ? null : value.name();
    }
}
