package com.smartlearnly.backend.curriculum.admin.service;

import com.smartlearnly.backend.common.audit.AuditAction;
import com.smartlearnly.backend.common.audit.CourseAuditRecorder;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.course.access.service.CourseAccessService;
import com.smartlearnly.backend.curriculum.dto.ModuleRequest;
import com.smartlearnly.backend.curriculum.dto.ModuleResponse;
import com.smartlearnly.backend.curriculum.dto.ReorderRequest;
import com.smartlearnly.backend.curriculum.dto.SectionRequest;
import com.smartlearnly.backend.curriculum.dto.SectionResponse;
import com.smartlearnly.backend.curriculum.entity.CurriculumSection;
import com.smartlearnly.backend.curriculum.entity.CurriculumVersion;
import com.smartlearnly.backend.curriculum.repository.CurriculumSectionRepository;
import com.smartlearnly.backend.curriculum.service.CurriculumDtoMapper;
import com.smartlearnly.backend.learning.module.entity.CourseModule;
import com.smartlearnly.backend.learning.module.repository.CourseModuleRepository;
import com.smartlearnly.backend.user.entity.UserAccount;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CurriculumSectionAdminService {
    private final CurriculumSectionRepository sectionRepository;
    private final CourseModuleRepository courseModuleRepository;
    private final CurriculumDtoMapper curriculumDtoMapper;
    private final CurrentUserService currentUserService;
    private final CourseAuditRecorder courseAuditRecorder;
    private final CourseAccessService courseAccessService;
    private final MasterCurriculumAccessService curriculumAccessService;

    // Liệt kê section của master curriculum theo thứ tự hiển thị ổn định.
    @Transactional(readOnly = true)
    public List<SectionResponse> listSections(UUID courseId) {
        CurriculumVersion version = curriculumAccessService.findReadableVersion(courseId);
        return orderedSections(version).stream()
                .map(curriculumDtoMapper::toSectionResponse)
                .toList();
    }

    // Liệt kê module tương thích cũ từ các snapshot section của master curriculum.
    @Transactional(readOnly = true)
    public List<ModuleResponse> listModules(UUID courseId) {
        CurriculumVersion version = curriculumAccessService.findReadableVersion(courseId);
        return orderedSections(version).stream()
                .map(curriculumDtoMapper::toModuleResponse)
                .toList();
    }

    // Trả chi tiết một section sau khi kiểm tra quyền đọc khóa học.
    @Transactional(readOnly = true)
    public SectionResponse getSection(UUID sectionId) {
        return curriculumDtoMapper.toSectionResponse(curriculumAccessService.findReadableSection(sectionId));
    }

    // Trả chi tiết module tương thích cũ từ snapshot section hiện tại.
    @Transactional(readOnly = true)
    public ModuleResponse getModule(UUID moduleId) {
        return curriculumDtoMapper.toModuleResponse(curriculumAccessService.findReadableModuleSnapshot(moduleId));
    }

    // Tạo section mới cùng bản ghi module chuẩn để hai API cũ và mới tiếp tục đồng bộ.
    @Transactional
    public SectionResponse createSection(UUID courseId, SectionRequest request) {
        courseAccessService.requireUpdatableCourse(courseId);
        CurriculumVersion version = curriculumAccessService.findOrCreateUpdatableVersion(courseId);
        String title = normalizeRequired(request.title(), "Module title is required");
        int sortOrder = request.sortOrder() == null
                ? sectionRepository.findMaxSortOrderByCurriculumVersionId(version.getId()) + 1
                : request.sortOrder();

        CourseModule module = new CourseModule();
        module.setCourseId(courseId);
        module.setTitle(title);
        module.setOrderIndex(sortOrder);
        module.setStatus(CourseModule.STATUS_ACTIVE);
        module.setSystem(false);
        CourseModule savedModule = courseModuleRepository.save(module);

        CurriculumSection section = new CurriculumSection();
        section.setCurriculumVersion(version);
        section.setSourceModuleId(savedModule.getId());
        section.setTitle(title);
        section.setSortOrder(sortOrder);
        CurriculumSection saved = sectionRepository.save(section);
        audit(AuditAction.SECTION_CREATED, "CURRICULUM_SECTION", saved.getId(), courseId, saved.getTitle());
        return curriculumDtoMapper.toSectionResponse(saved);
    }

    // Tạo module qua hợp đồng cũ nhưng dùng chung nghiệp vụ section chuẩn.
    @Transactional
    public ModuleResponse createModule(UUID courseId, ModuleRequest request) {
        SectionResponse section = createSection(courseId, request.toSectionRequest());
        CurriculumSection snapshot = sectionRepository.findById(section.id())
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.INTERNAL_ERROR,
                        "Created module snapshot was not found"));
        return curriculumDtoMapper.toModuleResponse(snapshot);
    }

    // Cập nhật tiêu đề/thứ tự section và đồng bộ module chuẩn liên quan.
    @Transactional
    public SectionResponse updateSection(UUID sectionId, SectionRequest request) {
        CurriculumSection section = curriculumAccessService.findUpdatableSection(sectionId);
        section.setTitle(normalizeRequired(request.title(), "Section title is required"));
        if (request.sortOrder() != null) {
            section.setSortOrder(request.sortOrder());
        }
        CurriculumSection saved = sectionRepository.save(section);
        synchronizeCanonicalModule(saved);
        UUID courseId = saved.getCurriculumVersion().getCourseId();
        audit(AuditAction.SECTION_UPDATED, "CURRICULUM_SECTION", saved.getId(), courseId, saved.getTitle());
        return curriculumDtoMapper.toSectionResponse(saved);
    }

    // Cập nhật module qua snapshot section và giữ hợp đồng phản hồi module hiện tại.
    @Transactional
    public ModuleResponse updateModule(UUID moduleId, ModuleRequest request) {
        CurriculumSection snapshot = curriculumAccessService.findUpdatableModuleSnapshot(moduleId);
        updateSection(snapshot.getId(), request.toSectionRequest());
        return curriculumDtoMapper.toModuleResponse(snapshot);
    }

    // Xóa section cùng các lesson con và chuyển module chuẩn sang không hoạt động.
    @Transactional
    public void deleteSection(UUID sectionId) {
        CurriculumSection section = curriculumAccessService.findUpdatableSection(sectionId);
        UUID courseId = section.getCurriculumVersion().getCourseId();
        String title = section.getTitle();
        deactivateCanonicalModule(section);
        sectionRepository.delete(section);
        audit(AuditAction.SECTION_DELETED, "CURRICULUM_SECTION", sectionId, courseId, title);
    }

    // Xóa module qua snapshot section tương ứng để hai mô hình dữ liệu không lệch nhau.
    @Transactional
    public void deleteModule(UUID moduleId) {
        CurriculumSection snapshot = curriculumAccessService.findUpdatableModuleSnapshot(moduleId);
        deleteSection(snapshot.getId());
    }

    // Sắp xếp lại toàn bộ section và yêu cầu payload chứa mỗi section đúng một lần.
    @Transactional
    public List<SectionResponse> reorderSections(UUID courseId, ReorderRequest request) {
        CurriculumVersion version = curriculumAccessService.findUpdatableVersion(courseId);
        List<CurriculumSection> sections = sectionRepository
                .findByCurriculumVersionIdOrderBySortOrderAscCreatedAtAsc(version.getId());
        Map<UUID, CurriculumSection> sectionsById = sections.stream()
                .collect(
                        LinkedHashMap::new,
                        (map, section) -> map.put(section.getId(), section),
                        LinkedHashMap::putAll);
        assertReorderMatchesAllItems(request.ids(), sectionsById.keySet(), "Section");

        int sortOrder = 0;
        for (UUID sectionId : request.ids()) {
            sectionsById.get(sectionId).setSortOrder(sortOrder++);
        }
        List<CurriculumSection> saved = sectionRepository.saveAll(sections);
        saved.forEach(this::synchronizeCanonicalModule);
        audit(
                AuditAction.SECTIONS_REORDERED,
                "CURRICULUM_VERSION",
                version.getId(),
                version.getCourseId(),
                "Sections reordered");
        return saved.stream()
                .sorted(Comparator.comparing(CurriculumSection::getSortOrder))
                .map(curriculumDtoMapper::toSectionResponse)
                .toList();
    }

    // Đổi mã module từ API cũ sang mã snapshot section trước khi dùng nghiệp vụ sắp xếp chuẩn.
    @Transactional
    public List<ModuleResponse> reorderModules(UUID courseId, ReorderRequest request) {
        CurriculumVersion version = curriculumAccessService.findUpdatableVersion(courseId);
        Map<UUID, UUID> snapshotIdsByModuleId = orderedSections(version).stream()
                .collect(
                        LinkedHashMap::new,
                        (map, snapshot) -> map.put(snapshot.getSourceModuleId(), snapshot.getId()),
                        LinkedHashMap::putAll);
        List<UUID> snapshotIds = request.ids().stream()
                .map(moduleId -> {
                    UUID snapshotId = snapshotIdsByModuleId.get(moduleId);
                    if (snapshotId == null) {
                        throw new BusinessException(ErrorCode.INVALID_REQUEST, "Module reorder payload is invalid");
                    }
                    return snapshotId;
                })
                .toList();
        reorderSections(courseId, new ReorderRequest(snapshotIds));
        return listModules(courseId);
    }

    // Đồng bộ tiêu đề và thứ tự của snapshot về bản ghi module chuẩn.
    private void synchronizeCanonicalModule(CurriculumSection snapshot) {
        if (snapshot.getSourceModuleId() == null) {
            return;
        }
        CourseModule module = courseModuleRepository.findById(snapshot.getSourceModuleId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR, "Canonical module was not found"));
        module.setTitle(snapshot.getTitle());
        module.setOrderIndex(snapshot.getSortOrder());
        courseModuleRepository.save(module);
    }

    // Đánh dấu module chuẩn không hoạt động khi snapshot section bị xóa.
    private void deactivateCanonicalModule(CurriculumSection snapshot) {
        if (snapshot.getSourceModuleId() == null) {
            return;
        }
        courseModuleRepository.findById(snapshot.getSourceModuleId()).ifPresent(module -> {
            module.setStatus(CourseModule.STATUS_INACTIVE);
            courseModuleRepository.save(module);
        });
    }

    // Xác thực danh sách sắp xếp không thiếu, thừa hoặc lặp section.
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

    // Ghi audit master curriculum kèm metadata.courseId cho timeline change-history.
    private void audit(
            AuditAction action, String targetType, UUID targetId, UUID courseId, String summary) {
        UserAccount actor = currentUserService.requireAuthenticatedUser();
        courseAuditRecorder.recordMaster(actor, action, targetType, targetId, courseId, summary);
    }

    // Chuẩn hóa tiêu đề bắt buộc và báo lỗi nếu chỉ có khoảng trắng.
    private String normalizeRequired(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, message);
        }
        return value.trim();
    }

    // Sắp xếp section ổn định theo thứ tự, thời điểm tạo và mã định danh.
    private List<CurriculumSection> orderedSections(CurriculumVersion version) {
        return version.getSections().stream()
                .sorted(Comparator
                        .comparing(CurriculumSection::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(CurriculumSection::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(CurriculumSection::getId, Comparator.nullsLast(UUID::compareTo)))
                .toList();
    }
}
