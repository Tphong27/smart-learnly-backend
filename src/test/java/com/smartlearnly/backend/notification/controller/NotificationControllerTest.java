package com.smartlearnly.backend.notification.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.common.api.PageResponse;
import com.smartlearnly.backend.notification.dto.ArchivedCountResponse;
import com.smartlearnly.backend.notification.dto.NotificationResponse;
import com.smartlearnly.backend.notification.dto.UnreadCountResponse;
import com.smartlearnly.backend.notification.entity.NotificationType;
import com.smartlearnly.backend.notification.service.NotificationService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.prepost.PreAuthorize;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {
    @Mock
    private NotificationService notificationService;

    private NotificationController controller;
    private UUID notificationId;

    @BeforeEach
    void setUp() {
        controller = new NotificationController(notificationService);
        notificationId = UUID.randomUUID();
    }

    @Test
    void controllerShouldRequireAuthentication() {
        PreAuthorize annotation = NotificationController.class.getAnnotation(PreAuthorize.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("isAuthenticated()");
    }

    @Test
    void listShouldDeclarePaginationValidation() throws Exception {
        Method method = NotificationController.class.getMethod(
                "list", int.class, int.class, String.class, String.class);

        Min pageMin = method.getParameters()[0].getAnnotation(Min.class);
        Min sizeMin = method.getParameters()[1].getAnnotation(Min.class);
        Max sizeMax = method.getParameters()[1].getAnnotation(Max.class);

        assertThat(pageMin.value()).isZero();
        assertThat(sizeMin.value()).isEqualTo(1);
        assertThat(sizeMax.value()).isEqualTo(100);
    }

    @Test
    void listShouldReturnApiResponse() {
        PageResponse<NotificationResponse> page = new PageResponse<>(List.of(response()), 0, 20, 1, 1);
        when(notificationService.list("unread", "payment", 0, 20)).thenReturn(page);

        var response = controller.list(0, 20, "unread", "payment");

        assertThat(response.success()).isTrue();
        assertThat(response.message()).isEqualTo("Notifications loaded successfully");
        assertThat(response.data()).isSameAs(page);
    }

    @Test
    void unreadCountShouldReturnApiResponse() {
        UnreadCountResponse count = new UnreadCountResponse(2);
        when(notificationService.unreadCount()).thenReturn(count);

        var response = controller.unreadCount();

        assertThat(response.success()).isTrue();
        assertThat(response.data()).isSameAs(count);
    }

    @Test
    void getShouldReturnApiResponse() {
        NotificationResponse notification = response();
        when(notificationService.get(notificationId)).thenReturn(notification);

        var response = controller.get(notificationId);

        assertThat(response.success()).isTrue();
        assertThat(response.data()).isSameAs(notification);
    }

    @Test
    void markReadShouldReturnApiResponse() {
        NotificationResponse notification = response();
        when(notificationService.markRead(notificationId)).thenReturn(notification);

        var response = controller.markRead(notificationId);

        assertThat(response.success()).isTrue();
        assertThat(response.data()).isSameAs(notification);
    }

    @Test
    void recordClickShouldReturnApiResponse() {
        NotificationResponse notification = response();
        when(notificationService.recordClick(notificationId)).thenReturn(notification);

        var response = controller.recordClick(notificationId);

        assertThat(response.success()).isTrue();
        assertThat(response.message()).isEqualTo("Notification click recorded");
        assertThat(response.data()).isSameAs(notification);
    }

    @Test
    void archiveShouldReturnApiResponse() {
        NotificationResponse notification = response();
        when(notificationService.archive(notificationId)).thenReturn(notification);

        var response = controller.archive(notificationId);

        assertThat(response.success()).isTrue();
        assertThat(response.message()).isEqualTo("Notification archived");
        assertThat(response.data()).isSameAs(notification);
    }

    @Test
    void markAllReadShouldReturnApiResponse() {
        UnreadCountResponse count = new UnreadCountResponse(0);
        when(notificationService.markAllRead()).thenReturn(count);

        var response = controller.markAllRead();

        assertThat(response.success()).isTrue();
        assertThat(response.data()).isSameAs(count);
    }

    @Test
    void archiveAllShouldReturnApiResponse() {
        ArchivedCountResponse count = new ArchivedCountResponse(3);
        when(notificationService.archiveAll()).thenReturn(count);

        var response = controller.archiveAll();

        assertThat(response.success()).isTrue();
        assertThat(response.message()).isEqualTo("Notifications archived");
        assertThat(response.data()).isSameAs(count);
    }

    private NotificationResponse response() {
        return new NotificationResponse(
                notificationId,
                NotificationType.PAYMENT,
                "Payment received",
                "Your payment was received.",
                "TRANSACTION",
                UUID.randomUUID(),
                "/transactions/" + notificationId,
                null,
                "payment:" + notificationId,
                Map.of(),
                null,
                null,
                null,
                null,
                null,
                Instant.now());
    }
}
