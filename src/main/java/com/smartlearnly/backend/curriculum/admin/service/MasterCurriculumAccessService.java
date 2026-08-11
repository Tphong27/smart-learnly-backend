package com.smartlearnly.backend.curriculum.admin.service;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.course.access.service.CourseAccessService;
import com.smartlearnly.backend.course.entity.Course;
import com.smartlearnly.backend.course.entity.CourseStatus;
import com.smartlearnly.backend.course.repository.CourseRepository;
import com.smartlearnly.backend.curriculum.entity.CurriculumLesson;
import com.smartlearnly.backend.curriculum.entity.CurriculumScope;
import com.smartlearnly.backend.curriculum.entity.CurriculumSection;
import com.smartlearnly.backend.curriculum.entity.CurriculumStatus;
import com.smartlearnly.backend.curriculum.entity.CurriculumVersion;
import com.smartlearnly.backend.curriculum.repository.CurriculumLessonRepository;
import com.smartlearnly.backend.curriculum.repository.CurriculumSectionRepository;
import com.smartlearnly.backend.curriculum.repository.CurriculumVersionRepository;
import com.smartlearnly.backend.learning.module.entity.CourseModule;
import com.smartlearnly.backend.learning.module.repository.CourseModuleRepository;
import com.smartlearnly.backend.user.entity.UserAccount;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MasterCurriculumAccessService {
    private final CourseRepository courseRepository;
    private final CurriculumVersionRepository curriculumVersionRepository;
    private final CurriculumSectionRepository sectionRepository;
    private final CourseModuleRepository courseModuleRepository;
    private final CurriculumLessonRepository lessonRepository;
    private final CurrentUserService currentUserService;
    private final CourseAccessService courseAccessService;

    // Lấy phiên bản master mới nhất sau khi xác nhận người dùng có quyền đọc khóa học.
    CurriculumVersion findReadableVersion(UUID courseId) {
        courseAccessService.requireReadableCourse(courseId);
        findExistingCourse(courseId);
        return findLatestMasterVersion(courseId);
    }

    // Lấy phiên bản master mới nhất sau khi xác nhận người dùng có quyền chỉnh sửa khóa học.
    CurriculumVersion findUpdatableVersion(UUID courseId) {
        courseAccessService.requireUpdatableCourse(courseId);
        findExistingCourse(courseId);
        return findLatestMasterVersion(courseId);
    }

    // Lấy hoặc khởi tạo phiên bản master khi quản trị viên tạo section đầu tiên.
    CurriculumVersion findOrCreateUpdatableVersion(UUID courseId) {
        courseAccessService.requireUpdatableCourse(courseId);
        Course course = findExistingCourse(courseId);
        return curriculumVersionRepository
                .findFirstByCourseIdAndScopeOrderByVersionNumberDescCreatedAtDesc(
                        courseId,
                        CurriculumScope.MASTER)
                .orElseGet(() -> createInitialMasterVersion(course));
    }

    // Tìm section thuộc master curriculum và kiểm tra quyền đọc khóa học sở hữu nó.
    CurriculumSection findReadableSection(UUID sectionId) {
        CurriculumSection section = sectionRepository.findById(sectionId)
                .map(this::requireMasterSection)
                .orElseGet(() -> findReadableModuleSnapshot(sectionId));
        UUID courseId = getCourseId(section);
        UUID resolvedCourseId = courseId;
        if (courseModuleRepository.findById(sectionId)
                .filter(module -> !resolvedCourseId.equals(module.getCourseId()))
                .isPresent()) {
            section = findReadableModuleSnapshot(sectionId);
            courseId = getCourseId(section);
        }
        courseAccessService.requireReadableCourse(courseId);
        findExistingCourse(courseId);
        return section;
    }

    /** Tìm section thuộc master curriculum và kiểm tra quyền cập nhật khóa học sở hữu nó. */
    public CurriculumSection findUpdatableSection(UUID sectionId) {
        CurriculumSection section = sectionRepository.findById(sectionId)
                .map(this::requireMasterSection)
                .orElseGet(() -> findUpdatableModuleSnapshot(sectionId));
        UUID courseId = getCourseId(section);
        UUID resolvedCourseId = courseId;
        if (courseModuleRepository.findById(sectionId)
                .filter(module -> !resolvedCourseId.equals(module.getCourseId()))
                .isPresent()) {
            section = findUpdatableModuleSnapshot(sectionId);
            courseId = getCourseId(section);
        }
        courseAccessService.requireUpdatableCourse(courseId);
        findExistingCourse(courseId);
        return section;
    }

    // Ánh xạ module tương thích cũ sang snapshot section của master curriculum để đọc.
    CurriculumSection findReadableModuleSnapshot(UUID moduleId) {
        CourseModule module = courseModuleRepository.findById(moduleId)
                .filter(candidate -> !Boolean.TRUE.equals(candidate.getSystem()))
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Module was not found"));
        CurriculumVersion version = findReadableVersion(module.getCourseId());
        return sectionRepository.findBySourceModuleIdAndCurriculumVersionId(moduleId, version.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Module was not found"));
    }

    // Ánh xạ module đang hoạt động sang snapshot section của master curriculum để cập nhật.
    public CurriculumSection findUpdatableModuleSnapshot(UUID moduleId) {
        CourseModule module = courseModuleRepository.findById(moduleId)
                .filter(candidate -> !Boolean.TRUE.equals(candidate.getSystem()))
                .filter(candidate -> CourseModule.STATUS_ACTIVE.equals(candidate.getStatus()))
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Module was not found"));
        CurriculumVersion version = findOrCreateUpdatableVersion(module.getCourseId());
        return sectionRepository.findBySourceModuleIdAndCurriculumVersionId(moduleId, version.getId())
                .orElseGet(() -> createMissingModuleSnapshot(module, version));
    }

    // Tìm lesson thuộc master curriculum và kiểm tra quyền đọc khóa học sở hữu nó.
    CurriculumLesson findReadableLesson(UUID lessonId) {
        CurriculumLesson lesson = findMasterLesson(lessonId);
        UUID courseId = getCourseId(lesson);
        courseAccessService.requireReadableCourse(courseId);
        findExistingCourse(courseId);
        return lesson;
    }

    // Tìm lesson thuộc master curriculum và kiểm tra quyền cập nhật khóa học sở hữu nó.
    CurriculumLesson findUpdatableLesson(UUID lessonId) {
        CurriculumLesson lesson = findMasterLesson(lessonId);
        UUID courseId = getCourseId(lesson);
        courseAccessService.requireUpdatableCourse(courseId);
        findExistingCourse(courseId);
        return lesson;
    }

    // Lấy phiên bản master mới nhất hoặc báo lỗi khi khóa học chưa có curriculum.
    private CurriculumVersion findLatestMasterVersion(UUID courseId) {
        return curriculumVersionRepository
                .findFirstByCourseIdAndScopeOrderByVersionNumberDescCreatedAtDesc(
                        courseId,
                        CurriculumScope.MASTER)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Master curriculum version was not found"));
    }

    // Khởi tạo master curriculum đầu tiên với trạng thái đồng bộ theo khóa học.
    private CurriculumVersion createInitialMasterVersion(Course course) {
        CurriculumVersion version = new CurriculumVersion();
        version.setCourseId(course.getId());
        version.setScope(CurriculumScope.MASTER);
        boolean courseIsPublished = course.getStatus() == CourseStatus.PUBLISHED;
        version.setStatus(courseIsPublished ? CurriculumStatus.PUBLISHED : CurriculumStatus.DRAFT);
        if (courseIsPublished) {
            version.setPublishedAt(Instant.now());
        }
        version.setVersionNumber(curriculumVersionRepository.findMaxMasterVersionNumber(
                course.getId(),
                CurriculumScope.MASTER) + 1);
        version.setTitle(course.getTitle());
        UserAccount currentUser = currentUserService.requireAuthenticatedUser();
        version.setCreatedBy(currentUser.getId());
        return curriculumVersionRepository.save(version);
    }

    // Tìm khóa học chưa bị xóa để ngăn thao tác trên dữ liệu đã lưu trữ mềm.
    private CurriculumSection createMissingModuleSnapshot(CourseModule module, CurriculumVersion version) {
        CurriculumSection section = new CurriculumSection();
        section.setCurriculumVersion(version);
        section.setSourceModuleId(module.getId());
        section.setTitle(module.getTitle());
        section.setSortOrder(module.getOrderIndex());
        return sectionRepository.save(section);
    }

    private Course findExistingCourse(UUID courseId) {
        return courseRepository.findByIdAndDeletedAtIsNull(courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Course was not found"));
    }

    // Tìm section và đảm bảo section đó thuộc phạm vi MASTER.
    private CurriculumSection findMasterSection(UUID sectionId) {
        CurriculumSection section = sectionRepository.findById(sectionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Section was not found"));
        return requireMasterSection(section);
    }

    private CurriculumSection requireMasterSection(CurriculumSection section) {
        CurriculumVersion version = section.getCurriculumVersion();
        if (version == null || version.getScope() != CurriculumScope.MASTER) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Section was not found");
        }
        return section;
    }

    // Lấy mã khóa học từ section master và từ chối quan hệ curriculum không hợp lệ.
    private UUID getCourseId(CurriculumSection section) {
        CurriculumVersion version = section.getCurriculumVersion();
        if (version == null || version.getCourseId() == null || version.getScope() != CurriculumScope.MASTER) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Section was not found");
        }
        return version.getCourseId();
    }

    // Tìm lesson và đảm bảo lesson đó nằm trong section của master curriculum.
    private CurriculumLesson findMasterLesson(UUID lessonId) {
        CurriculumLesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Lesson was not found"));
        CurriculumSection section = lesson.getSection();
        CurriculumVersion version = section == null ? null : section.getCurriculumVersion();
        if (version == null || version.getScope() != CurriculumScope.MASTER) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Lesson was not found");
        }
        return lesson;
    }

    // Lấy mã khóa học từ lesson master và từ chối quan hệ curriculum không hợp lệ.
    private UUID getCourseId(CurriculumLesson lesson) {
        CurriculumSection section = lesson.getSection();
        CurriculumVersion version = section == null ? null : section.getCurriculumVersion();
        if (version == null || version.getCourseId() == null || version.getScope() != CurriculumScope.MASTER) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Lesson was not found");
        }
        return version.getCourseId();
    }
}
