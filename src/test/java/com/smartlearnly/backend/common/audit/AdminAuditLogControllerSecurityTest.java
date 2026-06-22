package com.smartlearnly.backend.common.audit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartlearnly.backend.common.api.PageResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminAuditLogControllerSecurityTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuditLogQueryService auditLogQueryService;

    @Test
    void listShouldRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/admin/audit-logs"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "TRAINEE")
    void listShouldRejectNonAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/admin/audit-logs"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "TMO")
    void detailShouldRejectNonAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/admin/audit-logs/" + java.util.UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }
    @Test
    @WithMockUser(roles = "ADMIN")
    void listShouldAllowAdmin() throws Exception {
        when(auditLogQueryService.list(
                isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(),
                isNull(), isNull(), anyInt(), anyInt()
        )).thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/api/v1/admin/audit-logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items").isEmpty());
    }
}
