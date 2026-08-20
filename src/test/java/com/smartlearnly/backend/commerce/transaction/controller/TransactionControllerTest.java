package com.smartlearnly.backend.commerce.transaction.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartlearnly.backend.commerce.transaction.dto.TransactionFilterOptionsResponse;
import com.smartlearnly.backend.commerce.transaction.service.TransactionQueryService;
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
class TransactionControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TransactionQueryService transactionQueryService;

    @Test
    void filterOptionsShouldRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/transactions/filter-options"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "TRAINEE")
    void filterOptionsShouldReturnFilterDataForTrainee() throws Exception {
        when(transactionQueryService.getFilterOptions())
                .thenReturn(new TransactionFilterOptionsResponse(
                        List.of("PENDING", "SUCCESS"),
                        List.of("SEPAY"),
                        List.of("VND")));

        mockMvc.perform(get("/api/v1/transactions/filter-options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Transaction filter options loaded successfully"))
                .andExpect(jsonPath("$.data.statuses[0]").value("PENDING"))
                .andExpect(jsonPath("$.data.statuses[1]").value("SUCCESS"))
                .andExpect(jsonPath("$.data.paymentGateways[0]").value("SEPAY"))
                .andExpect(jsonPath("$.data.currencies[0]").value("VND"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void filterOptionsShouldRejectAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/transactions/filter-options"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "TMO")
    void filterOptionsShouldReturnFilterDataForTmo() throws Exception {
        when(transactionQueryService.getFilterOptions())
                .thenReturn(new TransactionFilterOptionsResponse(
                        List.of("PENDING", "SUCCESS"),
                        List.of("SEPAY"),
                        List.of("VND")));

        mockMvc.perform(get("/api/v1/transactions/filter-options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Transaction filter options loaded successfully"))
                .andExpect(jsonPath("$.data.statuses[0]").value("PENDING"))
                .andExpect(jsonPath("$.data.statuses[1]").value("SUCCESS"))
                .andExpect(jsonPath("$.data.paymentGateways[0]").value("SEPAY"))
                .andExpect(jsonPath("$.data.currencies[0]").value("VND"));
    }
}
