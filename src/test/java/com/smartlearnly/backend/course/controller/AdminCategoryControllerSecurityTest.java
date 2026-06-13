package com.smartlearnly.backend.course.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class AdminCategoryControllerSecurityTest {
    @Autowired
    private AdminCategoryController adminCategoryController;

    @Test
    @WithMockUser(username = "trainee@smartlearnly.dev", roles = "TRAINEE")
    void listShouldRejectNonAdminUser() {
        assertThatThrownBy(() -> adminCategoryController.list(null, null, null))
                .isInstanceOf(AccessDeniedException.class);
    }
}
