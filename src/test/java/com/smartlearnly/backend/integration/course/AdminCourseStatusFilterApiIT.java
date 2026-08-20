package com.smartlearnly.backend.integration.course;

import static com.smartlearnly.backend.integration.IntegrationSecurity.asUser;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartlearnly.backend.integration.AbstractPostgresIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AdminCourseStatusFilterApiIT extends AbstractPostgresIntegrationTest {

    private static final UUID TMO_ID = UUID.fromString("00000000-0000-0000-0000-000000000701");
    private static final UUID CATEGORY_ID = UUID.fromString("00000000-0000-0000-0000-000000000702");

    @BeforeEach
    void seedCourses() {
        jdbcTemplate.update("""
                insert into public.users (id, email, full_name, role, status)
                values (?, 'course-tmo@it.local', 'Course TMO', 'TMO'::public.user_role,
                        'active'::public.user_status)
                """, TMO_ID);
        jdbcTemplate.update("""
                insert into public.categories (id, name, slug)
                values (?, 'Integration Category', 'integration-category')
                """, CATEGORY_ID);
        jdbcTemplate.update("""
                insert into public.courses (title, slug, category_id, creator_id, status)
                values
                    ('Draft Course', 'draft-course', ?, ?, 'draft'::public.course_status),
                    ('Published Course', 'published-course', ?, ?, 'published'::public.course_status),
                    ('Inactive Course', 'inactive-course', ?, ?, 'inactive'::public.course_status)
                """, CATEGORY_ID, TMO_ID, CATEGORY_ID, TMO_ID, CATEGORY_ID, TMO_ID);
    }

    @Test
    void listFiltersPostgresCourseEnumWithoutServerError() throws Exception {
        assertStatusFilter("draft", "draft-course");
        assertStatusFilter("published", "published-course");
        assertStatusFilter("inactive", "inactive-course");
    }

    private void assertStatusFilter(String courseStatus, String expectedSlug) throws Exception {
        mockMvc.perform(get("/api/v1/admin/courses")
                        .with(asUser(TMO_ID, "course-tmo@it.local", "TMO"))
                        .queryParam("status", courseStatus))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalItems").value(1))
                .andExpect(jsonPath("$.data.items[0].slug").value(expectedSlug))
                .andExpect(jsonPath("$.data.items[0].status").value(courseStatus));
    }
}
