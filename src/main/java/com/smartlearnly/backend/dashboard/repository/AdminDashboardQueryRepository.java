package com.smartlearnly.backend.dashboard.repository;

import com.smartlearnly.backend.dashboard.dto.DashboardUsersResponse;
import com.smartlearnly.backend.dashboard.query.DashboardQueryBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AdminDashboardQueryRepository {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final DashboardQueryBuilder queryBuilder;

    /** Account status snapshot — no date-range dependency. */
    public DashboardUsersResponse countUsers() {
        String sql = queryBuilder.buildUserStatsQuery();
        return jdbcTemplate.queryForObject(sql, new MapSqlParameterSource(), (rs, rowNum) -> new DashboardUsersResponse(
                rs.getLong("total"),
                rs.getLong("active"),
                rs.getLong("pending_verify"),
                rs.getLong("inactive"),
                rs.getLong("locked"),
                rs.getLong("banned")
        ));
    }

    /** Lightweight DB liveness check for System Health. */
    public boolean isDatabaseUp() {
        try {
            Integer one = jdbcTemplate.getJdbcTemplate().queryForObject("SELECT 1", Integer.class);
            return one != null && one == 1;
        } catch (Exception ignored) {
            return false;
        }
    }
}
