package com.smartlearnly.backend.common.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.common.api.PageResponse;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

/**
 * Unit tests for {@link AuditLogQueryService} covering the most complex
 * function: the JPA {@link Specification} filter builder (keyword LIKE and
 * equality predicates) plus the detail lookup. Pure Mockito/JUnit tests, no
 * Spring context involved. Lenient stubbing is used because the shared Criteria
 * probe stubs predicates that are only exercised by some tests.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuditLogQueryServiceUnitTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @Test
    void listShouldBuildKeywordLikePredicatesForEverySearchableField() {
        AuditLogQueryService service = new AuditLogQueryService(auditLogRepository);
        when(auditLogRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sampleLog())));

        service.list("reconciled", null, null, null, null, null, null, null, null, null, 0, 20);

        CriteriaBuilder builder = specificationBuilder().invoke();
        verify(builder, times(4)).like(any(Expression.class), eq("%reconciled%"));
        verify(builder).or(any(Predicate[].class));
        verify(builder).and(any(Predicate[].class));
        verify(builder, never()).equal(any(Expression.class), any(Object.class));
    }

    @Test
    void listShouldBuildEqualityAndDateRangePredicates() {
        AuditLogQueryService service = new AuditLogQueryService(auditLogRepository);
        UUID actorId = UUID.randomUUID();
        Instant from = Instant.parse("2026-06-01T00:00:00Z");
        Instant to = Instant.parse("2026-06-30T00:00:00Z");
        when(auditLogRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sampleLog())));

        service.list(null, "payment", "PAYMENT_RECONCILED", "SUCCESS",
                actorId, "TRAINEE", "PAYMENT_TRANSACTION", "txn-1", from, to, 0, 20);

        CriteriaBuilder builder = specificationBuilder().invoke();
        // 6 ignore-case equality filters (domain/action/result/actorRole/targetType/targetId) + actorId.
        verify(builder, times(7)).equal(any(Expression.class), any(Object.class));
        verify(builder).greaterThanOrEqualTo(any(Expression.class), eq(from));
        verify(builder).lessThanOrEqualTo(any(Expression.class), eq(to));
        verify(builder).and(any(Predicate[].class));
        verify(builder, never()).or(any(Predicate[].class));
    }

    @Test
    void getShouldReturnDetailForExistingId() {
        AuditLogQueryService service = new AuditLogQueryService(auditLogRepository);
        AuditLog log = sampleLog();
        when(auditLogRepository.findById(log.getId())).thenReturn(Optional.of(log));

        AuditLogDetailResponse response = service.get(log.getId());

        assertThat(response.id()).isEqualTo(log.getId());
        assertThat(response.action()).isEqualTo("PAYMENT_RECONCILED");
        assertThat(response.summary()).isEqualTo("Payment reconciled");
        assertThat(response.correlationId()).isEqualTo("corr-1");
    }

    @Test
    void listShouldTrimKeywordBeforeBuildingPattern() {
        AuditLogQueryService service = new AuditLogQueryService(auditLogRepository);
        when(auditLogRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sampleLog())));

        service.list("  reconciled  ", null, null, null, null, null, null, null, null, null, 0, 20);

        CriteriaBuilder builder = specificationBuilder().invoke();
        verify(builder, times(4)).like(any(Expression.class), eq("%reconciled%"));
    }

    /**
     * Captures the Specification passed to the repository and prepares mocked
     * Criteria objects so the returned lambda can be invoked directly.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private SpecificationProbe specificationBuilder() {
        ArgumentCaptor<Specification<AuditLog>> specCaptor = ArgumentCaptor.forClass(Specification.class);
        verify(auditLogRepository).findAll(specCaptor.capture(), any(Pageable.class));
        Specification<AuditLog> specification = specCaptor.getValue();

        CriteriaBuilder builder = mock(CriteriaBuilder.class);
        CriteriaQuery query = mock(CriteriaQuery.class);
        Root root = mock(Root.class);
        Path path = mock(Path.class);
        Predicate predicate = mock(Predicate.class);

        when(root.get(anyString())).thenReturn(path);
        when(path.as(String.class)).thenReturn(path);
        when(builder.lower(any(Expression.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(builder.like(any(Expression.class), anyString())).thenReturn(predicate);
        when(builder.equal(any(Expression.class), any(Object.class))).thenReturn(predicate);
        when(builder.or(any(Predicate[].class))).thenReturn(predicate);
        when(builder.and(any(Predicate[].class))).thenReturn(predicate);
        when(builder.greaterThanOrEqualTo(any(Expression.class), any(Instant.class))).thenReturn(predicate);
        when(builder.lessThanOrEqualTo(any(Expression.class), any(Instant.class))).thenReturn(predicate);

        return new SpecificationProbe(builder, specification, root, query);
    }

    private record SpecificationProbe(CriteriaBuilder builder, Specification<AuditLog> specification,
            Root<AuditLog> root, CriteriaQuery<AuditLog> query) {
        CriteriaBuilder invoke() {
            specification.toPredicate(root, query, builder);
            return builder;
        }
    }

    private AuditLog sampleLog() {
        AuditLog log = new AuditLog();
        log.setId(UUID.randomUUID());
        log.setOccurredAt(Instant.parse("2026-06-22T10:00:00Z"));
        log.setActorType(AuditActorType.SYSTEM);
        log.setActorEmail("scheduler");
        log.setAction(AuditAction.PAYMENT_RECONCILED.name());
        log.setDomain(AuditDomain.PAYMENT.name());
        log.setResult(AuditResult.SUCCESS.name());
        log.setSummary("Payment reconciled");
        log.setCorrelationId("corr-1");
        return log;
    }
}
