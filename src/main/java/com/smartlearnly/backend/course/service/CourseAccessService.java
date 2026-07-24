package com.smartlearnly.backend.course.service;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.course.repository.CourseRepository;
import com.smartlearnly.backend.user.entity.UserAccount;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CourseAccessService {

    private final CourseRepository courseRepository;
    private final CurrentUserService currentUserService;

    public boolean isCurrentUserCourseManager() {
        return hasRole("ADMIN") || hasRole("TMO");
    }

    public boolean isCurrentUserSme() {
        return hasRole("SME");
    }

    public boolean isCurrentUserTrainer() {
        return hasRole("TRAINER");
    }

    public UUID getCurrentUserId() {
        UserAccount currentUser = currentUserService.requireAuthenticatedUser();

        return currentUser.getId();
    }

    public void requireCourseManager() {
        if (!isCurrentUserCourseManager()) {
            throw new BusinessException(
                    ErrorCode.FORBIDDEN,
                    "Only Admin or TMO can manage course assignment");
        }
    }

    public void requireReadableCourse(UUID courseId) {
        requireAssignmentWhenNecessary(courseId);
    }

    public void requireUpdatableCourse(UUID courseId) {
        requireAssignmentWhenNecessary(courseId);
    }

    private void requireAssignmentWhenNecessary(UUID courseId) {
        if (isCurrentUserCourseManager()) {
            return;
        }

        UUID currentUserId = getCurrentUserId();
        boolean assigned;

        if (isCurrentUserSme()) {
            assigned = courseRepository
                    .existsByIdAndAssignedSme_IdAndDeletedAtIsNull(
                            courseId,
                            currentUserId);
        } else if (isCurrentUserTrainer()) {
            assigned = courseRepository.existsTrainerAssignment(
                    courseId,
                    currentUserId);
        } else {
            assigned = false;
        }

        if (!assigned) {
            throw new BusinessException(
                    ErrorCode.RESOURCE_NOT_FOUND,
                    "Course was not found");
        }
    }

    private boolean hasRole(String role) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        String expectedAuthority = "ROLE_" + role;

        return authentication.getAuthorities()
                .stream()
                .anyMatch(authority -> expectedAuthority.equals(authority.getAuthority()));
    }
}