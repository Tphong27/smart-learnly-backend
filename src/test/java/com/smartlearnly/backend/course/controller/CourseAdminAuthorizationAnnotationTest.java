package com.smartlearnly.backend.course.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartlearnly.backend.file.controller.AdminUploadController;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;

class CourseAdminAuthorizationAnnotationTest {

    @Test
    void courseManagementShouldAllowAllCourseAuthoringRoles() {
        assertThat(preAuthorizeValue(AdminCourseController.class))
                .isEqualTo("hasAnyRole('ADMIN', 'TMO', 'SME', 'TRAINER')");
    }

    @Test
    void courseContentShouldAllowAllCourseAuthoringRoles() {
        assertThat(preAuthorizeValue(AdminCourseContentController.class))
                .isEqualTo("hasAnyRole('ADMIN', 'TMO', 'SME', 'TRAINER')");
    }

    @Test
    void courseThumbnailUploadShouldAllowAllCourseAuthoringRoles() {
        assertThat(preAuthorizeValue(AdminUploadController.class))
                .isEqualTo("hasAnyRole('ADMIN', 'TMO', 'SME', 'TRAINER')");
    }

    @Test
    void courseAccessBlockShouldRemainAdminAndTmoOnly() {
        assertThat(preAuthorizeValue(AdminCourseAccessController.class))
                .isEqualTo("hasAnyRole('ADMIN', 'TMO')");
    }

    @Test
    void categoryManagementShouldAllowAllCourseAuthoringRoles() {
        assertThat(preAuthorizeValue(AdminCategoryController.class))
                .isEqualTo("hasAnyRole('ADMIN', 'TMO', 'SME', 'TRAINER')");
    }

    private String preAuthorizeValue(Class<?> controllerClass) {
        PreAuthorize annotation = AnnotatedElementUtils.findMergedAnnotation(
                controllerClass,
                PreAuthorize.class
        );
        assertThat(annotation).isNotNull();
        return annotation.value();
    }
}
