package com.smartlearnly.backend.common.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.user.entity.UserAccount;
import com.smartlearnly.backend.user.repository.UserRepository;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {
    @Mock
    private AuditLogRepository auditLogRepository;
    private final AuditDataSanitizer sanitizer = new AuditDataSanitizer();
    @Mock
    private UserRepository userRepository;

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void recordShouldSanitizePersistAndCaptureRequestContext() {
        AuditLogService service = new AuditLogService(auditLogRepository, sanitizer, userRepository);
        UUID actorId = UUID.randomUUID();
        Map<String, Object> metadata = Map.of("token", "secret");
        Map<String, Object> sanitized = Map.of("token", AuditDataSanitizer.REDACTED_VALUE);
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "203.0.113.10, 10.0.0.1");
        request.addHeader("User-Agent", "audit-test-agent");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        service.record(AuditEvent.userEvent(
                actorId, "admin@example.com", "ADMIN",
                AuditAction.COURSE_UPDATED, AuditDomain.COURSE, AuditResult.SUCCESS,
                "COURSE", UUID.randomUUID().toString(), "Course was updated",
                null, null, metadata
        ));

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        assertThat(captor.getValue().getMetadata()).isEqualTo(sanitized);
        assertThat(captor.getValue().getIpAddress()).isEqualTo("203.0.113.10");
        assertThat(captor.getValue().getUserAgent()).isEqualTo("audit-test-agent");
    }

    @Test
    void legacyBridgeShouldResolveActorAndUseTypedCatalog() {
        AuditLogService service = new AuditLogService(auditLogRepository, sanitizer, userRepository);
        UserAccount actor = new UserAccount();
        actor.setId(UUID.randomUUID());
        actor.setEmail("admin@example.com");
        actor.setRole("ADMIN");
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(actor.getEmail()))
                .thenReturn(Optional.of(actor));
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.record(actor.getEmail(), "SECTION_CREATED", "SECTION", UUID.randomUUID().toString());

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        assertThat(captor.getValue().getActorId()).isEqualTo(actor.getId());
        assertThat(captor.getValue().getAction()).isEqualTo(AuditAction.SECTION_CREATED);
        assertThat(captor.getValue().getDomain()).isEqualTo(AuditDomain.CONTENT);
    }
}
