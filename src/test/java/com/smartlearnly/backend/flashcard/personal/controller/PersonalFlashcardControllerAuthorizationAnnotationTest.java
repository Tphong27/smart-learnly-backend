package com.smartlearnly.backend.flashcard.personal.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class PersonalFlashcardControllerAuthorizationAnnotationTest {
    @Test
    void controllerShouldAllowOnlyPersonalFlashcardRoles() {
        PreAuthorize annotation = PersonalFlashcardController.class.getAnnotation(PreAuthorize.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("hasAnyRole('TRAINEE', 'TRAINER', 'SME')");
    }
}
