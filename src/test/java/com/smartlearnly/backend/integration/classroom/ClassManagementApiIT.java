package com.smartlearnly.backend.integration.classroom;

import static com.smartlearnly.backend.integration.IntegrationSecurity.asUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartlearnly.backend.integration.AbstractPostgresIntegrationTest;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;

class ClassManagementApiIT extends AbstractPostgresIntegrationTest {

    private static final UUID ADMIN_ID = UUID.fromString("00000000-0000-0000-0000-000000000301");
    private static final UUID COURSE_ID = UUID.fromString("00000000-0000-0000-0000-000000000303");
    private static final UUID TRAINER_ID = UUID.fromString("00000000-0000-0000-0000-000000000302");

    @Test
    @Sql("/integration/classroom/cm-04-course-and-trainer.sql")
    void cm04_createPersistsClassAndFutureSessions() throws Exception {
        mockMvc.perform(post("/api/v1/admin/classes")
                        .with(asUser(ADMIN_ID, "admin@it.local", "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreatePayload()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.className").value("Java Evening K01"));

        assertThat(count("classes", "class_name = 'Java Evening K01'")).isEqualTo(1);
        assertThat(count("class_sessions", "class_id = (select id from public.classes where class_name = 'Java Evening K01')"))
                .isGreaterThan(0);
    }

    private String validCreatePayload() {
        return """
                {"courseId":"%s","className":"Java Evening K01","trainerId":"%s",
                 "meetingUrl":"https://meet.google.com/abc-defg-hij",
                 "scheduleDescription":"[{\\"dayOfWeek\\":\\"MONDAY\\",\\"slots\\":[{\\"startTime\\":\\"19:30\\",\\"endTime\\":\\"21:30\\"}]}]",
                 "startDate":"%s","endDate":"%s","maxStudents":20,"price":1500000.00}
                """.formatted(COURSE_ID, TRAINER_ID, LocalDate.now().plusDays(1), LocalDate.now().plusWeeks(4));
    }

    private Integer count(String table, String whereClause) {
        return jdbcTemplate.queryForObject("select count(*) from public." + table + " where " + whereClause, Integer.class);
    }
}
