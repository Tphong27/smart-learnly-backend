package com.smartlearnly.backend.integration.classroom;

import static com.smartlearnly.backend.integration.IntegrationSecurity.asUser;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartlearnly.backend.integration.AbstractPostgresIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClassAnalyticsApiIT extends AbstractPostgresIntegrationTest {

    private static final UUID CLASS_ID = UUID.fromString("00000000-0000-0000-0000-000000000401");
    private static final UUID TRAINER_ID = UUID.fromString("00000000-0000-0000-0000-000000000402");

    @Test
    void an01_requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/trainer/classes/{classId}/analytics", CLASS_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void an04_rejectsInactiveDaysOutsideContractRange() throws Exception {
        mockMvc.perform(get("/api/v1/trainer/classes/{classId}/analytics", CLASS_ID)
                        .with(asUser(TRAINER_ID, "trainer@it.local", "TRAINER"))
                        .queryParam("inactiveDays", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void an05_rejectsKeywordLongerThanOneHundredCharacters() throws Exception {
        mockMvc.perform(get("/api/v1/admin/classes/{classId}/analytics", CLASS_ID)
                        .with(asUser(TRAINER_ID, "tmo@it.local", "TMO"))
                        .queryParam("keyword", "x".repeat(101)))
                .andExpect(status().isBadRequest());
    }
}
