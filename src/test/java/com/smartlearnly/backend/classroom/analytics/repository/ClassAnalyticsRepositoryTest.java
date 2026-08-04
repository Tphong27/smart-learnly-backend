package com.smartlearnly.backend.classroom.analytics.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ClassAnalyticsRepositoryTest {

        private static final UUID CLASS_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
        private static final UUID STUDENT_ONE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
        private static final UUID STUDENT_TWO_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

        private static final Instant ENROLLED_AT = Instant.parse("2026-07-01T01:02:03Z");
        private static final Instant LAST_ACTIVITY_AT = Instant.parse("2026-07-20T12:00:00Z");

        @Mock
        private EntityManager entityManager;

        @Mock
        private Query query;

        private ClassAnalyticsRepository repository;

        @BeforeEach
        void setUp() {
                repository = new ClassAnalyticsRepository();
                ReflectionTestUtils.setField(repository, "entityManager", entityManager);
                when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        }

        // findActiveStudents(): UTCID restarts from UTCID01.

        @Test
        void UTCID01_findActiveStudents_mapsStringUuidTextAndTimestampColumns() {
                when(query.setParameter("classId", CLASS_ID)).thenReturn(query);
                when(query.getResultList()).thenReturn(
                                Collections.singletonList(new Object[] {
                                                STUDENT_ONE_ID.toString(),
                                                "Nguyen Duy Khiem",
                                                "khiem@example.com",
                                                Timestamp.from(ENROLLED_AT)
                                }));

                List<ClassAnalyticsRepository.StudentBaseRow> result = repository.findActiveStudents(CLASS_ID);

                assertThat(result).singleElement().satisfies(row -> {
                        assertThat(row.studentId()).isEqualTo(STUDENT_ONE_ID);
                        assertThat(row.studentName()).isEqualTo("Nguyen Duy Khiem");
                        assertThat(row.email()).isEqualTo("khiem@example.com");
                        assertThat(row.enrollmentDate()).isEqualTo(ENROLLED_AT);
                });
                verify(query).setParameter("classId", CLASS_ID);
        }

        @Test
        void UTCID02_findActiveStudents_mapsUuidInstantAndNullDatabaseColumns() {
                Instant secondEnrollmentDate = Instant.parse("2026-08-01T01:02:03Z");
                when(query.setParameter("classId", CLASS_ID)).thenReturn(query);
                when(query.getResultList()).thenReturn(List.of(
                                new Object[] {
                                                STUDENT_ONE_ID,
                                                null,
                                                null,
                                                secondEnrollmentDate
                                },
                                new Object[] {
                                                null,
                                                "Unknown Student",
                                                "unknown@example.com",
                                                null
                                }));

                List<ClassAnalyticsRepository.StudentBaseRow> result = repository.findActiveStudents(CLASS_ID);

                assertThat(result).hasSize(2);
                assertThat(result.get(0).studentId()).isEqualTo(STUDENT_ONE_ID);
                assertThat(result.get(0).studentName()).isNull();
                assertThat(result.get(0).email()).isNull();
                assertThat(result.get(0).enrollmentDate()).isEqualTo(secondEnrollmentDate);
                assertThat(result.get(1).studentId()).isNull();
                assertThat(result.get(1).studentName()).isEqualTo("Unknown Student");
                assertThat(result.get(1).email()).isEqualTo("unknown@example.com");
                assertThat(result.get(1).enrollmentDate()).isNull();
        }

        @Test
        void UTCID03_findActiveStudents_returnsEmptyListWhenNativeQueryReturnsNoRows() {
                when(query.setParameter("classId", CLASS_ID)).thenReturn(query);
                when(query.getResultList()).thenReturn(List.of());

                List<ClassAnalyticsRepository.StudentBaseRow> result = repository.findActiveStudents(CLASS_ID);

                assertThat(result).isEmpty();
        }

        // getAssignmentStatistics(): UTCID restarts from UTCID01.

        @Test
        void UTCID01_getAssignmentStatistics_calculatesThreeSubmittedOfFourExpectedAs7500Percent() {
                long activeStudentCount = 2L;
                when(query.setParameter("classId", CLASS_ID)).thenReturn(query);
                when(query.getSingleResult()).thenReturn(new Object[] {
                                2L,
                                3L,
                                new BigDecimal("8.50"),
                                1L,
                                2L
                });

                ClassAnalyticsRepository.AssignmentStatistics result = repository.getAssignmentStatistics(CLASS_ID,
                                activeStudentCount);

                assertThat(result.totalAssignments()).isEqualTo(2);
                assertThat(result.totalSubmitted()).isEqualTo(3);
                assertThat(result.submissionRate()).isEqualByComparingTo("75.00");
                assertThat(result.averageScore()).isEqualByComparingTo("8.50");
                assertThat(result.lateSubmissions()).isEqualTo(1);
                assertThat(result.pendingGrading()).isEqualTo(2);
        }

        @Test
        void UTCID02_getAssignmentStatistics_returnsZeroRateForZeroAssignmentsAndZeroStudents() {
                long activeStudentCount = 0L;
                when(query.setParameter("classId", CLASS_ID)).thenReturn(query);
                when(query.getSingleResult()).thenReturn(new Object[] {
                                0L,
                                0L,
                                null,
                                0L,
                                0L
                });

                ClassAnalyticsRepository.AssignmentStatistics result = repository.getAssignmentStatistics(CLASS_ID,
                                activeStudentCount);

                assertThat(result.totalAssignments()).isZero();
                assertThat(result.totalSubmitted()).isZero();
                assertThat(result.submissionRate()).isEqualByComparingTo(BigDecimal.ZERO);
                assertThat(result.averageScore()).isNull();
                assertThat(result.lateSubmissions()).isZero();
                assertThat(result.pendingGrading()).isZero();
        }

        @Test
        void UTCID03_getAssignmentStatistics_mapsIntegerAndStringValuesAndRoundsTwoOfThreeTo6667Percent() {
                long activeStudentCount = 3L;
                when(query.setParameter("classId", CLASS_ID)).thenReturn(query);
                when(query.getSingleResult()).thenReturn(new Object[] {
                                1,
                                2,
                                "7.25",
                                1,
                                0
                });

                ClassAnalyticsRepository.AssignmentStatistics result = repository.getAssignmentStatistics(CLASS_ID,
                                activeStudentCount);

                assertThat(result.totalAssignments()).isEqualTo(1);
                assertThat(result.totalSubmitted()).isEqualTo(2);
                assertThat(result.submissionRate()).isEqualByComparingTo("66.67");
                assertThat(result.averageScore()).isEqualByComparingTo("7.25");
                assertThat(result.lateSubmissions()).isEqualTo(1);
                assertThat(result.pendingGrading()).isZero();
        }

        // findStudentAssessmentStatistics(): UTCID restarts from UTCID01.

        @Test
        void UTCID01_findStudentAssessmentStatistics_mapsTwoRowsIncludingNullableScoresAndActivity() {
                when(query.setParameter("classId", CLASS_ID)).thenReturn(query);
                when(query.getResultList()).thenReturn(List.of(
                                new Object[] {
                                                STUDENT_ONE_ID,
                                                LAST_ACTIVITY_AT,
                                                new BigDecimal("9.25"),
                                                "8.50",
                                                true
                                },
                                new Object[] {
                                                STUDENT_TWO_ID.toString(),
                                                null,
                                                null,
                                                null,
                                                false
                                }));

                List<ClassAnalyticsRepository.StudentAssessmentRow> result = repository
                                .findStudentAssessmentStatistics(CLASS_ID);

                assertThat(result).hasSize(2);
                assertThat(result.get(0).studentId()).isEqualTo(STUDENT_ONE_ID);
                assertThat(result.get(0).lastActivityAt()).isEqualTo(LAST_ACTIVITY_AT);
                assertThat(result.get(0).averageTestScore()).isEqualByComparingTo("9.25");
                assertThat(result.get(0).averageAssignmentScore()).isEqualByComparingTo("8.50");
                assertThat(result.get(0).hasLateSubmission()).isTrue();
                assertThat(result.get(1).studentId()).isEqualTo(STUDENT_TWO_ID);
                assertThat(result.get(1).lastActivityAt()).isNull();
                assertThat(result.get(1).averageTestScore()).isNull();
                assertThat(result.get(1).averageAssignmentScore()).isNull();
                assertThat(result.get(1).hasLateSubmission()).isFalse();
        }

        @Test
        void UTCID02_findStudentAssessmentStatistics_returnsEmptyListWhenNativeQueryReturnsNoRows() {
                when(query.setParameter("classId", CLASS_ID)).thenReturn(query);
                when(query.getResultList()).thenReturn(List.of());

                List<ClassAnalyticsRepository.StudentAssessmentRow> result = repository
                                .findStudentAssessmentStatistics(CLASS_ID);

                assertThat(result).isEmpty();
        }
}
