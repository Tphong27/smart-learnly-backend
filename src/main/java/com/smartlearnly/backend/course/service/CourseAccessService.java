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

    public boolean isCurrentUserTrainer() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null) {
            return false;
        }

        return authentication.getAuthorities()
                .stream()
                .anyMatch(authority ->
                        "ROLE_TRAINER".equals(authority.getAuthority())
                );
    }

    public UUID getCurrentUserId() {
        UserAccount currentUser =
                currentUserService.requireAuthenticatedUser();

        return currentUser.getId();
    }

    public void requireReadableCourse(UUID courseId) {
        requireTrainerAssignmentWhenNecessary(courseId);
    }

    public void requireUpdatableCourse(UUID courseId) {
        requireTrainerAssignmentWhenNecessary(courseId);
    }

    private void requireTrainerAssignmentWhenNecessary(UUID courseId) {
        if (!isCurrentUserTrainer()) {
            return;
        }

        UUID trainerId = getCurrentUserId();

        boolean assigned =
                courseRepository.existsTrainerAssignment(
                        courseId,
                        trainerId
                );

        if (!assigned) {
            throw new BusinessException(
                    ErrorCode.RESOURCE_NOT_FOUND,
                    "Course was not found"
            );
        }
    }
}