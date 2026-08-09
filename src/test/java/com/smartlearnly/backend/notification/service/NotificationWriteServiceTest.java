package com.smartlearnly.backend.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.notification.dto.NotificationCreateCommand;
import com.smartlearnly.backend.notification.dto.NotificationResponse;
import com.smartlearnly.backend.notification.dto.UnreadCountResponse;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationWriteServiceTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private CurrentUserService currentUserService;
    @Mock private NotificationQueryService queryService;

    @InjectMocks private NotificationWriteService service;

    private UUID userId;
    private UUID notificationId;
    private UserAccount user;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        notificationId = UUID.randomUUID();
        user = userAccount(userId);
    }

    @Test
    void markRead_marksNotificationAsRead() {
        when(currentUserService.requireAuthenticatedUser()).thenReturn(user);
        Notification notification = notification(notificationId, userId);
        when(notificationRepository.findByIdAndUserIdAndArchivedAtIsNull(notificationId, userId))
                .thenReturn(Optional.of(notification));
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NotificationResponse response = service.markRead(notificationId);

        assertThat(response).isNotNull();
        assertThat(notification.getReadAt()).isNotNull();
        assertThat(notification.getSeenAt()).isNotNull();
        verify(notificationRepository).save(notification);
    }

    @Test
    void markRead_doesNotOverwriteExistingReadTimestamp() {
        when(currentUserService.requireAuthenticatedUser()).thenReturn(user);
        Notification notification = notification(notificationId, userId);
        notification.setReadAt(Instant.now().minusSeconds(60));
        notification.setSeenAt(Instant.now().minusSeconds(60));
        when(notificationRepository.findByIdAndUserIdAndArchivedAtIsNull(notificationId, userId))
                .thenReturn(Optional.of(notification));
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Instant originalReadAt = notification.getReadAt();
        Instant originalSeenAt = notification.getSeenAt();

        service.markRead(notificationId);

        assertThat(notification.getReadAt()).isEqualTo(originalReadAt);
        assertThat(notification.getSeenAt()).isEqualTo(originalSeenAt);
    }

    @Test
    void markRead_throwsWhenNotificationNotFound() {
        when(currentUserService.requireAuthenticatedUser()).thenReturn(user);
        when(notificationRepository.findByIdAndUserIdAndArchivedAtIsNull(notificationId, userId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markRead(notificationId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void markAllRead_callsRepositoryAndReturnsCount() {
        when(currentUserService.requireAuthenticatedUser()).thenReturn(user);
        UnreadCountResponse expectedCount = new UnreadCountResponse(0);
        when(queryService.unreadCount()).thenReturn(expectedCount);

        UnreadCountResponse response = service.markAllRead();

        assertThat(response).isEqualTo(expectedCount);
        verify(notificationRepository).markAllReadForUser(eq(userId), any(Instant.class));
    }

    @Test
    void recordClick_marksReadSeenAndClicked() {
        when(currentUserService.requireAuthenticatedUser()).thenReturn(user);
        Notification notification = notification(notificationId, userId);
        when(notificationRepository.findByIdAndUserIdAndArchivedAtIsNull(notificationId, userId))
                .thenReturn(Optional.of(notification));
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NotificationResponse response = service.recordClick(notificationId);

        assertThat(response).isNotNull();
        assertThat(notification.getReadAt()).isNotNull();
        assertThat(notification.getSeenAt()).isNotNull();
        assertThat(notification.getClickedAt()).isNotNull();
    }

    @Test
    void archive_marksNotificationAsArchived() {
        when(currentUserService.requireAuthenticatedUser()).thenReturn(user);
        Notification notification = notification(notificationId, userId);
        when(notificationRepository.findByIdAndUserIdAndArchivedAtIsNull(notificationId, userId))
                .thenReturn(Optional.of(notification));
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NotificationResponse response = service.archive(notificationId);

        assertThat(response).isNotNull();
        assertThat(notification.getArchivedAt()).isNotNull();
    }

    @Test
    void archiveAll_callsRepositoryAndReturnsUnreadCount() {
        when(currentUserService.requireAuthenticatedUser()).thenReturn(user);
        UnreadCountResponse expectedCount = new UnreadCountResponse(0);
        when(queryService.unreadCount()).thenReturn(expectedCount);

        UnreadCountResponse response = service.archiveAll();

        assertThat(response).isEqualTo(expectedCount);
        verify(notificationRepository).archiveAllForUser(eq(userId), any(Instant.class));
    }

    @Test
    void emit_createsNewNotification() {
        NotificationCreateCommand command = new NotificationCreateCommand(
                userId, NotificationType.SYSTEM, "Test Title", "Test Body",
                null, null, null, null, null, Map.of()
        );
        lenient().when(notificationRepository.existsByUserIdAndEventKey(userId, null)).thenReturn(false);
        when(notificationRepository.save(any())).thenAnswer(inv -> {
            Notification n = inv.getArgument(0);
            n.setId(UUID.randomUUID());
            return n;
        });

        Optional<NotificationResponse> response = service.emit(command);

        assertThat(response).isPresent();
        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    void emit_skipsDuplicateEventKey() {
        String eventKey = "duplicate-key";
        NotificationCreateCommand command = new NotificationCreateCommand(
                userId, NotificationType.SYSTEM, "Test Title", "Test Body",
                null, null, null, null, eventKey, null
        );
        when(notificationRepository.existsByUserIdAndEventKey(userId, eventKey)).thenReturn(true);

        Optional<NotificationResponse> response = service.emit(command);

        assertThat(response).isEmpty();
    }

    @Test
    void emit_throwsWhenTitleExceedsMaxLength() {
        String longTitle = "a".repeat(256);
        NotificationCreateCommand command = new NotificationCreateCommand(
                userId, NotificationType.SYSTEM, longTitle, "Body",
                null, null, null, null, null, null
        );
        lenient().when(notificationRepository.existsByUserIdAndEventKey(userId, null)).thenReturn(false);

        assertThatThrownBy(() -> service.emit(command))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    void emitAll_processesMultipleCommands() {
        NotificationCreateCommand cmd1 = new NotificationCreateCommand(
                userId, NotificationType.SYSTEM, "Title 1", "Body 1",
                null, null, null, null, null, null
        );
        NotificationCreateCommand cmd2 = new NotificationCreateCommand(
                userId, NotificationType.SYSTEM, "Title 2", "Body 2",
                null, null, null, null, null, null
        );

        lenient().when(notificationRepository.existsByUserIdAndEventKey(eq(userId), any())).thenReturn(false);
        when(notificationRepository.saveAll(any())).thenAnswer(inv -> {
            List<Notification> list = inv.getArgument(0);
            list.forEach(n -> n.setId(UUID.randomUUID()));
            return list;
        });

        List<NotificationResponse> responses = service.emitAll(List.of(cmd1, cmd2));

        assertThat(responses).hasSize(2);
        verify(notificationRepository).saveAll(any());
    }

    @Test
    void emitAll_deduplicatesWithinBatch() {
        String eventKey = "batch-dedup-key";
        NotificationCreateCommand cmd1 = new NotificationCreateCommand(
                userId, NotificationType.SYSTEM, "Title 1", "Body 1",
                null, null, null, null, eventKey, null
        );
        NotificationCreateCommand cmd2 = new NotificationCreateCommand(
                userId, NotificationType.SYSTEM, "Title 2", "Body 2",
                null, null, null, null, eventKey, null
        );

        when(notificationRepository.existsByUserIdAndEventKey(userId, eventKey)).thenReturn(false);
        when(notificationRepository.saveAll(any())).thenAnswer(inv -> {
            List<Notification> list = inv.getArgument(0);
            list.forEach(n -> n.setId(UUID.randomUUID()));
            return list;
        });

        List<NotificationResponse> responses = service.emitAll(List.of(cmd1, cmd2));

        assertThat(responses).hasSize(1);
    }

    @Test
    void emitAll_returnsEmptyListForNullInput() {
        List<NotificationResponse> responses = service.emitAll(null);

        assertThat(responses).isEmpty();
    }

    @Test
    void emitAll_returnsEmptyListForEmptyInput() {
        List<NotificationResponse> responses = service.emitAll(List.of());

        assertThat(responses).isEmpty();
    }

    @Test
    void cleanupReadOrArchivedCreatedBefore_deletesOldNotifications() {
        Instant cutoff = Instant.now();
        when(notificationRepository.deleteReadOrArchivedCreatedBefore(cutoff)).thenReturn(10);

        int deleted = service.cleanupReadOrArchivedCreatedBefore(cutoff);

        assertThat(deleted).isEqualTo(10);
        verify(notificationRepository).deleteReadOrArchivedCreatedBefore(cutoff);
    }

    @Test
    void cleanupReadOrArchivedCreatedBefore_throwsWhenCutoffIsNull() {
        assertThatThrownBy(() -> service.cleanupReadOrArchivedCreatedBefore(null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    // Helper methods

    private UserAccount userAccount(UUID id) {
        UserAccount user = new UserAccount();
        user.setId(id);
        return user;
    }

    private Notification notification(UUID id, UUID userId) {
        Notification n = new Notification();
        n.setId(id);
        n.setUserId(userId);
        n.setType(NotificationType.SYSTEM);
        n.setTitle("Test");
        n.setCreatedAt(Instant.now());
        return n;
    }
}
