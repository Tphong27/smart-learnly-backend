package com.smartlearnly.backend.common.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.common.api.PageResponse;
import com.smartlearnly.backend.common.exception.BusinessException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class AuditLogQueryServiceTest {
    @Mock
    private AuditLogRepository auditLogRepository;

    @Test
    void listShouldReturnStableNewestFirstPage() {
        AuditLogQueryService service = new AuditLogQueryService(auditLogRepository);
        AuditLog log = new AuditLog();
        log.setId(UUID.randomUUID());
        log.setOccurredAt(Instant.parse("2026-06-22T10:00:00Z"));
        log.setActorType(AuditActorType.SYSTEM);
        log.setActorEmail("scheduler");
        log.setAction(AuditAction.PAYMENT_RECONCILED);
        log.setDomain(AuditDomain.PAYMENT);
        log.setResult(AuditResult.SUCCESS);
        log.setSummary("Payment reconciled");
        when(auditLogRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(log)));

        PageResponse<AuditLogSummaryResponse> response = service.list(
                null, AuditDomain.PAYMENT, null, AuditResult.SUCCESS,
                null, null, null, null, null, null, 0, 20
        );

        assertThat(response.items()).hasSize(1);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(auditLogRepository).findAll(any(Specification.class), pageable.capture());
        assertThat(pageable.getValue().getSort().getOrderFor("occurredAt").isDescending()).isTrue();
        assertThat(pageable.getValue().getSort().getOrderFor("id").isDescending()).isTrue();
    }

    @Test
    void listShouldRejectInvalidOrExcessiveDateRange() {
        AuditLogQueryService service = new AuditLogQueryService(auditLogRepository);
        Instant now = Instant.parse("2026-06-22T00:00:00Z");

        assertThatThrownBy(() -> service.list(
                null, null, null, null, null, null, null, null,
                now, now.minus(1, ChronoUnit.DAYS), 0, 20
        )).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.list(
                null, null, null, null, null, null, null, null,
                now.minus(91, ChronoUnit.DAYS), now, 0, 20
        )).isInstanceOf(BusinessException.class);
        verify(auditLogRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void getShouldRejectUnknownId() {
        AuditLogQueryService service = new AuditLogQueryService(auditLogRepository);
        UUID id = UUID.randomUUID();
        when(auditLogRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(id))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Audit log was not found");
    }
}
