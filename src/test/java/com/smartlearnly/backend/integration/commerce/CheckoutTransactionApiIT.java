package com.smartlearnly.backend.integration.commerce;

import static com.smartlearnly.backend.integration.IntegrationSecurity.asUser;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartlearnly.backend.integration.AbstractPostgresIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class CheckoutTransactionApiIT extends AbstractPostgresIntegrationTest {

    private static final UUID TRAINEE_ID = UUID.fromString("00000000-0000-0000-0000-000000000601");

    @Test
    void py01_requiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/orders/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemType\":\"COURSE\",\"courseId\":\"00000000-0000-0000-0000-000000000602\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void py04_rejectsPayloadWithoutRequiredCourseIdBeforeCreatingRecords() throws Exception {
        mockMvc.perform(post("/api/v1/orders/checkout")
                        .with(asUser(TRAINEE_ID, "trainee@it.local", "TRAINEE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemType\":\"COURSE\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void py05_rejectsUnknownItemTypeBeforeCreatingRecords() throws Exception {
        mockMvc.perform(post("/api/v1/orders/checkout")
                        .with(asUser(TRAINEE_ID, "trainee@it.local", "TRAINEE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemType\":\"BUNDLE\",\"courseId\":\"00000000-0000-0000-0000-000000000602\"}"))
                .andExpect(status().isBadRequest());
    }
}
