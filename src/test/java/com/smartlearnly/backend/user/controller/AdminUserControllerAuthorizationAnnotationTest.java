package com.smartlearnly.backend.user.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;

class AdminUserControllerAuthorizationAnnotationTest {
    @Test
    void adminUserLookupShouldAllowAdminAndTmoOnly() {
        PreAuthorize annotation = AnnotatedElementUtils.findMergedAnnotation(
                AdminUserController.class,
                PreAuthorize.class
        );

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("hasAnyRole('ADMIN', 'TMO')");
    }
}
