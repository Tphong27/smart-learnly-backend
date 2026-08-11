package com.smartlearnly.backend.dashboard.repository;

import com.smartlearnly.backend.dashboard.dto.DashboardClassesResponse;
import com.smartlearnly.backend.dashboard.dto.DashboardContentResponse;
import com.smartlearnly.backend.dashboard.dto.DashboardCoursesResponse;
import com.smartlearnly.backend.dashboard.dto.DashboardQuestionsResponse;
import com.smartlearnly.backend.dashboard.dto.DashboardUsersResponse;
import com.smartlearnly.backend.dashboard.query.DashboardQueryBuilder;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AdminDashboardQueryRepository {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final DashboardQueryBuilder queryBuilder;

    /** Đếm user bằng schema chuẩn hiện tại, không truy vấn information_schema ở mỗi request. */
    public DashboardUsersResponse countUsers(Instant from, Instant to) {
        String sql = queryBuilder.buildUserStatsQuery();
        MapSqlParameterSource params = queryBuilder.buildTimeRangeParams(from, to);

        return jdbcTemplate.queryForObject(sql, params, (rs, rowNum) -> new DashboardUsersResponse(
                rs.getLong("total"),
                rs.getLong("active"),
                rs.getLong("pending_verify"),
                rs.getLong("inactive"),
                rs.getLong("banned"),
                rs.getLong("new_in_range")
        ));
    }

    public DashboardCoursesResponse countCourses(Instant from, Instant to) {
        String sql = queryBuilder.buildCourseStatsQuery();
        MapSqlParameterSource params = queryBuilder.buildTimeRangeParams(from, to);

        return jdbcTemplate.queryForObject(sql, params, (rs, rowNum) -> new DashboardCoursesResponse(
                rs.getLong("total"),
                rs.getLong("published"),
                rs.getLong("draft"),
                rs.getLong("inactive"),
                rs.getLong("new_in_range")
        ));
    }

    public DashboardClassesResponse countClasses(Instant from, Instant to) {
        String sql = queryBuilder.buildClassStatsQuery();
        MapSqlParameterSource params = queryBuilder.buildTimeRangeParams(from, to);

        return jdbcTemplate.queryForObject(sql, params, (rs, rowNum) -> new DashboardClassesResponse(
                rs.getLong("total"),
                rs.getLong("upcoming"),
                rs.getLong("ongoing"),
                rs.getLong("completed"),
                rs.getLong("cancelled"),
                rs.getLong("new_in_range")
        ));
    }

    public DashboardContentResponse countContent(Instant from, Instant to) {
        String sql = queryBuilder.buildContentStatsQuery();
        MapSqlParameterSource params = queryBuilder.buildTimeRangeParams(from, to);

        return jdbcTemplate.queryForObject(sql, params, (rs, rowNum) -> new DashboardContentResponse(
                rs.getLong("modules"),
                rs.getLong("lessons"),
                rs.getLong("published_lessons"),
                rs.getLong("draft_lessons"),
                rs.getLong("inactive_lessons"),
                rs.getLong("new_modules_in_range"),
                rs.getLong("new_lessons_in_range")
        ));
    }

    public DashboardQuestionsResponse countQuestions(Instant from, Instant to) {
        String sql = queryBuilder.buildQuestionStatsQuery();
        MapSqlParameterSource params = queryBuilder.buildTimeRangeParams(from, to);

        return jdbcTemplate.queryForObject(sql, params, (rs, rowNum) -> new DashboardQuestionsResponse(
                rs.getLong("total"),
                rs.getLong("approved"),
                rs.getLong("pending_review"),
                rs.getLong("draft"),
                rs.getLong("rejected"),
                rs.getLong("archived"),
                rs.getLong("new_in_range"),
                rs.getLong("reviewed_in_range"),
                rs.getLong("ai_generated"),
                rs.getLong("manual")
        ));
    }

}
