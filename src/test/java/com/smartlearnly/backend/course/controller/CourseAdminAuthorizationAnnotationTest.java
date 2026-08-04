package com.smartlearnly.backend.course.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartlearnly.backend.course.access.controller.AdminCourseAccessController;
import com.smartlearnly.backend.course.authoring.controller.AdminCourseController;
import com.smartlearnly.backend.curriculum.admin.controller.AdminCourseLessonController;
import com.smartlearnly.backend.curriculum.admin.controller.AdminCourseSectionController;
import com.smartlearnly.backend.file.controller.AdminUploadController;
import com.smartlearnly.backend.course.category.controller.AdminCategoryController;
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
        assertThat(preAuthorizeValue(AdminCourseSectionController.class))
                .isEqualTo("hasAnyRole('ADMIN', 'TMO', 'SME', 'TRAINER')");
        assertThat(preAuthorizeValue(AdminCourseLessonController.class))
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
