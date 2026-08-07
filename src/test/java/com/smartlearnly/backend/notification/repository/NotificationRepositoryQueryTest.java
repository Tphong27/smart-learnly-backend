package com.smartlearnly.backend.notification.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

class NotificationRepositoryQueryTest {
    @Test
    void findActiveForUserShouldReturnOnlyActiveNotificationsNewestFirst() throws Exception {
        Method method = NotificationRepository.class.getMethod(
                "findActiveForUser",
                UUID.class,
                Pageable.class);

        Query query = method.getAnnotation(Query.class);

        assertThat(query).isNotNull();
        assertThat(query.nativeQuery()).isTrue();
        assertThat(query.value())
                .contains("notification.user_id = :userId")
                .contains("notification.archived_at IS NULL")
                .contains("ORDER BY notification.created_at DESC")
                .doesNotContain("notification.type")
                .doesNotContain("notification.read_at");
        assertThat(query.countQuery())
                .contains("SELECT COUNT(*)")
                .contains("notification.user_id = :userId")
                .contains("notification.archived_at IS NULL")
                .doesNotContain("ORDER BY")
                .doesNotContain("notification.type")
                .doesNotContain("notification.read_at");
    }
}
