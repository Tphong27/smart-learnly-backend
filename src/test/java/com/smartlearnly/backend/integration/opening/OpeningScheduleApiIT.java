package com.smartlearnly.backend.integration.opening;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.jdbc.Sql;
import com.smartlearnly.backend.integration.AbstractPostgresIntegrationTest;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OpeningScheduleApiIT extends AbstractPostgresIntegrationTest {

    private static final UUID CLASS_ID = UUID.fromString("00000000-0000-0000-0000-000000000201");

    @Test
    @Sql("/integration/opening/oc-05-capacity-and-enrollments.sql")
    void oc05_calculatesAvailableSlots() throws Exception {
        mockMvc.perform(get("/api/v1/opening-schedules/{id}", CLASS_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.availableSlots").value(13))
                .andExpect(jsonPath("$.data.classId").value(CLASS_ID.toString()));
    }

    @Test
    void oc06_rejectsInvertedPriceRange() throws Exception {
        mockMvc.perform(get("/api/v1/opening-schedules")
                        .queryParam("minPrice", "500000")
                        .queryParam("maxPrice", "100000"))
                .andExpect(status().isBadRequest());
    }
}
