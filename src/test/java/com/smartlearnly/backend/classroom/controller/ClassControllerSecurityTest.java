package com.smartlearnly.backend.classroom.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ClassControllerSecurityTest {

    @Autowired
    private ClassController classController;

    @Test
    @WithMockUser(username = "trainee@smartlearnly.dev", roles = "TRAINEE")
    void listAdminClassesShouldRejectTrainee() {
        assertThatThrownBy(() -> classController.listAdminClasses(
                null,
                null,
                null,
                null,
                0,
                20))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(username = "admin@smartlearnly.dev", roles = "ADMIN")
    void listTrainerClassesShouldRejectAdmin() {
        assertThatThrownBy(() -> classController.listMyAssignedClasses(
                null,
                null,
                0,
                20))
                .isInstanceOf(AccessDeniedException.class);
    }
}