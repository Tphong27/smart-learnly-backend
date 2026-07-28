package com.smartlearnly.backend.flashcard.personal.controller;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartlearnly.backend.common.api.PageResponse;
import com.smartlearnly.backend.flashcard.personal.service.PersonalFlashcardImageUploadService;
import com.smartlearnly.backend.flashcard.personal.service.PersonalFlashcardService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PersonalFlashcardControllerSecurityTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PersonalFlashcardService personalFlashcardService;

    @MockitoBean
    private PersonalFlashcardImageUploadService personalFlashcardImageUploadService;

    @Test
    void listShouldRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/my-flashcards/sets"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void listShouldRejectAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/my-flashcards/sets"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "TMO")
    void listShouldRejectTmo() throws Exception {
        mockMvc.perform(get("/api/v1/my-flashcards/sets"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "TRAINEE")
    void listShouldAllowEligiblePersonalRole() throws Exception {
        when(personalFlashcardService.listSets(isNull(), org.mockito.ArgumentMatchers.eq("updated_desc"), anyInt(), anyInt()))
                .thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/api/v1/my-flashcards/sets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items").isEmpty());
    }
}
