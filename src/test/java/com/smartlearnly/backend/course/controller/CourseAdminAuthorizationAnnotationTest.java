package com.smartlearnly.backend.course.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartlearnly.backend.course.access.controller.AdminCourseAccessController;
import com.smartlearnly.backend.course.authoring.controller.AdminCourseController;
import com.smartlearnly.backend.curriculum.admin.controller.AdminCourseLessonController;
import com.smartlearnly.backend.curriculum.admin.controller.AdminCourseSectionController;
import com.smartlearnly.backend.file.controller.AdminUploadController;
import com.smartlearnly.backend.course.category.controller.AdminCategoryController;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;

class CourseAdminAuthorizationAnnotationTest {

    @Test
    void courseReadEndpointsShouldStillAllowTmo() {
        assertThat(preAuthorizeValue(AdminCourseController.class))
                .isEqualTo("hasAnyRole('TMO', 'SME', 'TRAINER')");
    }

    @Test
    void courseWritesShouldUseTmoAndTrainerOwnership() throws Exception {
        Method createMethod = AdminCourseController.class.getMethod(
                "create",
                com.smartlearnly.backend.course.authoring.dto.CreateCourseRequest.class);
        Method updateMethod = AdminCourseController.class.getMethod(
                "update",
                java.util.UUID.class,
                com.smartlearnly.backend.course.authoring.dto.UpdateCourseRequest.class);
        Method deleteMethod = AdminCourseController.class.getMethod(
                "delete",
                java.util.UUID.class);

        assertThat(preAuthorizeValue(createMethod))
                .isEqualTo("hasRole('TMO')")
                .doesNotContain("ADMIN");
        assertThat(preAuthorizeValue(updateMethod))
                .isEqualTo("hasRole('TRAINER')")
                .doesNotContain("ADMIN", "TMO", "SME");
        assertThat(preAuthorizeValue(deleteMethod))
                .isEqualTo("hasRole('TMO')")
                .doesNotContain("ADMIN");
    }

    @Test
    void courseContentShouldAllowCourseAuthoringRolesWithoutAdmin() {
        assertThat(preAuthorizeValue(AdminCourseSectionController.class))
                .isEqualTo("hasAnyRole('TMO', 'SME', 'TRAINER')");
        assertThat(preAuthorizeValue(AdminCourseLessonController.class))
                .isEqualTo("hasAnyRole('TMO', 'SME', 'TRAINER')");
    }

    @Test
    void courseThumbnailUploadShouldRejectTmoAndAdmin() {
        assertThat(preAuthorizeValue(AdminUploadController.class))
                .isEqualTo("hasAnyRole('SME', 'TRAINER')")
                .doesNotContain("TMO", "ADMIN");
    }

    @Test
    void courseAccessBlockShouldRemainTmoOnly() {
        assertThat(preAuthorizeValue(AdminCourseAccessController.class))
                .isEqualTo("hasRole('TMO')");
    }

    @Test
    void categoryReadEndpointsShouldStillAllowStaffWithoutAdmin() throws Exception {
        assertThat(preAuthorizeValue(AdminCategoryController.class))
                .isEqualTo("hasAnyRole('TMO', 'SME', 'TRAINER')");

        Method createMethod = AdminCategoryController.class.getMethod(
                "create",
                com.smartlearnly.backend.course.category.dto.CreateCategoryRequest.class);
        assertThat(preAuthorizeValue(createMethod))
                .isEqualTo("hasRole('TMO')")
                .doesNotContain("ADMIN");
    }

    private String preAuthorizeValue(Class<?> controllerClass) {
        PreAuthorize annotation = AnnotatedElementUtils.findMergedAnnotation(
                controllerClass,
                PreAuthorize.class
        );
        assertThat(annotation).isNotNull();
        return annotation.value();
    }

    private String preAuthorizeValue(Method method) {
        PreAuthorize annotation = AnnotatedElementUtils.findMergedAnnotation(
                method,
                PreAuthorize.class
        );
        assertThat(annotation).isNotNull();
        return annotation.value();
    }
}
