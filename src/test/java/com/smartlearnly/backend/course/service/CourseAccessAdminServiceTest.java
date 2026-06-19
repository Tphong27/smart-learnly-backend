package com.smartlearnly.backend.course.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.common.audit.AuditLogService;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.course.dto.CourseAccessResponse;
import com.smartlearnly.backend.course.dto.UpdateCourseAccessRequest;
import com.smartlearnly.backend.course.entity.Course;
import com.smartlearnly.backend.course.repository.CourseRepository;
import com.smartlearnly.backend.user.entity.UserAccount;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CourseAccessAdminServiceTest {
    @Mock
    private CourseRepository courseRepository;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private AuditLogService auditLogService;

    private CourseAccessAdminService service;

    @BeforeEach
    void setUp() {
        service = new CourseAccessAdminService(
                courseRepository,
                currentUserService,
                auditLogService
        );
    }

    @Test
    void blockShouldRequireReason() {
        Course course = course();
        UserAccount actor = actor();
        when(courseRepository.findByIdAndDeletedAtIsNullForUpdate(course.getId()))
                .thenReturn(Optional.of(course));
        when(currentUserService.requireAuthenticatedUser()).thenReturn(actor);

        assertThatThrownBy(() ->
                service.update(course.getId(), new UpdateCourseAccessRequest(true, " ")))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));

        verify(courseRepository, never()).save(course);
    }

    @Test
    void blockAndUnblockShouldPersistExplicitAccessState() {
        Course course = course();
        UserAccount actor = actor();
        when(courseRepository.findByIdAndDeletedAtIsNullForUpdate(course.getId()))
                .thenReturn(Optional.of(course));
        when(currentUserService.requireAuthenticatedUser()).thenReturn(actor);

        CourseAccessResponse blocked =
                service.update(course.getId(), new UpdateCourseAccessRequest(true, "Compliance hold"));
        CourseAccessResponse unblocked =
                service.update(course.getId(), new UpdateCourseAccessRequest(false, null));

        assertThat(blocked.accessBlocked()).isTrue();
        assertThat(blocked.accessBlockReason()).isEqualTo("Compliance hold");
        assertThat(unblocked.accessBlocked()).isFalse();
        assertThat(unblocked.accessBlockReason()).isNull();
        verify(auditLogService).record(
                actor.getEmail(),
                "COURSE_ACCESS_BLOCKED",
                "COURSE",
                course.getId().toString()
        );
        verify(auditLogService).record(
                actor.getEmail(),
                "COURSE_ACCESS_UNBLOCKED",
                "COURSE",
                course.getId().toString()
        );
    }

    private Course course() {
        Course course = new Course();
        course.setId(UUID.randomUUID());
        return course;
    }

    private UserAccount actor() {
        UserAccount actor = new UserAccount();
        actor.setId(UUID.randomUUID());
        actor.setEmail("admin@smartlearnly.dev");
        return actor;
    }
}
