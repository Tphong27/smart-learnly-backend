package com.smartlearnly.backend.curriculum.admin.service;

import com.smartlearnly.backend.common.audit.AuditLogService;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.curriculum.dto.LessonRequest;
import com.smartlearnly.backend.curriculum.dto.LessonResourceRequest;
import com.smartlearnly.backend.curriculum.dto.LessonResponse;
import com.smartlearnly.backend.curriculum.dto.ReorderRequest;
import com.smartlearnly.backend.curriculum.entity.CurriculumLesson;
import com.smartlearnly.backend.curriculum.entity.CurriculumLessonResource;
import com.smartlearnly.backend.curriculum.entity.CurriculumSection;
import com.smartlearnly.backend.curriculum.repository.CurriculumLessonRepository;
import com.smartlearnly.backend.curriculum.service.CurriculumDtoMapper;
import com.smartlearnly.backend.curriculum.service.CurriculumLessonTestLinkService;
import com.smartlearnly.backend.flashcard.repository.FlashcardSetRepository;
import com.smartlearnly.backend.learning.lesson.entity.LessonStatus;
import com.smartlearnly.backend.learning.lesson.entity.LessonType;
import com.smartlearnly.backend.learning.lesson.service.QuizContentValidator;
import com.smartlearnly.backend.user.entity.UserAccount;
import com.smartlearnly.backend.videoai.service.VideoSummaryService;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CurriculumLessonAdminService {
    private static final int MAX_RESOURCES_PER_LESSON = 10;

    private final CurriculumLessonRepository lessonRepository;
    private final CurriculumDtoMapper curriculumDtoMapper;
    private final CurrentUserService currentUserService;
    private final AuditLogService auditLogService;
    private final QuizContentValidator quizContentValidator;
    private final VideoSummaryService videoSummaryService;
    private final FlashcardSetRepository flashcardSetRepository;
    private final MasterCurriculumAccessService curriculumAccessService;
    private final CurriculumLessonTestLinkService lessonTestLinkService;

    // Liệt kê toàn bộ lesson chưa xóa của section để màn quản trị thấy cả trạng thái inactive.
    @Transactional(readOnly = true)
    public List<LessonResponse> listLessons(UUID sectionId) {
        CurriculumSection section = curriculumAccessService.findReadableSection(sectionId);
        return lessonRepository.findBySectionIdOrderBySortOrderAscCreatedAtAsc(section.getId()).stream()
                .map(curriculumDtoMapper::toLessonResponse)
                .toList();
    }

    // Liệt kê lesson của module tương thích cũ thông qua snapshot section hiện tại.
    @Transactional(readOnly = true)
    public List<LessonResponse> listModuleLessons(UUID moduleId) {
        return listLessons(curriculumAccessService.findReadableModuleSnapshot(moduleId).getId());
    }

    // Trả chi tiết lesson sau khi xác nhận nó thuộc master curriculum có thể đọc.
    @Transactional(readOnly = true)
    public LessonResponse getLesson(UUID lessonId) {
        return curriculumDtoMapper.toLessonResponse(curriculumAccessService.findReadableLesson(lessonId));
    }

    // Tạo lesson mới trong section với trạng thái và thứ tự mặc định của authoring.
    @Transactional
    public LessonResponse createLesson(UUID sectionId, LessonRequest request) {
        CurriculumSection section = curriculumAccessService.findUpdatableSection(sectionId);
        CurriculumLesson lesson = new CurriculumLesson();
        lesson.setSection(section);
        lesson.setLessonIdentityId(UUID.randomUUID());
        applyLessonRequest(lesson, request, true);
        int sortOrder = request.sortOrder() == null
                ? lessonRepository.findMaxSortOrderBySectionId(sectionId) + 1
                : request.sortOrder();
        lesson.setSortOrder(sortOrder);
        CurriculumLesson saved = lessonRepository.save(lesson);
        lessonTestLinkService.ensureQuizTest(saved);
        audit("LESSON_CREATED", "CURRICULUM_LESSON", saved.getId());
        return curriculumDtoMapper.toLessonResponse(saved);
    }

    // Tạo lesson qua mã module tương thích cũ sau khi tìm snapshot section.
    @Transactional
    public LessonResponse createModuleLesson(UUID moduleId, LessonRequest request) {
        return createLesson(curriculumAccessService.findUpdatableModuleSnapshot(moduleId).getId(), request);
    }

    // Cập nhật nội dung, loại, trạng thái, resource và thứ tự được gửi cho lesson.
    @Transactional
    public LessonResponse updateLesson(UUID lessonId, LessonRequest request) {
        CurriculumLesson lesson = curriculumAccessService.findUpdatableLesson(lessonId);
        applyLessonRequest(lesson, request, false);
        if (request.sortOrder() != null) {
            lesson.setSortOrder(request.sortOrder());
        }
        CurriculumLesson saved = lessonRepository.save(lesson);
        lessonTestLinkService.ensureQuizTest(saved);
        synchronizeFlashcardSetTitle(saved);
        audit("LESSON_UPDATED", "CURRICULUM_LESSON", saved.getId());
        return curriculumDtoMapper.toLessonResponse(saved);
    }

    // Xóa mềm lesson bằng trạng thái inactive và thời điểm xóa để giữ lịch sử.
    @Transactional
    public void deleteLesson(UUID lessonId) {
        CurriculumLesson lesson = curriculumAccessService.findUpdatableLesson(lessonId);
        lesson.setStatus(LessonStatus.INACTIVE);
        lesson.setDeletedAt(Instant.now());
        lessonRepository.save(lesson);
        audit("LESSON_DELETED", "CURRICULUM_LESSON", lesson.getId());
    }

    // Sắp xếp lại toàn bộ lesson và yêu cầu payload chứa mỗi lesson đúng một lần.
    @Transactional
    public List<LessonResponse> reorderLessons(UUID sectionId, ReorderRequest request) {
        CurriculumSection section = curriculumAccessService.findUpdatableSection(sectionId);
        List<CurriculumLesson> lessons = lessonRepository
                .findBySectionIdOrderBySortOrderAscCreatedAtAsc(section.getId());
        Map<UUID, CurriculumLesson> lessonsById = lessons.stream()
                .collect(
                        LinkedHashMap::new,
                        (map, lesson) -> map.put(lesson.getId(), lesson),
                        LinkedHashMap::putAll);
        assertReorderMatchesAllItems(request.ids(), lessonsById.keySet(), "Lesson");

        int sortOrder = 0;
        for (UUID lessonId : request.ids()) {
            lessonsById.get(lessonId).setSortOrder(sortOrder++);
        }
        List<CurriculumLesson> saved = lessonRepository.saveAll(lessons);
        audit("LESSONS_REORDERED", "CURRICULUM_SECTION", section.getId());
        return saved.stream()
                .sorted(Comparator.comparing(CurriculumLesson::getSortOrder))
                .map(curriculumDtoMapper::toLessonResponse)
                .toList();
    }

    // Giữ nguyên cách route module hiện tại chuyển yêu cầu sắp xếp vào nghiệp vụ section.
    @Transactional
    public List<LessonResponse> reorderModuleLessons(UUID moduleId, ReorderRequest request) {
        return reorderLessons(moduleId, request);
    }

    // Áp dụng và kiểm tra các trường lesson dùng chung cho cả thao tác tạo và cập nhật.
    private void applyLessonRequest(CurriculumLesson lesson, LessonRequest request, boolean create) {
        lesson.setTitle(normalizeRequired(request.title(), "Lesson title is required"));
        LessonType defaultType = create ? LessonType.RICH_TEXT : lesson.getType();
        String currentVideoUrl = lesson.getVideoUrl();
        lesson.setType(parseLessonType(resolveLessonType(request), defaultType));

        boolean videoLesson = lesson.getType() == LessonType.VIDEO;
        String requestedVideoUrl = normalizeNullable(request.videoUrl());
        if (create && videoLesson && requestedVideoUrl == null) {
            // Modal curriculum tạo khung lesson trước; URL YouTube được nhập ở màn chi tiết sau.
            lesson.setVideoUrl(null);
        } else {
            lesson.setVideoUrl(videoSummaryService.normalizeLessonVideoUrl(
                    currentVideoUrl,
                    requestedVideoUrl,
                    videoLesson));
        }

        String content = normalizeNullable(request.content());
        if (lesson.getType() == LessonType.QUIZ) {
            quizContentValidator.validate(content);
        }
        lesson.setContent(content);
        lesson.setAttachmentUrl(normalizeNullable(request.attachmentUrl()));
        lesson.setDurationSeconds(request.durationSeconds());
        if (create || request.isPreview() != null) {
            lesson.setPreview(Boolean.TRUE.equals(request.isPreview()));
        }
        LessonStatus defaultStatus = create ? LessonStatus.DRAFT : lesson.getStatus();
        lesson.setStatus(parseLessonStatus(request.status(), defaultStatus));

        if (request.resources() != null) {
            if (request.resources().size() > MAX_RESOURCES_PER_LESSON) {
                throw new BusinessException(
                        ErrorCode.INVALID_REQUEST,
                        "Lesson resources must not exceed 10 files");
            }
            lesson.getResources().clear();
            IntStream.range(0, request.resources().size())
                    .mapToObj(index -> toLessonResource(request.resources().get(index), index))
                    .forEach(lesson::addResource);
        }
    }

    // Đồng bộ tên bộ flashcard gắn với lesson khi tiêu đề lesson thay đổi.
    private void synchronizeFlashcardSetTitle(CurriculumLesson lesson) {
        if (lesson.getType() != LessonType.FLASHCARD) {
            return;
        }
        flashcardSetRepository.findByCurriculumLessonIdAndDeletedAtIsNull(lesson.getId())
                .ifPresent(flashcardSet -> {
                    flashcardSet.setTitle(lesson.getTitle());
                    flashcardSetRepository.save(flashcardSet);
                });
    }

    // Xác thực danh sách sắp xếp không thiếu, thừa hoặc lặp lesson.
    private void assertReorderMatchesAllItems(
            List<UUID> requestedIds,
            Set<UUID> existingIds,
            String itemName) {
        if (requestedIds == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, itemName + " reorder list is required");
        }
        Set<UUID> uniqueRequestedIds = new HashSet<>(requestedIds);
        if (uniqueRequestedIds.size() != requestedIds.size()) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    itemName + " reorder list contains duplicate ids");
        }
        if (!uniqueRequestedIds.equals(existingIds)) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    itemName + " reorder request must include every item exactly once");
        }
    }

    // Chuyển loại lesson từ chuỗi sang enum và hỗ trợ alias document của client cũ.
    private LessonType parseLessonType(String value, LessonType defaultType) {
        if (value == null || value.isBlank()) {
            return defaultType;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if ("DOCUMENT".equals(normalized)) {
            return LessonType.PDF;
        }
        try {
            return LessonType.valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Lesson type must be video, pdf, document, rich_text, quiz, flashcard, assignment, or essay");
        }
    }

    // Ưu tiên trường lessonType mới nhưng vẫn nhận trường type tương thích cũ.
    private String resolveLessonType(LessonRequest request) {
        String lessonType = normalizeNullable(request.lessonType());
        return lessonType == null ? normalizeNullable(request.type()) : lessonType;
    }

    // Chuyển trạng thái lesson từ chuỗi sang enum và giữ mặc định khi không được gửi.
    private LessonStatus parseLessonStatus(String value, LessonStatus defaultStatus) {
        if (value == null || value.isBlank()) {
            return defaultStatus;
        }
        try {
            return LessonStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Lesson status must be draft, published, or inactive");
        }
    }

    // Ghi audit thao tác lesson bằng người dùng đang đăng nhập.
    private void audit(String action, String targetType, UUID targetId) {
        UserAccount actor = currentUserService.requireAuthenticatedUser();
        auditLogService.record(actor.getEmail(), action, targetType, targetId.toString());
    }

    // Chuẩn hóa chuỗi bắt buộc và báo lỗi nếu chỉ có khoảng trắng.
    private String normalizeRequired(String value, String message) {
        String normalized = normalizeNullable(value);
        if (normalized == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, message);
        }
        return normalized;
    }

    // Chuẩn hóa chuỗi tùy chọn, biến giá trị rỗng thành null.
    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    // Chuyển resource trong request thành entity con với tên và thứ tự an toàn.
    private CurriculumLessonResource toLessonResource(LessonResourceRequest request, int index) {
        CurriculumLessonResource resource = new CurriculumLessonResource();
        String url = normalizeRequired(request.url(), "Resource URL is required");
        resource.setUrl(url);
        resource.setObjectPath(normalizeNullable(request.objectPath()));
        resource.setName(resolveResourceName(request, url, index));
        resource.setFileSize(request.fileSize());
        resource.setContentType(normalizeNullable(request.contentType()));
        resource.setSortOrder(request.sortOrder() == null ? index : request.sortOrder());
        return resource;
    }

    // Chọn tên resource từ name, fileName, URL hoặc tên dự phòng theo thứ tự ưu tiên.
    private String resolveResourceName(LessonResourceRequest request, String url, int index) {
        String name = normalizeNullable(request.name());
        if (name == null) {
            name = normalizeNullable(request.fileName());
        }
        if (name == null) {
            name = fileNameFromUrl(url);
        }
        if (name == null) {
            name = "resource-" + (index + 1);
        }
        if (name.length() > 255) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Resource name must not exceed 255 characters");
        }
        return name;
    }

    // Tách tên file cuối URL sau khi loại bỏ fragment và query string.
    private String fileNameFromUrl(String url) {
        int fragmentIndex = url.indexOf('#');
        String withoutFragment = fragmentIndex < 0 ? url : url.substring(0, fragmentIndex);
        int queryIndex = withoutFragment.indexOf('?');
        String withoutQuery = queryIndex < 0 ? withoutFragment : withoutFragment.substring(0, queryIndex);
        int slashIndex = withoutQuery.lastIndexOf('/');
        String fileName = slashIndex < 0 ? withoutQuery : withoutQuery.substring(slashIndex + 1);
        return normalizeNullable(fileName);
    }
}
