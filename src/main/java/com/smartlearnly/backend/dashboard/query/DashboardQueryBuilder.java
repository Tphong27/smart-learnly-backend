package com.smartlearnly.backend.dashboard.query;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Component;

/**
 * Builds SQL queries and parameters for dashboard statistics.
 */
@Component
public class DashboardQueryBuilder {

    private static final String USERS_TABLE = "public.users";
    private static final String COURSES_TABLE = "public.courses";
    private static final String CLASSES_TABLE = "public.classes";
    private static final String LESSONS_TABLE = "public.lessons";
    private static final String MODULES_TABLE = "public.modules";
    private static final String QUESTIONS_TABLE = "public.questions";

    /**
     * Builds the SQL query for user statistics.
     */
    public String buildUserStatsQuery(boolean hasStatus, boolean hasDeletedAt, boolean hasCreatedAt) {
        String liveCondition = hasDeletedAt ? "deleted_at IS NULL" : "TRUE";
        String activeCondition = hasStatus ? liveCondition + " AND status = 'active'" : "FALSE";
        String pendingCondition = hasStatus ? liveCondition + " AND status = 'pending_verify'" : "FALSE";
        String inactiveCondition = hasStatus ? liveCondition + " AND status = 'inactive'" : "FALSE";
        String bannedCondition = hasStatus ? liveCondition + " AND status = 'banned'" : "FALSE";
        String newCondition = hasCreatedAt ? liveCondition + " AND created_at BETWEEN :from AND :to" : "FALSE";

        return """
                SELECT
                    COUNT(*) FILTER (WHERE %s) AS total,
                    COUNT(*) FILTER (WHERE %s) AS active,
                    COUNT(*) FILTER (WHERE %s) AS pending_verify,
                    COUNT(*) FILTER (WHERE %s) AS inactive,
                    COUNT(*) FILTER (WHERE %s) AS banned,
                    COUNT(*) FILTER (WHERE %s) AS new_in_range
                FROM %s
                """.formatted(
                        liveCondition,
                        activeCondition,
                        pendingCondition,
                        inactiveCondition,
                        bannedCondition,
                        newCondition,
                        USERS_TABLE);
    }

    /**
     * Builds the SQL query for course statistics.
     */
    public String buildCourseStatsQuery() {
        return """
                SELECT
                    COUNT(*) FILTER (WHERE deleted_at IS NULL) AS total,
                    COUNT(*) FILTER (WHERE deleted_at IS NULL AND status = 'published') AS published,
                    COUNT(*) FILTER (WHERE deleted_at IS NULL AND status = 'draft') AS draft,
                    COUNT(*) FILTER (WHERE deleted_at IS NULL AND status = 'inactive') AS inactive,
                    COUNT(*) FILTER (WHERE deleted_at IS NULL AND created_at BETWEEN :from AND :to) AS new_in_range
                FROM %s
                """.formatted(COURSES_TABLE);
    }

    /**
     * Builds the SQL query for class statistics.
     */
    public String buildClassStatsQuery() {
        return """
                SELECT
                    COUNT(*) FILTER (WHERE deleted_at IS NULL) AS total,
                    COUNT(*) FILTER (WHERE deleted_at IS NULL AND status = 'upcoming') AS upcoming,
                    COUNT(*) FILTER (WHERE deleted_at IS NULL AND status = 'ongoing') AS ongoing,
                    COUNT(*) FILTER (WHERE deleted_at IS NULL AND status = 'completed') AS completed,
                    COUNT(*) FILTER (WHERE deleted_at IS NULL AND status = 'cancelled') AS cancelled,
                    COUNT(*) FILTER (WHERE deleted_at IS NULL AND created_at BETWEEN :from AND :to) AS new_in_range
                FROM %s
                """.formatted(CLASSES_TABLE);
    }

    /**
     * Builds the SQL query for content (modules and lessons) statistics.
     */
    public String buildContentStatsQuery() {
        return """
                SELECT
                    (
                        SELECT COUNT(*)
                        FROM %s module
                        JOIN %s course ON course.id = module.course_id
                        WHERE course.deleted_at IS NULL
                          AND module.is_system = false
                    ) AS modules,
                    (
                        SELECT COUNT(*)
                        FROM %s module
                        JOIN %s course ON course.id = module.course_id
                        WHERE course.deleted_at IS NULL
                          AND module.is_system = false
                          AND module.created_at BETWEEN :from AND :to
                    ) AS new_modules_in_range,
                    COUNT(*) AS lessons,
                    COUNT(*) FILTER (WHERE lesson.status = 'published') AS published_lessons,
                    COUNT(*) FILTER (WHERE lesson.status = 'draft') AS draft_lessons,
                    COUNT(*) FILTER (WHERE lesson.status = 'inactive') AS inactive_lessons,
                    COUNT(*) FILTER (WHERE lesson.created_at BETWEEN :from AND :to) AS new_lessons_in_range
                FROM %s lesson
                JOIN %s course ON course.id = lesson.course_id
                WHERE course.deleted_at IS NULL
                """.formatted(MODULES_TABLE, COURSES_TABLE, MODULES_TABLE, COURSES_TABLE, LESSONS_TABLE, COURSES_TABLE);
    }

    /**
     * Builds the SQL query for question statistics.
     */
    public String buildQuestionStatsQuery() {
        return """
                SELECT
                    COUNT(*) AS total,
                    COUNT(*) FILTER (WHERE question.status = 'approved') AS approved,
                    COUNT(*) FILTER (WHERE question.status = 'pending_review') AS pending_review,
                    COUNT(*) FILTER (WHERE question.status = 'draft') AS draft,
                    COUNT(*) FILTER (WHERE question.status = 'rejected') AS rejected,
                    COUNT(*) FILTER (WHERE question.status = 'archived') AS archived,
                    COUNT(*) FILTER (WHERE question.created_at BETWEEN :from AND :to) AS new_in_range,
                    COUNT(*) FILTER (WHERE question.reviewed_at BETWEEN :from AND :to) AS reviewed_in_range,
                    COUNT(*) FILTER (WHERE question.is_ai_generated IS TRUE) AS ai_generated,
                    COUNT(*) FILTER (WHERE question.is_ai_generated IS NOT TRUE) AS manual
                FROM %s question
                JOIN %s course ON course.id = question.course_id
                WHERE course.deleted_at IS NULL
                """.formatted(QUESTIONS_TABLE, COURSES_TABLE);
    }

    /**
     * Builds SQL to check if a column exists.
     */
    public String buildColumnExistsQuery(String tableName, String columnName) {
        return """
                SELECT EXISTS (
                    SELECT 1
                    FROM information_schema.columns
                    WHERE table_schema = 'public'
                      AND table_name = :tableName
                      AND column_name = :columnName
                )
                """;
    }

    /**
     * Builds parameters for time range queries.
     */
    public MapSqlParameterSource buildTimeRangeParams(Instant from, Instant to) {
        return new MapSqlParameterSource()
                .addValue("from", OffsetDateTime.ofInstant(from, ZoneOffset.UTC),
                        java.sql.Types.TIMESTAMP_WITH_TIMEZONE)
                .addValue("to", OffsetDateTime.ofInstant(to, ZoneOffset.UTC),
                        java.sql.Types.TIMESTAMP_WITH_TIMEZONE);
    }

    /**
     * Builds parameters for column existence check.
     */
    public Map<String, String> buildColumnCheckParams(String tableName, String columnName) {
        return Map.of("tableName", tableName, "columnName", columnName);
    }
}
