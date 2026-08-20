package com.smartlearnly.backend.curriculum.admin.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartlearnly.backend.curriculum.admin.service.CurriculumLessonAdminService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Regression guard for the SME lesson-access fix:
 * {@code SecurityConfig} must allow ADMIN/TMO/SME/TRAINER through the filter
 * chain on {@code GET /api/v1/admin/modules/{moduleId}/lessons}. Without the
 * {@code /api/v1/admin/modules/**} matcher, SME/TMO/TRAINER were rejected with
 * an empty-body 403 before ever reaching the controller's @PreAuthorize.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminCourseLessonControllerSecurityTest {
    private static final UUID MODULE_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CurriculumLessonAdminService curriculumLessonAdminService;

    @Test
    void listModuleLessonsShouldRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/admin/modules/{moduleId}/lessons", MODULE_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "TRAINEE")
    void listModuleLessonsShouldRejectTrainee() throws Exception {
        mockMvc.perform(get("/api/v1/admin/modules/{moduleId}/lessons", MODULE_ID))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "SME")
    void listModuleLessonsShouldAllowSme() throws Exception {
        when(curriculumLessonAdminService.listModuleLessons(any(UUID.class)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/admin/modules/{moduleId}/lessons", MODULE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @WithMockUser(roles = "TMO")
    void listModuleLessonsShouldAllowTmo() throws Exception {
        when(curriculumLessonAdminService.listModuleLessons(any(UUID.class)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/admin/modules/{moduleId}/lessons", MODULE_ID))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "TRAINER")
    void listModuleLessonsShouldAllowTrainer() throws Exception {
        when(curriculumLessonAdminService.listModuleLessons(any(UUID.class)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/admin/modules/{moduleId}/lessons", MODULE_ID))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void listModuleLessonsShouldRejectAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/admin/modules/{moduleId}/lessons", MODULE_ID))
                .andExpect(status().isForbidden());
    }
}
