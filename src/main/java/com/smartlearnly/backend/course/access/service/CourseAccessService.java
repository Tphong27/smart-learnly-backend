package com.smartlearnly.backend.course.access.service;

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

    // Xác định người dùng hiện tại có vai trò quản lý toàn bộ khóa học hay không.
    public boolean isCurrentUserCourseManager() {
        return hasRole("ADMIN") || hasRole("TMO");
    }

    // Xác định người dùng hiện tại có vai trò chuyên gia nội dung hay không.
    public boolean isCurrentUserSme() {
        return hasRole("SME");
    }

    // Xác định người dùng hiện tại có vai trò giảng viên hay không.
    public boolean isCurrentUserTrainer() {
        return hasRole("TRAINER");
    }

    // Lấy mã người dùng đã đăng nhập để đối chiếu phân công khóa học.
    public UUID getCurrentUserId() {
        UserAccount currentUser = currentUserService.requireAuthenticatedUser();

        return currentUser.getId();
    }

    // Chỉ cho Admin hoặc TMO thực hiện nghiệp vụ phân công khóa học.
    public void requireCourseManager() {
        if (!isCurrentUserCourseManager()) {
            throw new BusinessException(
                    ErrorCode.FORBIDDEN,
                    "Only Admin or TMO can manage course assignment");
        }
    }

    // Kiểm tra người dùng có quyền đọc khóa học theo vai trò và phân công.
    public void requireReadableCourse(UUID courseId) {
        requireAssignmentWhenNecessary(courseId);
    }

    // Kiểm tra người dùng có quyền cập nhật khóa học theo vai trò và phân công.
    public void requireUpdatableCourse(UUID courseId) {
        requireAssignmentWhenNecessary(courseId);
    }

    // Yêu cầu SME hoặc giảng viên phải được phân công vào khóa học; quản lý được truy cập toàn bộ.
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

    // Kiểm tra một vai trò Spring Security trong phiên đăng nhập hiện tại.
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
