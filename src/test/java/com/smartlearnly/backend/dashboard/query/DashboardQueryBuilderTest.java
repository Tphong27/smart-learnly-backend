package com.smartlearnly.backend.dashboard.query;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

class DashboardQueryBuilderTest {

    private final DashboardQueryBuilder builder = new DashboardQueryBuilder();

    @Test
    void buildUserStatsQuery_includesAllConditions() {
        String sql = builder.buildUserStatsQuery();

        assertThat(sql).contains("COUNT(*) FILTER (WHERE");
        assertThat(sql).contains("total");
        assertThat(sql).contains("active");
        assertThat(sql).contains("pending_verify");
        assertThat(sql).contains("inactive");
        assertThat(sql).contains("banned");
        assertThat(sql).contains("new_in_range");
        assertThat(sql).contains("public.users");
    }

    @Test
    void buildCourseStatsQuery_includesAllStatusFilters() {
        String sql = builder.buildCourseStatsQuery();

        assertThat(sql).contains("total");
        assertThat(sql).contains("published");
        assertThat(sql).contains("draft");
        assertThat(sql).contains("inactive");
        assertThat(sql).contains("new_in_range");
        assertThat(sql).contains("public.courses");
    }

    @Test
    void buildClassStatsQuery_includesAllStatusFilters() {
        String sql = builder.buildClassStatsQuery();

        assertThat(sql).contains("total");
        assertThat(sql).contains("upcoming");
        assertThat(sql).contains("ongoing");
        assertThat(sql).contains("completed");
        assertThat(sql).contains("cancelled");
        assertThat(sql).contains("public.classes");
    }

    @Test
    void buildContentStatsQuery_includesModulesAndLessons() {
        String sql = builder.buildContentStatsQuery();

        assertThat(sql).contains("WITH module_stats AS");
        assertThat(sql).contains("lesson_stats AS");
        assertThat(sql).contains("current_curriculum_versions AS");
        assertThat(sql).contains("public.curriculum_lessons");
        assertThat(sql).contains("public.curriculum_versions");
        assertThat(sql).contains("SELECT DISTINCT ON");
        assertThat(sql).contains("curriculum_version.scope");
        assertThat(sql).contains("curriculum_version.class_id");
        assertThat(sql).contains("curriculum_version.version_number DESC");
        assertThat(sql).contains("curriculum_version.status <> 'archived'");
        assertThat(sql).contains("lesson.deleted_at IS NULL");
        assertThat(sql).contains("modules");
        assertThat(sql).contains("lessons");
        assertThat(sql).contains("published_lessons");
        assertThat(sql).contains("draft_lessons");
        assertThat(sql).contains("inactive_lessons");
        assertThat(sql).contains("new_modules_in_range");
        assertThat(sql).contains("new_lessons_in_range");
    }

    @Test
    void buildQuestionStatsQuery_includesStatusAndSourceFilters() {
        String sql = builder.buildQuestionStatsQuery();

        assertThat(sql).contains("total");
        assertThat(sql).contains("approved");
        assertThat(sql).contains("pending_review");
        assertThat(sql).contains("draft");
        assertThat(sql).contains("rejected");
        assertThat(sql).contains("archived");
        assertThat(sql).contains("ai_generated");
        assertThat(sql).contains("manual");
    }

    @Test
    void buildTimeRangeParams_addsFromAndTo() {
        Instant from = Instant.parse("2024-01-01T00:00:00Z");
        Instant to = Instant.parse("2024-12-31T23:59:59Z");

        MapSqlParameterSource params = builder.buildTimeRangeParams(from, to);

        assertThat(params.getValue("from")).isNotNull();
        assertThat(params.getValue("to")).isNotNull();
    }

}
