package com.smartlearnly.backend.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.notification.dto.NotificationCreateCommand;
import com.smartlearnly.backend.notification.dto.NotificationResponse;
import com.smartlearnly.backend.notification.entity.Notification;
import com.smartlearnly.backend.notification.entity.NotificationType;
import com.smartlearnly.backend.notification.repository.NotificationRepository;
import com.smartlearnly.backend.user.entity.UserAccount;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {
    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private CurrentUserService currentUserService;

    private NotificationService service;
    private UserAccount user;

    @BeforeEach
    void setUp() {
        service = new NotificationService(notificationRepository, currentUserService);
        user = new UserAccount();
        user.setId(UUID.randomUUID());
        user.setRole("TRAINEE");
    }

    @Test
    void listShouldReturnPagedNotificationsForCurrentUser() {
        Notification unread = sample(NotificationType.PAYMENT, null);
        when(currentUserService.requireAuthenticatedUser()).thenReturn(user);
        when(notificationRepository.findForUser(
                eq(user.getId()),
                eq(false),
                eq(true),
                eq(NotificationType.PAYMENT),
                any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(unread), PageRequest.of(0, 20), 1));

        var response = service.list("unread", "payment", 0, 20);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).id()).isEqualTo(unread.getId());
        assertThat(response.page()).isZero();
        assertThat(response.totalItems()).isEqualTo(1);
    }

    @Test
    void unreadCountShouldUseCurrentUser() {
        when(currentUserService.requireAuthenticatedUser()).thenReturn(user);
        when(notificationRepository.countByUserIdAndReadAtIsNull(user.getId())).thenReturn(3L);

        assertThat(service.unreadCount().unreadCount()).isEqualTo(3L);
    }

    @Test
    void markReadShouldOnlyLoadOwnedNotification() {
        Notification notification = sample(NotificationType.ASSIGNMENT, null);
        when(currentUserService.requireAuthenticatedUser()).thenReturn(user);
        when(notificationRepository.findByIdAndUserId(notification.getId(), user.getId()))
                .thenReturn(Optional.of(notification));
        when(notificationRepository.save(notification)).thenReturn(notification);

        NotificationResponse response = service.markRead(notification.getId());

        assertThat(response.readAt()).isNotNull();
        verify(notificationRepository).findByIdAndUserId(notification.getId(), user.getId());
    }

    @Test
    void markReadShouldRejectMissingOrUnownedNotification() {
        UUID notificationId = UUID.randomUUID();
        when(currentUserService.requireAuthenticatedUser()).thenReturn(user);
        when(notificationRepository.findByIdAndUserId(notificationId, user.getId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markRead(notificationId))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void markAllReadShouldUpdateOnlyCurrentUser() {
        when(currentUserService.requireAuthenticatedUser()).thenReturn(user);
        when(notificationRepository.countByUserIdAndReadAtIsNull(user.getId())).thenReturn(0L);

        var response = service.markAllRead();

        assertThat(response.unreadCount()).isZero();
        verify(notificationRepository).markAllReadForUser(eq(user.getId()), any(Instant.class));
    }

    @Test
    void emitShouldSaveNotification() {
        UUID referenceId = UUID.randomUUID();
        Notification saved = sample(NotificationType.AI_SUGGESTION, null);
        saved.setReferenceId(referenceId);
        when(notificationRepository.save(any(Notification.class))).thenReturn(saved);

        var result = service.emit(new NotificationCreateCommand(
                user.getId(),
                NotificationType.AI_SUGGESTION,
                "AI drafts ready",
                "Review generated questions.",
                "AI_QUESTION_BATCH",
                referenceId,
                "/admin/courses/" + referenceId,
                null,
                "ai:" + referenceId + ":ready",
                Map.of("status", "ready")));

        assertThat(result).isPresent();
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(user.getId());
        assertThat(captor.getValue().getType()).isEqualTo(NotificationType.AI_SUGGESTION);
        assertThat(captor.getValue().getPayload()).containsEntry("status", "ready");
    }

    @Test
    void emitShouldSkipDuplicateEventKey() {
        String eventKey = "payment:tx-1:success";
        when(notificationRepository.existsByUserIdAndEventKey(user.getId(), eventKey)).thenReturn(true);

        var result = service.emit(new NotificationCreateCommand(
                user.getId(),
                NotificationType.PAYMENT,
                "Payment received",
                null,
                "TRANSACTION",
                UUID.randomUUID(),
                null,
                null,
                eventKey,
                Map.of()));

        assertThat(result).isEmpty();
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void listShouldRejectInvalidStatus() {
        when(currentUserService.requireAuthenticatedUser()).thenReturn(user);

        assertThatThrownBy(() -> service.list("new", null, 0, 20))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    private Notification sample(NotificationType type, Instant readAt) {
        Notification notification = new Notification();
        notification.setId(UUID.randomUUID());
        notification.setUserId(user.getId());
        notification.setType(type);
        notification.setTitle("Title");
        notification.setBody("Body");
        notification.setPayload(Map.of("sample", true));
        notification.setReadAt(readAt);
        notification.setCreatedAt(Instant.now());
        notification.setUpdatedAt(Instant.now());
        return notification;
    }
}
