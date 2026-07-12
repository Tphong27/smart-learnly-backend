package com.smartlearnly.backend.classroom.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class ClassAnalyticsRepository {

    @PersistenceContext
    private EntityManager entityManager;

    public List<StudentBaseRow> findActiveStudents(UUID classId) {
        String sql = """
                SELECT
                    ce.student_id,
                    u.full_name,
                    u.email,
                    ce.enrollment_date
                FROM public.class_enrollments ce
                JOIN public.users u
                    ON u.id = ce.student_id
                WHERE ce.class_id = :classId
                  AND ce.status = 'active'::public.enroll_status
                ORDER BY u.full_name ASC, u.id ASC
                """;

        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager
                .createNativeQuery(sql)
                .setParameter("classId", classId)
                .getResultList();

        return rows.stream()
                .map(row -> new StudentBaseRow(
                        toUuid(row[0]),
                        toStringValue(row[1]),
                        toStringValue(row[2]),
                        toInstant(row[3])))
                .toList();
    }

    public TestStatistics getTestStatistics(UUID classId) {
        String sql = """
                SELECT
                    AVG(ta.score) FILTER (
                        WHERE ta.score IS NOT NULL
                    )
                FROM public.tests t
                LEFT JOIN public.test_attempts ta
                    ON ta.test_id = t.id
                WHERE t.class_id = :classId
                  AND t.is_archived = FALSE
                """;

        Object value = entityManager
                .createNativeQuery(sql)
                .setParameter("classId", classId)
                .getSingleResult();

        return new TestStatistics(toBigDecimal(value));
    }

    public AssignmentStatistics getAssignmentStatistics(UUID classId, long activeStudentCount) {
        String sql = """
                SELECT
                    COUNT(DISTINCT a.id),

                    COUNT(s.id) FILTER (
                        WHERE
                            s.status IN (
                                'submitted'::public.submission_status,
                                'graded'::public.submission_status
                            )
                            OR s.is_late = TRUE
                    ),

                    AVG(s.score) FILTER (
                        WHERE s.score IS NOT NULL
                    ),

                    COUNT(s.id) FILTER (
                        WHERE s.is_late = TRUE
                    ),

                    COUNT(s.id) FILTER (
                        WHERE (
                            s.status =
                                'submitted'::public.submission_status
                            OR s.is_late = TRUE
                        )
                        AND s.graded_at IS NULL
                    )

                FROM public.assignments a

                LEFT JOIN public.assignment_submissions s
                    ON s.assignment_id = a.id

                WHERE a.class_id = :classId
                  AND a.is_archived = FALSE
                """;

        Object[] row = (Object[]) entityManager
                .createNativeQuery(sql)
                .setParameter("classId", classId)
                .getSingleResult();

        long totalAssignments = toLong(row[0]);
        long totalSubmitted = toLong(row[1]);

        long expectedSubmissions = totalAssignments * activeStudentCount;

        BigDecimal submissionRate = percentage(totalSubmitted, expectedSubmissions);

        return new AssignmentStatistics(
                totalAssignments,
                totalSubmitted,
                submissionRate,
                toBigDecimal(row[2]),
                toLong(row[3]),
                toLong(row[4]));
    }

    public List<StudentAssessmentRow> findStudentAssessmentStatistics(
            UUID classId) {
        String sql = """
                WITH active_students AS (
                    SELECT
                        ce.student_id
                    FROM public.class_enrollments ce
                    WHERE ce.class_id = :classId
                      AND ce.status =
                          'active'::public.enroll_status
                ),

                learning_activity AS (
                    SELECT
                        lp.student_id,
                        MAX(lp.last_accessed_at)
                            AS last_activity_at
                    FROM public.lesson_progress lp
                    WHERE lp.class_id = :classId
                    GROUP BY lp.student_id
                ),

                test_performance AS (
                    SELECT
                        ta.student_id,
                        AVG(ta.score) FILTER (
                            WHERE ta.score IS NOT NULL
                        ) AS average_test_score
                    FROM public.tests t
                    JOIN public.test_attempts ta
                        ON ta.test_id = t.id
                    WHERE t.class_id = :classId
                      AND t.is_archived = FALSE
                    GROUP BY ta.student_id
                ),

                assignment_performance AS (
                    SELECT
                        s.student_id,

                        AVG(s.score) FILTER (
                            WHERE s.score IS NOT NULL
                        ) AS average_assignment_score,

                        BOOL_OR(s.is_late)
                            AS has_late_submission

                    FROM public.assignments a

                    JOIN public.assignment_submissions s
                        ON s.assignment_id = a.id

                    WHERE a.class_id = :classId
                      AND a.is_archived = FALSE

                    GROUP BY s.student_id
                )

                SELECT
                    active.student_id,
                    activity.last_activity_at,
                    test.average_test_score,
                    assignment.average_assignment_score,
                    COALESCE(
                        assignment.has_late_submission,
                        FALSE
                    )

                FROM active_students active

                LEFT JOIN learning_activity activity
                    ON activity.student_id =
                        active.student_id

                LEFT JOIN test_performance test
                    ON test.student_id =
                        active.student_id

                LEFT JOIN assignment_performance assignment
                    ON assignment.student_id =
                        active.student_id
                """;

        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager
                .createNativeQuery(sql)
                .setParameter("classId", classId)
                .getResultList();

        return rows.stream()
                .map(row -> new StudentAssessmentRow(
                        toUuid(row[0]),
                        toInstant(row[1]),
                        toBigDecimal(row[2]),
                        toBigDecimal(row[3]),
                        Boolean.TRUE.equals(row[4])))
                .toList();
    }

    private BigDecimal percentage(
            long numerator,
            long denominator) {
        if (denominator <= 0) {
            return BigDecimal.ZERO;
        }

        return BigDecimal
                .valueOf(numerator)
                .multiply(BigDecimal.valueOf(100))
                .divide(
                        BigDecimal.valueOf(denominator),
                        2,
                        java.math.RoundingMode.HALF_UP);
    }

    private UUID toUuid(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof UUID uuid) {
            return uuid;
        }

        return UUID.fromString(value.toString());
    }

    private long toLong(Object value) {
        if (value == null) {
            return 0L;
        }

        return ((Number) value).longValue();
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof BigDecimal decimal) {
            return decimal;
        }

        return new BigDecimal(value.toString());
    }

    private Instant toInstant(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof Instant instant) {
            return instant;
        }

        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant();
        }

        return Instant.parse(value.toString());
    }

    private String toStringValue(Object value) {
        return value == null ? null : value.toString();
    }

    public record StudentBaseRow(
            UUID studentId,
            String studentName,
            String email,
            Instant enrollmentDate) {
    }

    public record StudentAssessmentRow(
            UUID studentId,
            Instant lastActivityAt,
            BigDecimal averageTestScore,
            BigDecimal averageAssignmentScore,
            boolean hasLateSubmission) {
    }

    public record TestStatistics(
            BigDecimal averageScore) {
    }

    public record AssignmentStatistics(
            long totalAssignments,
            long totalSubmitted,
            BigDecimal submissionRate,
            BigDecimal averageScore,
            long lateSubmissions,
            long pendingGrading) {
    }
}