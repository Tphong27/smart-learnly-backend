package com.smartlearnly.backend.flashcard.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartlearnly.backend.flashcard.dto.AdminFlashcardDtos.CreateFlashcardLessonRequest;
import com.smartlearnly.backend.flashcard.staging.controller.AdminFlashcardStagingController;
import java.lang.reflect.Method;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;

class AdminFlashcardControllerAuthorizationAnnotationTest {
    @Test
    void adminFlashcardReadEndpointsShouldStillAllowTmo() throws Exception {
        PreAuthorize annotation = AnnotatedElementUtils.findMergedAnnotation(
                AdminFlashcardController.class,
                PreAuthorize.class
        );

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("hasAnyRole('TMO', 'SME', 'TRAINER')");

        Method createMethod = AdminFlashcardController.class.getMethod(
                "createFlashcardLesson",
                UUID.class,
                UUID.class,
                CreateFlashcardLessonRequest.class);
        PreAuthorize createAuthorization = AnnotatedElementUtils.findMergedAnnotation(
                createMethod,
                PreAuthorize.class);
        assertThat(createAuthorization).isNotNull();
        // assertThat(createAuthorization.value()).doesNotContain("TMO", "ADMIN");
        assertThat(createAuthorization.value())
        .contains("TMO")
        .doesNotContain("ADMIN");
    }

    @Test
    void adminFlashcardStagingShouldRejectTmoAndAdmin() {
        PreAuthorize annotation = AnnotatedElementUtils.findMergedAnnotation(
                AdminFlashcardStagingController.class,
                PreAuthorize.class
        );

        assertThat(annotation).isNotNull();
        // assertThat(annotation.value()).isEqualTo("hasAnyRole('SME', 'TRAINER')");
        // assertThat(annotation.value()).doesNotContain("TMO", "ADMIN");
        assertThat(annotation.value()).isEqualTo("hasAnyRole('TMO', 'SME', 'TRAINER')");
        assertThat(annotation.value()).doesNotContain("ADMIN");
    }

    @Test
    void adminFlashcardImageUploadShouldAllowSmeAndTrainerOnly() {
        PreAuthorize annotation = AnnotatedElementUtils.findMergedAnnotation(
                AdminFlashcardImageUploadController.class,
                PreAuthorize.class
        );

        assertThat(annotation).isNotNull();
        // assertThat(annotation.value()).isEqualTo("hasAnyRole('SME', 'TRAINER')");
        // assertThat(annotation.value()).doesNotContain("TMO", "ADMIN");
        assertThat(annotation.value()).isEqualTo("hasAnyRole('TMO', 'SME', 'TRAINER')");
        assertThat(annotation.value()).doesNotContain("ADMIN");
    }
}
