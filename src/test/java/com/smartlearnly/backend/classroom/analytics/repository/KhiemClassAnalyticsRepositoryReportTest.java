package com.smartlearnly.backend.classroom.analytics.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class KhiemClassAnalyticsRepositoryReportTest {

        @Mock
        private EntityManager entityManager;

        @Mock
        private Query query;

        private ClassAnalyticsRepository repository;

        @BeforeEach
        void setUp() {
                repository = new ClassAnalyticsRepository();
                ReflectionTestUtils.setField(repository, "entityManager", entityManager);
                when(entityManager.createNativeQuery(org.mockito.ArgumentMatchers.anyString())).thenReturn(query);
        }

        @Test
        void UTCID_KHIEM_BE_490_findActiveStudents_mapsDatabaseRows() {
                UUID classId = UUID.randomUUID();
                UUID studentId = UUID.randomUUID();
                Instant enrolledAt = Instant.parse("2026-07-01T01:02:03Z");
                when(query.setParameter("classId", classId)).thenReturn(query);
                when(query.getResultList()).thenReturn(
                                java.util.Collections.singletonList(new Object[] {
                                                studentId.toString(),
                                                "Nguyen Duy Khiem",
                                                "khiem@example.com",
                                                Timestamp.from(enrolledAt)
                                }));

                List<ClassAnalyticsRepository.StudentBaseRow> result = repository.findActiveStudents(classId);

                assertThat(result).singleElement().satisfies(row -> {
                        assertThat(row.studentId()).isEqualTo(studentId);
                        assertThat(row.studentName()).isEqualTo("Nguyen Duy Khiem");
                        assertThat(row.email()).isEqualTo("khiem@example.com");
                        assertThat(row.enrollmentDate()).isEqualTo(enrolledAt);
                });
                verify(query).setParameter("classId", classId);
        }

        @Test
        void UTCID_KHIEM_BE_491_getAssignmentStatistics_calculatesSubmissionRate() {
                UUID classId = UUID.randomUUID();
                when(query.setParameter("classId", classId)).thenReturn(query);
                when(query.getSingleResult()).thenReturn(new Object[] {
                                2L, 3L, new BigDecimal("8.50"), 1L, 2L
                });

                ClassAnalyticsRepository.AssignmentStatistics result = repository.getAssignmentStatistics(classId, 2);

                assertThat(result.totalAssignments()).isEqualTo(2);
                assertThat(result.totalSubmitted()).isEqualTo(3);
                assertThat(result.submissionRate()).isEqualByComparingTo("75.00");
                assertThat(result.averageScore()).isEqualByComparingTo("8.50");
                assertThat(result.lateSubmissions()).isEqualTo(1);
                assertThat(result.pendingGrading()).isEqualTo(2);
        }

        @Test
        void UTCID_KHIEM_BE_492_getAssignmentStatistics_returnsZeroRateWithoutExpectedSubmissions() {
                UUID classId = UUID.randomUUID();
                when(query.setParameter("classId", classId)).thenReturn(query);
                when(query.getSingleResult()).thenReturn(new Object[] {
                                0L, 0L, null, 0L, 0L
                });

                ClassAnalyticsRepository.AssignmentStatistics result = repository.getAssignmentStatistics(classId, 0);

                assertThat(result.submissionRate()).isEqualByComparingTo(BigDecimal.ZERO);
                assertThat(result.averageScore()).isNull();
        }

        @Test
        void UTCID_KHIEM_BE_493_findStudentAssessmentStatistics_mapsNullableStatistics() {
                UUID classId = UUID.randomUUID();
                UUID firstStudent = UUID.randomUUID();
                UUID secondStudent = UUID.randomUUID();
                Instant activity = Instant.parse("2026-07-20T12:00:00Z");
                when(query.setParameter("classId", classId)).thenReturn(query);
                when(query.getResultList()).thenReturn(List.of(
                                new Object[] {
                                                firstStudent,
                                                activity,
                                                new BigDecimal("9.25"),
                                                "8.50",
                                                true
                                },
                                new Object[] {
                                                secondStudent.toString(),
                                                null,
                                                null,
                                                null,
                                                false
                                }));

                List<ClassAnalyticsRepository.StudentAssessmentRow> result = repository
                                .findStudentAssessmentStatistics(classId);

                assertThat(result).hasSize(2);
                assertThat(result.get(0).studentId()).isEqualTo(firstStudent);
                assertThat(result.get(0).lastActivityAt()).isEqualTo(activity);
                assertThat(result.get(0).averageTestScore()).isEqualByComparingTo("9.25");
                assertThat(result.get(0).averageAssignmentScore()).isEqualByComparingTo("8.50");
                assertThat(result.get(0).hasLateSubmission()).isTrue();
                assertThat(result.get(1).studentId()).isEqualTo(secondStudent);
                assertThat(result.get(1).lastActivityAt()).isNull();
                assertThat(result.get(1).hasLateSubmission()).isFalse();
        }
}
