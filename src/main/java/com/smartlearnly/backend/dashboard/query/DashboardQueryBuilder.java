package com.smartlearnly.backend.dashboard.query;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
    private static final String CURRICULUM_LESSONS_TABLE = "public.curriculum_lessons";
    private static final String CURRICULUM_VERSIONS_TABLE = "public.curriculum_versions";
    private static final String MODULES_TABLE = "public.modules";
    private static final String QUESTIONS_TABLE = "public.questions";

    /**
     * Builds the SQL query for account status snapshot (Information System dashboard).
     */
    public String buildUserStatsQuery() {
        return """
                SELECT
                    COUNT(*) FILTER (WHERE deleted_at IS NULL) AS total,
                    COUNT(*) FILTER (WHERE deleted_at IS NULL AND status = 'active') AS active,
                    COUNT(*) FILTER (WHERE deleted_at IS NULL AND status = 'pending_verify') AS pending_verify,
                    COUNT(*) FILTER (WHERE deleted_at IS NULL AND status = 'inactive') AS inactive,
                    COUNT(*) FILTER (
                        WHERE deleted_at IS NULL
                          AND locked_until IS NOT NULL
                          AND locked_until > NOW()
                    ) AS locked,
                    COUNT(*) FILTER (WHERE deleted_at IS NULL AND status = 'banned') AS banned
                FROM %s
                """.formatted(USERS_TABLE);
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
                WITH module_stats AS (
                    SELECT
                        COUNT(*) AS modules,
                        COUNT(*) FILTER (
                            WHERE module.created_at BETWEEN :from AND :to
                        ) AS new_modules_in_range
                    FROM %s module
                    JOIN %s course ON course.id = module.course_id
                    WHERE course.deleted_at IS NULL
                      AND module.is_system = false
                ),
                current_curriculum_versions AS (
                    SELECT DISTINCT ON (
                        curriculum_version.course_id,
                        curriculum_version.scope,
                        curriculum_version.class_id
                    )
                        curriculum_version.id,
                        curriculum_version.status
                    FROM %s curriculum_version
                    JOIN %s course ON course.id = curriculum_version.course_id
                    WHERE course.deleted_at IS NULL
                    ORDER BY
                        curriculum_version.course_id,
                        curriculum_version.scope,
                        curriculum_version.class_id,
                        curriculum_version.version_number DESC,
                        curriculum_version.created_at DESC
                ),
                lesson_stats AS (
                    SELECT
                        COUNT(*) AS lessons,
                        COUNT(*) FILTER (WHERE lesson.status = 'published') AS published_lessons,
                        COUNT(*) FILTER (WHERE lesson.status = 'draft') AS draft_lessons,
                        COUNT(*) FILTER (WHERE lesson.status = 'inactive') AS inactive_lessons,
                        COUNT(*) FILTER (
                            WHERE lesson.created_at BETWEEN :from AND :to
                    ) AS new_lessons_in_range
                    FROM %s lesson
                    JOIN current_curriculum_versions curriculum_version
                      ON curriculum_version.id = lesson.curriculum_version_id
                    WHERE curriculum_version.status <> 'archived'
                      AND lesson.deleted_at IS NULL
                )
                SELECT
                    module_stats.modules,
                    lesson_stats.lessons,
                    lesson_stats.published_lessons,
                    lesson_stats.draft_lessons,
                    lesson_stats.inactive_lessons,
                    module_stats.new_modules_in_range,
                    lesson_stats.new_lessons_in_range
                FROM module_stats
                CROSS JOIN lesson_stats
                """.formatted(
                        MODULES_TABLE,
                        COURSES_TABLE,
                        CURRICULUM_VERSIONS_TABLE,
                        COURSES_TABLE,
                        CURRICULUM_LESSONS_TABLE);
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
     * Builds parameters for time range queries.
     */
    public MapSqlParameterSource buildTimeRangeParams(Instant from, Instant to) {
        return new MapSqlParameterSource()
                .addValue("from", OffsetDateTime.ofInstant(from, ZoneOffset.UTC),
                        java.sql.Types.TIMESTAMP_WITH_TIMEZONE)
                .addValue("to", OffsetDateTime.ofInstant(to, ZoneOffset.UTC),
                        java.sql.Types.TIMESTAMP_WITH_TIMEZONE);
    }

}
