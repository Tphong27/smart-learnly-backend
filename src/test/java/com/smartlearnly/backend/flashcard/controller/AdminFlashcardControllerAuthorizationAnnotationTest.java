package com.smartlearnly.backend.flashcard.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartlearnly.backend.flashcard.staging.controller.AdminFlashcardStagingController;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;

class AdminFlashcardControllerAuthorizationAnnotationTest {
    @Test
    void adminFlashcardCrudShouldAllowAdminSmeAndTrainerOnly() {
        PreAuthorize annotation = AnnotatedElementUtils.findMergedAnnotation(
                AdminFlashcardController.class,
                PreAuthorize.class
        );

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("hasAnyRole('ADMIN', 'SME', 'TRAINER')");
        assertThat(annotation.value()).doesNotContain("TMO");
    }

    @Test
    void adminFlashcardStagingShouldAllowAdminSmeAndTrainerOnly() {
        PreAuthorize annotation = AnnotatedElementUtils.findMergedAnnotation(
                AdminFlashcardStagingController.class,
                PreAuthorize.class
        );

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("hasAnyRole('ADMIN', 'SME', 'TRAINER')");
        assertThat(annotation.value()).doesNotContain("TMO");
    }
}
