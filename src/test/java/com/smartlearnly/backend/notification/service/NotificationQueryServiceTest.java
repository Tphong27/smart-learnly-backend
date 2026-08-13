package com.smartlearnly.backend.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.common.api.PageResponse;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.notification.dto.NotificationResponse;
import com.smartlearnly.backend.notification.dto.UnreadCountResponse;
import com.smartlearnly.backend.notification.entity.Notification;
import com.smartlearnly.backend.notification.entity.NotificationType;
import com.smartlearnly.backend.notification.repository.NotificationRepository;
import com.smartlearnly.backend.user.entity.UserAccount;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class NotificationQueryServiceTest {

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private CurrentUserService currentUserService;

    private NotificationQueryService service;
    private UserAccount actor;

    @BeforeEach
    void setUp() {
        service = new NotificationQueryService(notificationRepository, currentUserService);
        actor = user();
    }

    @Test
    void listShouldUseDefaultFiltersAndReturnMappedPage() {
        Notification notification = notification();
        PageRequest pageRequest = PageRequest.of(2, 5);
        PageImpl<Notification> repositoryPage = new PageImpl<>(List.of(notification), pageRequest, 12);
        when(currentUserService.requireAuthenticatedUser()).thenReturn(actor);
        when(notificationRepository.findActiveForUserByStatusAndType(any(), any(), any(), any()))
                .thenReturn(repositoryPage);

        PageResponse<NotificationResponse> response = service.list(2, 5);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(notificationRepository).findActiveForUserByStatusAndType(
                eq(actor.getId()),
                eq("all"),
                eq(null),
                pageableCaptor.capture());

        assertThat(pageableCaptor.getValue()).isEqualTo(pageRequest);
        assertThat(response.page()).isEqualTo(2);
        assertThat(response.size()).isEqualTo(5);
        assertThat(response.totalItems()).isEqualTo(repositoryPage.getTotalElements());
        assertThat(response.totalPages()).isEqualTo(repositoryPage.getTotalPages());
        assertThat(response.items()).hasSize(1);

        NotificationResponse item = response.items().get(0);
        assertThat(item.id()).isEqualTo(notification.getId());
        assertThat(item.type()).isEqualTo(NotificationType.PAYMENT);
        assertThat(item.title()).isEqualTo("Payment received");
        assertThat(item.body()).isEqualTo("Your payment was confirmed.");
        assertThat(item.referenceType()).isEqualTo("ORDER");
        assertThat(item.referenceId()).isEqualTo(notification.getReferenceId());
        assertThat(item.actionUrl()).isEqualTo("/orders/" + notification.getReferenceId());
        assertThat(item.actorId()).isEqualTo(notification.getActorId());
        assertThat(item.eventKey()).isEqualTo("payment:confirmed");
        assertThat(item.payload()).containsEntry("invoice", "INV-100");
        assertThat(item.readAt()).isNull();
        assertThat(item.deliveredAt()).isEqualTo(notification.getDeliveredAt());
        assertThat(item.createdAt()).isEqualTo(notification.getCreatedAt());
    }

    @Test
    void listWithStatusShouldPreserveUnreadAndLeaveTypeNull() {
        when(currentUserService.requireAuthenticatedUser()).thenReturn(actor);
        when(notificationRepository.findActiveForUserByStatusAndType(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

        service.list(0, 10, "unread");

        verify(notificationRepository).findActiveForUserByStatusAndType(
                eq(actor.getId()),
                eq("unread"),
                eq(null),
                eq(PageRequest.of(0, 10)));
    }

    @ParameterizedTest
    @CsvSource(value = {
            "NULL, all",
            "'   ', all",
            "' READ ', read",
            "archived, all"
    }, nullValues = "NULL")
    void listShouldNormalizeStatus(String status, String expectedStatus) {
        when(currentUserService.requireAuthenticatedUser()).thenReturn(actor);
        when(notificationRepository.findActiveForUserByStatusAndType(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(1, 20), 0));

        service.list(1, 20, status, null);

        verify(notificationRepository).findActiveForUserByStatusAndType(
                eq(actor.getId()),
                eq(expectedStatus),
                eq(null),
                eq(PageRequest.of(1, 20)));
    }

    @ParameterizedTest
    @CsvSource(value = {
            "NULL, NULL",
            "'   ', NULL",
            "payment, PAYMENT",
            "ai-suggestion, AI_SUGGESTION",
            "made-up, NULL"
    }, nullValues = "NULL")
    void listShouldNormalizeType(String type, String expectedType) {
        when(currentUserService.requireAuthenticatedUser()).thenReturn(actor);
        when(notificationRepository.findActiveForUserByStatusAndType(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 15), 0));

        service.list(0, 15, "all", type);

        verify(notificationRepository).findActiveForUserByStatusAndType(
                eq(actor.getId()),
                eq("all"),
                eq(expectedType),
                eq(PageRequest.of(0, 15)));
    }

    @Test
    void unreadCountShouldReturnRepositoryCountForAuthenticatedUser() {
        when(currentUserService.requireAuthenticatedUser()).thenReturn(actor);
        when(notificationRepository.countByUserIdAndReadAtIsNullAndArchivedAtIsNull(actor.getId()))
                .thenReturn(7L);

        UnreadCountResponse response = service.unreadCount();

        verify(notificationRepository).countByUserIdAndReadAtIsNullAndArchivedAtIsNull(actor.getId());
        assertThat(response.unreadCount()).isEqualTo(7);
    }

    private UserAccount user() {
        UserAccount user = new UserAccount();
        user.setId(UUID.randomUUID());
        user.setEmail("learner@example.com");
        user.setFullName("Learner One");
        user.setRole("TRAINEE");
        user.setStatus("active");
        return user;
    }

    private Notification notification() {
        UUID referenceId = UUID.randomUUID();
        Instant deliveredAt = Instant.parse("2026-08-01T10:00:00Z");
        Instant createdAt = Instant.parse("2026-08-01T09:59:00Z");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("invoice", "INV-100");

        Notification notification = new Notification();
        notification.setId(UUID.randomUUID());
        notification.setUserId(actor.getId());
        notification.setType(NotificationType.PAYMENT);
        notification.setTitle("Payment received");
        notification.setBody("Your payment was confirmed.");
        notification.setReferenceType("ORDER");
        notification.setReferenceId(referenceId);
        notification.setActionUrl("/orders/" + referenceId);
        notification.setActorId(UUID.randomUUID());
        notification.setEventKey("payment:confirmed");
        notification.setPayload(payload);
        notification.setDeliveredAt(deliveredAt);
        notification.setCreatedAt(createdAt);
        notification.setUpdatedAt(deliveredAt);
        return notification;
    }
}
