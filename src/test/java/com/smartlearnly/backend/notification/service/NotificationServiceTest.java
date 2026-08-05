package com.smartlearnly.backend.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.common.api.PageResponse;
import com.smartlearnly.backend.notification.dto.NotificationCreateCommand;
import com.smartlearnly.backend.notification.dto.NotificationResponse;
import com.smartlearnly.backend.notification.dto.UnreadCountResponse;
import com.smartlearnly.backend.notification.entity.NotificationType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {
    @Mock
    private NotificationQueryService queryService;

    @Mock
    private NotificationWriteService writeService;

    private NotificationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationService(queryService, writeService);
    }

    @Test
    void listShouldDelegateToQueryService() {
        NotificationResponse expected = createNotificationResponse(NotificationType.PAYMENT);
        PageResponse<NotificationResponse> pageResponse = new PageResponse<>(
                List.of(expected), 0, 20, 1, 1);
        when(queryService.list("unread", "payment", 0, 20)).thenReturn(pageResponse);

        var response = service.list("unread", "payment", 0, 20);

        assertThat(response.items()).hasSize(1);
        verify(queryService).list("unread", "payment", 0, 20);
    }

    @Test
    void unreadCountShouldDelegateToQueryService() {
        UnreadCountResponse expected = new UnreadCountResponse(5L);
        when(queryService.unreadCount()).thenReturn(expected);

        var response = service.unreadCount();

        assertThat(response.unreadCount()).isEqualTo(5L);
        verify(queryService).unreadCount();
    }

    @Test
    void getShouldDelegateToQueryService() {
        UUID notificationId = UUID.randomUUID();
        NotificationResponse expected = createNotificationResponse(NotificationType.SYSTEM);
        when(queryService.get(notificationId)).thenReturn(expected);

        var response = service.get(notificationId);

        assertThat(response.id()).isNotNull();
        verify(queryService).get(notificationId);
    }

    @Test
    void markReadShouldDelegateToWriteService() {
        UUID notificationId = UUID.randomUUID();
        NotificationResponse expected = createNotificationResponseWithTimestamp(NotificationType.ASSIGNMENT);
        when(writeService.markRead(notificationId)).thenReturn(expected);

        NotificationResponse response = service.markRead(notificationId);

        assertThat(response.readAt()).isNotNull();
        verify(writeService).markRead(notificationId);
    }

    @Test
    void markAllReadShouldDelegateToWriteService() {
        UnreadCountResponse expected = new UnreadCountResponse(0L);
        when(writeService.markAllRead()).thenReturn(expected);

        var response = service.markAllRead();

        assertThat(response.unreadCount()).isZero();
        verify(writeService).markAllRead();
    }

    @Test
    void recordClickShouldDelegateToWriteService() {
        UUID notificationId = UUID.randomUUID();
        NotificationResponse expected = createNotificationResponseWithTimestamp(NotificationType.COURSE);
        when(writeService.recordClick(notificationId)).thenReturn(expected);

        NotificationResponse response = service.recordClick(notificationId);

        assertThat(response.clickedAt()).isNotNull();
        verify(writeService).recordClick(notificationId);
    }

    @Test
    void archiveShouldDelegateToWriteService() {
        UUID notificationId = UUID.randomUUID();
        NotificationResponse expected = createNotificationResponseWithArchivedAt(NotificationType.SYSTEM);
        when(writeService.archive(notificationId)).thenReturn(expected);

        NotificationResponse response = service.archive(notificationId);

        assertThat(response.archivedAt()).isNotNull();
        verify(writeService).archive(notificationId);
    }

    @Test
    void archiveAllShouldDelegateToWriteService() {
        var expected = new com.smartlearnly.backend.notification.dto.ArchivedCountResponse(4);
        when(writeService.archiveAll()).thenReturn(expected);

        var response = service.archiveAll();

        assertThat(response.archivedCount()).isEqualTo(4);
        verify(writeService).archiveAll();
    }

    @Test
    void emitShouldDelegateToWriteService() {
        UUID referenceId = UUID.randomUUID();
        NotificationCreateCommand command = new NotificationCreateCommand(
                UUID.randomUUID(),
                NotificationType.AI_SUGGESTION,
                "AI drafts ready",
                "Review generated questions.",
                "AI_QUESTION_BATCH",
                referenceId,
                "/admin/courses/" + referenceId,
                null,
                "ai:" + referenceId + ":ready",
                Map.of("status", "ready"));

        NotificationResponse expected = createNotificationResponse(NotificationType.AI_SUGGESTION);
        when(writeService.emit(command)).thenReturn(Optional.of(expected));

        var result = service.emit(command);

        assertThat(result).isPresent();
        verify(writeService).emit(command);
    }

    @Test
    void emitShouldReturnEmptyWhenWriteServiceReturnsEmpty() {
        NotificationCreateCommand command = new NotificationCreateCommand(
                UUID.randomUUID(),
                NotificationType.PAYMENT,
                "Payment received",
                null,
                "TRANSACTION",
                UUID.randomUUID(),
                null,
                null,
                "payment:tx-1:success",
                Map.of());

        when(writeService.emit(command)).thenReturn(Optional.empty());

        var result = service.emit(command);

        assertThat(result).isEmpty();
    }

    @Test
    void emitAllShouldDelegateToWriteService() {
        List<NotificationCreateCommand> commands = List.of(
                new NotificationCreateCommand(
                        UUID.randomUUID(),
                        NotificationType.CLASS,
                        "Class updated",
                        null,
                        "CLASS",
                        UUID.randomUUID(),
                        null,
                        null,
                        "class:1:updated",
                        Map.of()),
                new NotificationCreateCommand(
                        UUID.randomUUID(),
                        NotificationType.CLASS,
                        "Class restored",
                        null,
                        "CLASS",
                        UUID.randomUUID(),
                        null,
                        null,
                        "class:2:restored",
                        Map.of()));

        List<NotificationResponse> expected = List.of(
                createNotificationResponse(NotificationType.CLASS),
                createNotificationResponse(NotificationType.CLASS));
        when(writeService.emitAll(commands)).thenReturn(expected);

        var result = service.emitAll(commands);

        assertThat(result).hasSize(2);
        verify(writeService).emitAll(commands);
    }

    @Test
    void cleanupShouldDelegateToWriteService() {
        Instant cutoff = Instant.now().minusSeconds(3600);
        when(writeService.cleanupReadOrArchivedCreatedBefore(cutoff)).thenReturn(7);

        int result = service.cleanupReadOrArchivedCreatedBefore(cutoff);

        assertThat(result).isEqualTo(7);
        verify(writeService).cleanupReadOrArchivedCreatedBefore(cutoff);
    }

    private NotificationResponse createNotificationResponse(NotificationType type) {
        return new NotificationResponse(
                UUID.randomUUID(),
                type,
                "Title",
                "Body",
                null,
                null,
                null,
                null,
                null,
                Map.of(),
                null,
                Instant.now(),
                null,
                null,
                null,
                Instant.now());
    }

    private NotificationResponse createNotificationResponseWithTimestamp(NotificationType type) {
        Instant now = Instant.now();
        return new NotificationResponse(
                UUID.randomUUID(),
                type,
                "Title",
                "Body",
                null,
                null,
                null,
                null,
                null,
                Map.of(),
                now,
                now,
                now,
                now,
                null,
                now);
    }

    private NotificationResponse createNotificationResponseWithArchivedAt(NotificationType type) {
        Instant now = Instant.now();
        return new NotificationResponse(
                UUID.randomUUID(),
                type,
                "Title",
                "Body",
                null,
                null,
                null,
                null,
                null,
                Map.of(),
                now,
                now,
                now,
                null,
                now,
                now);
    }
}
