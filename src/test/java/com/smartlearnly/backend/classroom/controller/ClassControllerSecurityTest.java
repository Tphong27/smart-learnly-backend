package com.smartlearnly.backend.classroom.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartlearnly.backend.classroom.admin.controller.AdminClassController;
import com.smartlearnly.backend.classroom.trainer.controller.TrainerClassController;
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
    private AdminClassController adminClassController;
    @Autowired
    private TrainerClassController trainerClassController;

    @Test
    @WithMockUser(username = "trainee@smartlearnly.dev", roles = "TRAINEE")
    void listAdminClassesShouldRejectTrainee() {
        assertThatThrownBy(() -> adminClassController.listAdminClasses(
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
        assertThatThrownBy(() -> trainerClassController.listMyAssignedClasses(
                null,
                null,
                null,
                0,
                20))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(username = "trainer@smartlearnly.dev", roles = "TRAINER")
    void generateMeetingUrlShouldRejectTrainer() {
        assertThatThrownBy(() -> adminClassController.generateMeetingUrl())
                .isInstanceOf(
                        AccessDeniedException.class);
    }
}
