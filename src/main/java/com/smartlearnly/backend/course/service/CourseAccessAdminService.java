package com.smartlearnly.backend.course.service;

import com.smartlearnly.backend.common.audit.AuditLogService;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.course.dto.CourseAccessResponse;
import com.smartlearnly.backend.course.dto.UpdateCourseAccessRequest;
import com.smartlearnly.backend.course.entity.Course;
import com.smartlearnly.backend.course.repository.CourseRepository;
import com.smartlearnly.backend.user.entity.UserAccount;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CourseAccessAdminService {
    private final CourseRepository courseRepository;
    private final CurrentUserService currentUserService;
    private final AuditLogService auditLogService;

    @Transactional
    public CourseAccessResponse update(UUID courseId, UpdateCourseAccessRequest request) {
        Course course = courseRepository.findByIdAndDeletedAtIsNullForUpdate(courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Course was not found"));
        UserAccount actor = currentUserService.requireAuthenticatedUser();

        if (Boolean.TRUE.equals(request.accessBlocked())) {
            String reason = normalizeReason(request.reason());
            if (reason == null) {
                throw new BusinessException(
                        ErrorCode.INVALID_REQUEST,
                        "A reason is required when blocking course access"
                );
            }
            course.setAccessBlockedAt(Instant.now());
            course.setAccessBlockReason(reason);
            course.setAccessBlockedBy(actor);
            courseRepository.save(course);
            auditLogService.record(actor.getEmail(), "COURSE_ACCESS_BLOCKED", "COURSE", courseId.toString());
        }
        else {
            course.setAccessBlockedAt(null);
            course.setAccessBlockReason(null);
            course.setAccessBlockedBy(null);
            courseRepository.save(course);
            auditLogService.record(actor.getEmail(), "COURSE_ACCESS_UNBLOCKED", "COURSE", courseId.toString());
        }

        return new CourseAccessResponse(
                course.getId(),
                course.getAccessBlockedAt() != null,
                course.getAccessBlockedAt(),
                course.getAccessBlockReason(),
                course.getAccessBlockedBy() == null ? null : course.getAccessBlockedBy().getId()
        );
    }

    private String normalizeReason(String reason) {
        if (reason == null) {
            return null;
        }
        String normalized = reason.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
