package com.smartlearnly.backend.integration.catalog;

import com.smartlearnly.backend.integration.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.jdbc.Sql;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CourseCatalogApiIT extends AbstractPostgresIntegrationTest {

    @Test
    @Sql("/integration/catalog/cc-01-published-and-draft.sql")
    void cc01_returnsOnlyPublishedCourses() throws Exception {
        mockMvc.perform(get("/api/v1/courses")
                        .queryParam("page", "0")
                        .queryParam("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].slug").value("java-foundation"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void cc03_rejectsInvertedPriceRange() throws Exception {
        mockMvc.perform(get("/api/v1/courses")
                        .queryParam("minPrice", "500000")
                        .queryParam("maxPrice", "100000"))
                .andExpect(status().isBadRequest());
    }
}