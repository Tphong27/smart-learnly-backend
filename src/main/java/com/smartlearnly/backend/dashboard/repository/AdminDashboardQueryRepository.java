package com.smartlearnly.backend.dashboard.repository;

import com.smartlearnly.backend.dashboard.dto.DashboardClassesResponse;
import com.smartlearnly.backend.dashboard.dto.DashboardContentResponse;
import com.smartlearnly.backend.dashboard.dto.DashboardCoursesResponse;
import com.smartlearnly.backend.dashboard.dto.DashboardQuestionBanksResponse;
import com.smartlearnly.backend.dashboard.dto.DashboardUsersResponse;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AdminDashboardQueryRepository {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public DashboardUsersResponse countUsers(Instant from, Instant to) {
        boolean hasStatus = hasColumn("users", "status");
        boolean hasDeletedAt = hasColumn("users", "deleted_at");
        boolean hasCreatedAt = hasColumn("users", "created_at");

        String liveCondition = hasDeletedAt ? "deleted_at IS NULL" : "TRUE";
        String activeCondition = hasStatus ? liveCondition + " AND status = 'active'" : "FALSE";
        String pendingCondition = hasStatus ? liveCondition + " AND status = 'pending_verify'" : "FALSE";
        String inactiveCondition = hasStatus ? liveCondition + " AND status = 'inactive'" : "FALSE";
        String bannedCondition = hasStatus ? liveCondition + " AND status = 'banned'" : "FALSE";
        String newCondition = hasCreatedAt ? liveCondition + " AND created_at BETWEEN :from AND :to" : "FALSE";

        String sql = """
                SELECT
                    COUNT(*) FILTER (WHERE %s) AS total,
                    COUNT(*) FILTER (WHERE %s) AS active,
                    COUNT(*) FILTER (WHERE %s) AS pending_verify,
                    COUNT(*) FILTER (WHERE %s) AS inactive,
                    COUNT(*) FILTER (WHERE %s) AS banned,
                    COUNT(*) FILTER (WHERE %s) AS new_in_range
                FROM public.users
                """.formatted(
                liveCondition,
                activeCondition,
                pendingCondition,
                inactiveCondition,
                bannedCondition,
                newCondition
        );
        return jdbcTemplate.queryForObject(sql, params(from, to), (rs, rowNum) -> new DashboardUsersResponse(
                rs.getLong("total"),
                rs.getLong("active"),
                rs.getLong("pending_verify"),
                rs.getLong("inactive"),
                rs.getLong("banned"),
                rs.getLong("new_in_range")
        ));
    }

    public DashboardCoursesResponse countCourses(Instant from, Instant to) {
        String sql = """
                SELECT
                    COUNT(*) FILTER (WHERE deleted_at IS NULL) AS total,
                    COUNT(*) FILTER (WHERE deleted_at IS NULL AND status = 'published') AS published,
                    COUNT(*) FILTER (WHERE deleted_at IS NULL AND status = 'draft') AS draft,
                    COUNT(*) FILTER (WHERE deleted_at IS NULL AND status = 'inactive') AS inactive,
                    COUNT(*) FILTER (WHERE deleted_at IS NULL AND created_at BETWEEN :from AND :to) AS new_in_range
                FROM public.courses
                """;
        return jdbcTemplate.queryForObject(sql, params(from, to), (rs, rowNum) -> new DashboardCoursesResponse(
                rs.getLong("total"),
                rs.getLong("published"),
                rs.getLong("draft"),
                rs.getLong("inactive"),
                rs.getLong("new_in_range")
        ));
    }

    public DashboardClassesResponse countClasses(Instant from, Instant to) {
        String sql = """
                SELECT
                    COUNT(*) FILTER (WHERE deleted_at IS NULL) AS total,
                    COUNT(*) FILTER (WHERE deleted_at IS NULL AND status = 'upcoming') AS upcoming,
                    COUNT(*) FILTER (WHERE deleted_at IS NULL AND status = 'ongoing') AS ongoing,
                    COUNT(*) FILTER (WHERE deleted_at IS NULL AND status = 'completed') AS completed,
                    COUNT(*) FILTER (WHERE deleted_at IS NULL AND status = 'cancelled') AS cancelled,
                    COUNT(*) FILTER (WHERE deleted_at IS NULL AND created_at BETWEEN :from AND :to) AS new_in_range
                FROM public.classes
                """;
        return jdbcTemplate.queryForObject(sql, params(from, to), (rs, rowNum) -> new DashboardClassesResponse(
                rs.getLong("total"),
                rs.getLong("upcoming"),
                rs.getLong("ongoing"),
                rs.getLong("completed"),
                rs.getLong("cancelled"),
                rs.getLong("new_in_range")
        ));
    }

    public DashboardContentResponse countContent(Instant from, Instant to) {
        String sql = """
                SELECT
                    (
                        SELECT COUNT(*)
                        FROM public.course_sections section
                        JOIN public.courses course ON course.id = section.course_id
                        WHERE course.deleted_at IS NULL
                    ) AS sections,
                    (
                        SELECT COUNT(*)
                        FROM public.course_sections section
                        JOIN public.courses course ON course.id = section.course_id
                        WHERE course.deleted_at IS NULL
                          AND section.created_at BETWEEN :from AND :to
                    ) AS new_sections_in_range,
                    COUNT(*) AS lessons,
                    COUNT(*) FILTER (WHERE lesson.status = 'published') AS published_lessons,
                    COUNT(*) FILTER (WHERE lesson.status = 'draft') AS draft_lessons,
                    COUNT(*) FILTER (WHERE lesson.status = 'inactive') AS inactive_lessons,
                    COUNT(*) FILTER (WHERE lesson.created_at BETWEEN :from AND :to) AS new_lessons_in_range
                FROM public.lessons lesson
                JOIN public.courses course ON course.id = lesson.course_id
                WHERE course.deleted_at IS NULL
                """;
        return jdbcTemplate.queryForObject(sql, params(from, to), (rs, rowNum) -> new DashboardContentResponse(
                rs.getLong("sections"),
                rs.getLong("lessons"),
                rs.getLong("published_lessons"),
                rs.getLong("draft_lessons"),
                rs.getLong("inactive_lessons"),
                rs.getLong("new_sections_in_range"),
                rs.getLong("new_lessons_in_range")
        ));
    }

    public DashboardQuestionBanksResponse countQuestionBanks(Instant from, Instant to) {
        String sql = """
                SELECT
                    (
                        SELECT COUNT(*)
                        FROM public.question_banks bank
                        JOIN public.courses course ON course.id = bank.course_id
                        WHERE course.deleted_at IS NULL
                    ) AS total,
                    (
                        SELECT COUNT(*)
                        FROM public.question_banks bank
                        JOIN public.courses course ON course.id = bank.course_id
                        WHERE course.deleted_at IS NULL AND bank.status = 'approved'
                    ) AS approved,
                    (
                        SELECT COUNT(*)
                        FROM public.question_banks bank
                        JOIN public.courses course ON course.id = bank.course_id
                        WHERE course.deleted_at IS NULL AND bank.status = 'draft'
                    ) AS draft,
                    (
                        SELECT COUNT(*)
                        FROM public.question_banks bank
                        JOIN public.courses course ON course.id = bank.course_id
                        WHERE course.deleted_at IS NULL AND bank.status = 'archived'
                    ) AS archived,
                    (
                        SELECT COUNT(*)
                        FROM public.question_banks bank
                        JOIN public.courses course ON course.id = bank.course_id
                        WHERE course.deleted_at IS NULL
                          AND bank.created_at BETWEEN :from AND :to
                    ) AS new_banks_in_range,
                    COUNT(*) AS questions,
                    COUNT(*) FILTER (WHERE question.status = 'approved') AS approved_questions,
                    COUNT(*) FILTER (WHERE question.status = 'pending_review') AS pending_review_questions,
                    COUNT(*) FILTER (WHERE question.status = 'draft') AS draft_questions,
                    COUNT(*) FILTER (WHERE question.status = 'rejected') AS rejected_questions,
                    COUNT(*) FILTER (WHERE question.status = 'archived') AS archived_questions,
                    COUNT(*) FILTER (WHERE question.created_at BETWEEN :from AND :to) AS new_questions_in_range,
                    COUNT(*) FILTER (WHERE question.reviewed_at BETWEEN :from AND :to) AS reviewed_questions_in_range,
                    COUNT(*) FILTER (WHERE question.is_ai_generated IS TRUE) AS ai_generated_questions,
                    COUNT(*) FILTER (WHERE question.is_ai_generated IS NOT TRUE) AS manual_questions
                FROM public.questions question
                JOIN public.courses course ON course.id = question.course_id
                WHERE course.deleted_at IS NULL
                """;
        return jdbcTemplate.queryForObject(sql, params(from, to), (rs, rowNum) -> new DashboardQuestionBanksResponse(
                rs.getLong("total"),
                rs.getLong("approved"),
                rs.getLong("draft"),
                rs.getLong("archived"),
                rs.getLong("questions"),
                rs.getLong("approved_questions"),
                rs.getLong("pending_review_questions"),
                rs.getLong("draft_questions"),
                rs.getLong("rejected_questions"),
                rs.getLong("archived_questions"),
                rs.getLong("new_banks_in_range"),
                rs.getLong("new_questions_in_range"),
                rs.getLong("reviewed_questions_in_range"),
                rs.getLong("ai_generated_questions"),
                rs.getLong("manual_questions")
        ));
    }

    private MapSqlParameterSource params(Instant from, Instant to) {
        return new MapSqlParameterSource()
                .addValue("from", OffsetDateTime.ofInstant(from, ZoneOffset.UTC), Types.TIMESTAMP_WITH_TIMEZONE)
                .addValue("to", OffsetDateTime.ofInstant(to, ZoneOffset.UTC), Types.TIMESTAMP_WITH_TIMEZONE);
    }

    private boolean hasColumn(String tableName, String columnName) {
        String sql = """
                SELECT EXISTS (
                    SELECT 1
                    FROM information_schema.columns
                    WHERE table_schema = 'public'
                      AND table_name = :tableName
                      AND column_name = :columnName
                )
                """;
        Boolean exists = jdbcTemplate.queryForObject(
                sql,
                Map.of("tableName", tableName, "columnName", columnName),
                Boolean.class
        );
        return Boolean.TRUE.equals(exists);
    }
}
